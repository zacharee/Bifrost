@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EXPOSED_PARAMETER_TYPE")

package tk.zwander.common.tools

import com.fleeksoft.io.exception.ArrayIndexOutOfBoundsException
import com.linroid.ketch.api.Destination
import com.linroid.ketch.api.DownloadRequest
import com.linroid.ketch.api.DownloadState
import com.linroid.ketch.api.KetchError
import dev.zwander.kotlin.file.IPlatformFile
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.io.InternalIoApi
import tk.zwander.common.util.BreadcrumbType
import tk.zwander.common.util.BugsnagUtils
import tk.zwander.common.util.globalHttpClient
import tk.zwander.common.util.ketch

/**
 * Manage communications with Samsung's server.
 */
object FusClient : IFusClient<FusClient.Request> {
    enum class Request(val value: String, val cloud: Boolean) : IFusClient.IRequest {
        GENERATE_NONCE("NF_SmartDownloadGenerateNonce.do", false),
        BINARY_INFORM("NF_SmartDownloadBinaryInform.do", false),
        BINARY_INIT("NF_SmartDownloadBinaryInitForMass.do", false),
        HISTORY("SmartHistory.do", false),
    }

    private var nonce = ""

    private var auth: String = ""
    private var sessionId: String = ""

    override suspend fun getNonce(): String {
        if (nonce.isBlank()) {
            generateNonce()
        }

        return nonce
    }

    override suspend fun generateNonce() {
        BugsnagUtils.addBreadcrumb(
            message = "Generating nonce.",
            data = mapOf(),
            type = BreadcrumbType.LOG,
        )
        println("Generating nonce.")
        makeReq(Request.GENERATE_NONCE)
        BugsnagUtils.addBreadcrumb(
            message = "Nonce: $nonce, Auth: $auth",
            data = mapOf(),
            type = BreadcrumbType.LOG,
        )
        println("Nonce: $nonce")
        println("Auth: $auth")
    }

    private suspend fun makeSignatureHash(signature: String?): String? {
        if (signature == null) return null

        val hasher = CryptUtils.md5Provider.hasher()
        val a = hasher.hash("auth:$nonce:00000001".toByteArray()).toHexString()
        val b = hasher.hash("interface:$signature".toByteArray()).toHexString()

        return hasher.hash("$a:FUS:$b".toByteArray()).toHexString()
    }

    override suspend fun getAuthV(includeNonce: Boolean, signature: String?, cloud: Boolean): String {
        val hasSignature = !signature.isNullOrBlank()
        val nonce = when {
            includeNonce && hasSignature -> {
                val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
                CharArray(16) { chars.random() }.joinToString("")
            }
            includeNonce -> nonce
            else -> ""
        }
        return "FUS nonce=\"${if (cloud) nonce else this.nonce}\", " +
                "signature=\"${makeSignatureHash(signature?.takeIf { !it.isBlank() }) ?: this.auth}\", " +
                "nc=\"${if (hasSignature) "00000001" else ""}\", " +
                "type=\"${if (hasSignature) "auth" else ""}\", " +
                "realm=\"${if (hasSignature) "interface" else ""}\""
    }

    override suspend fun getDownloadUrl(path: String): String {
        return "http://cloud-neofussvr.samsungmobile.com/NF_SmartDownloadBinaryForMass.do?file=${path}"
    }

    /**
     * Make a request to Samsung, automatically inserting authorization data.
     * @param request the request to make.
     * @param data any body data that needs to go into the request.
     * @return the response body data, as text. Usually XML.
     */
    override suspend fun makeReq(request: Request, data: String, signature: String?, includeNonce: Boolean): String {
        if (nonce.isBlank() && request != Request.GENERATE_NONCE) {
            generateNonce()
        }

        val authV = getAuthV(cloud = request.cloud, signature = signature)

        val response =
            globalHttpClient.request("https://neofussvr.sslcs.cdngc.net/${request.value}") {
                method = HttpMethod.Post
                headers {
                    append("Authorization", authV)
                    append("User-Agent", "SMART 2.0")
                    append("Cookie", "JSESSIONID=${sessionId};SESSION=${sessionId}")
                    append("Set-Cookie", "JSESSIONID=${sessionId};SESSION=${sessionId}")
                    append(HttpHeaders.ContentLength, "${data.toByteArray().size}")
                }
                setBody(data)
            }

        val body = response.bodyAsText()

        if (request != Request.GENERATE_NONCE && response.is401(body)) {
            generateNonce()

            return makeReq(request, data)
        }

        if (response.headers["NONCE"] != null || response.headers["nonce"] != null) {
            try {
                nonce = response.headers["NONCE"] ?: response.headers["nonce"] ?: ""

                try {
                    auth = CryptUtils.decryptNonce(nonce.take(16).padEnd((16 - nonce.length).coerceAtLeast(0), '0'))
                } catch (_: Exception) {}
            } catch (e: ArrayIndexOutOfBoundsException) {
                BugsnagUtils.addBreadcrumb(
                    message = "Error generating nonce.",
                    data = mapOf("error" to e),
                    type = BreadcrumbType.ERROR,
                )
                println("Error generating nonce.")
                e.printStackTrace()
            }
        }

        if (response.headers["Set-Cookie"] != null || response.headers["set-cookie"] != null) {
            sessionId = response.headers.entries()
                .firstNotNullOfOrNull { headers ->
                    headers.value.find { value ->
                        value.contains("JSESSIONID=") ||
                                value.contains("SESSION=")
                    }
                }
                ?.replace("JSESSIONID=", "")
                ?.replace("SESSION=", "")
                ?.replace(Regex(";.*$"), "")
                ?: sessionId
        }

        return body
    }

    /**
     * Download a file from Samsung's server.
     * @param fileName the name of the file to download.
     * @param start an optional offset. Used for resuming downloads.
     */
    override suspend fun downloadFile(
        fileName: String,
        start: Long,
        size: Long,
        dest: IPlatformFile,
        progressCallback: suspend (current: Long, max: Long, bps: Long) -> Unit,
    ): String? {
        val authV = getAuthV(cloud = true)
        val url = getDownloadUrl(fileName)

        val md5Request = globalHttpClient.prepareRequest {
            method = HttpMethod.Get
            url(url)
            headers {
                append("Authorization", authV)
                append("User-Agent", "SMART 2.0")
                if (start > 0) {
                    append("Range", "bytes=${start}-")
                }
            }
            timeout {
                this.requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                this.socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                this.connectTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }

        val md5 = md5Request.execute { response ->
            response.headers["Content-MD5"]
        }

        val task = ketch.tasks.value.find { it.request.url == url }
            ?.let { download ->
                download.resume(Destination(dest.getAbsolutePath()))
                download.takeIf {
                    it.state.value !is DownloadState.Completed
                }
            } ?: ketch.download(
            DownloadRequest(
                url = url,
                destination = Destination(dest.getAbsolutePath()),
                headers = mapOf(
                    "Authorization" to authV.also { println(it) },
                    "User-Agent" to "SMART 2.0",
                    "Cache-Control" to "no-cache",
                ),
            ),
        )

        CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
            task.state.collect {
                if (it is DownloadState.Downloading) {
                    progressCallback(
                        it.progress.downloadedBytes,
                        size,
                        it.progress.bytesPerSecond,
                    )
                }
            }
        }

        try {
            while (true) {
                val result = task.await()

                if (result.isSuccess) {
                    break
                }

                (result.exceptionOrNull() as? KetchError)?.let { error ->
                    if (!error.isRetryable) {
                        throw error
                    }
                }
            }
        } catch (_: CancellationException) {
            task.pause()
        }

        return md5
    }
}
