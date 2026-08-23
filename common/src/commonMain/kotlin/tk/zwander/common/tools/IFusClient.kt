package tk.zwander.common.tools

import com.fleeksoft.ksoup.Ksoup
import com.linroid.ketch.api.Destination
import com.linroid.ketch.api.DownloadRequest
import com.linroid.ketch.api.DownloadState
import com.linroid.ketch.api.KetchError
import dev.zwander.kmp.platform.HostOS
import dev.zwander.kotlin.file.IPlatformFile
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.io.InternalIoApi
import tk.zwander.common.util.firstElementByTagName
import tk.zwander.common.util.globalHttpClient
import tk.zwander.common.util.ketch
import tk.zwander.common.util.trackOperationProgress

interface IFusClient<Request : IFusClient.IRequest> {
    sealed interface IRequest

    suspend fun getNonce(): String
    suspend fun generateNonce()
    suspend fun getAuthV(
        includeNonce: Boolean = true,
        signature: String? = null,
        cloud: Boolean = false,
    ): String
    suspend fun getDownloadUrl(path: String): String
    suspend fun makeReq(
        request: Request,
        data: String = "",
        signature: String? = null,
        includeNonce: Boolean = true,
    ): String

    /**
     * Download a file from Samsung's server.
     * @param fileName the name of the file to download.
     * @param start an optional offset. Used for resuming downloads.
     */
    @OptIn(InternalAPI::class, InternalIoApi::class)
    suspend fun downloadFile(
        fileName: String,
        start: Long = 0,
        size: Long,
        dest: IPlatformFile,
        progressCallback: suspend (current: Long, max: Long, bps: Long) -> Unit,
    ): String? {
        val authV = FusClientLegacy.getAuthV()
        val url = FusClientLegacy.getDownloadUrl(fileName)

        return if (HostOS.current != HostOS.Android) {
            val task = ketch.download(
                DownloadRequest(
                    url = url,
                    destination = Destination(dest.getAbsolutePath()),
                    headers = mapOf(
                        "Authorization" to authV,
                        "User-Agent" to "Kiss2.0_FUS",
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
                            break
                        }
                    }
                }
            } catch (_: CancellationException) {
                task.pause()
            }

            null
        } else {
            val outputStream = dest.openOutputStream(true) ?: return null

            val request = globalHttpClient.prepareRequest {
                method = HttpMethod.Get
                url(url)
                headers {
                    append("Authorization", authV)
                    append("User-Agent", "Kiss2.0_FUS")
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

            try {
                request.execute { response ->
                    val md5 = response.headers["Content-MD5"]
                    val channel = response.bodyAsChannel()

                    trackOperationProgress(
                        size = size,
                        progressCallback = progressCallback,
                        operation = {
                            channel.readTo(outputStream, 8192L)
                        },
                        progressOffset = start,
                        condition = { !channel.isClosedForRead },
                        throttle = false,
                    )

                    outputStream.flush()

                    md5
                }
            } finally {
                outputStream.flush()
                outputStream.close()
            }
        }

    }

    fun HttpResponse.is401(body: String): Boolean {
        if (status.value == 401) {
            println("Response status is 401")
            return true
        }

        try {
            val xml = Ksoup.parse(body)

            val status = xml.firstElementByTagName("FUSBody")
                ?.firstElementByTagName("Results")
                ?.firstElementByTagName("Status")
                ?.text()

            if (status == "401") {
                println("Response body status is 401")
                return true
            }
        } catch (_: Throwable) {
        }

        return false
    }

    companion object {
        suspend fun generateNonce(
            legacy: Boolean,
        ): String = selectClientAndMakeRequest(
            request = if (legacy) {
                FusClientLegacy.Request.GENERATE_NONCE
            } else {
                FusClient.Request.GENERATE_NONCE
            },
        )

        suspend fun performBinaryInform(
            data: String,
            includeNonce: Boolean,
            legacy: Boolean,
        ): String = selectClientAndMakeRequest(
            request = if (legacy) {
                FusClientLegacy.Request.BINARY_INFORM
            } else {
                FusClient.Request.BINARY_INFORM
            },
            includeNonce = includeNonce,
            data = data,
        )

        suspend fun selectClientAndMakeRequest(
            request: IRequest,
            data: String = "",
            signature: String? = null,
            includeNonce: Boolean = true,
        ): String {
            return when (request) {
                is FusClient.Request -> FusClient.makeReq(
                    request = request,
                    data = data,
                    signature = signature,
                    includeNonce = includeNonce,
                )
                is FusClientLegacy.Request -> FusClientLegacy.makeReq(
                    request = request,
                    data = data,
                    signature = signature,
                    includeNonce = includeNonce,
                )
            }
        }

        suspend fun getNonce(legacy: Boolean): String {
            return if (legacy) {
                FusClientLegacy.getNonce()
            } else {
                FusClient.getNonce()
            }
        }

        suspend fun downloadFile(
            legacy: Boolean,
            fileName: String,
            start: Long = 0,
            size: Long,
            dest: IPlatformFile,
            progressCallback: suspend (current: Long, max: Long, bps: Long) -> Unit,
        ): String? {
            return if (legacy) {
                FusClientLegacy.downloadFile(
                    fileName = fileName,
                    start = start,
                    size = size,
                    dest = dest,
                    progressCallback = progressCallback,
                )
            } else {
                FusClient.downloadFile(
                    fileName = fileName,
                    start = start,
                    size = size,
                    dest = dest,
                    progressCallback = progressCallback,
                )
            }
        }
    }
}
