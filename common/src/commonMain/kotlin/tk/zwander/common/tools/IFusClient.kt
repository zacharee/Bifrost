package tk.zwander.common.tools

import com.fleeksoft.ksoup.Ksoup
import com.linroid.ketch.api.*
import dev.zwander.kotlin.file.IPlatformFile
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import tk.zwander.common.util.firstElementByTagName
import tk.zwander.common.util.globalHttpClient
import tk.zwander.common.util.ketch

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
    suspend fun createHeaders(authV: String): Map<String, String>

    suspend fun createDownloadTask(
        url: String,
        fileName: String,
        start: Long = 0,
        size: Long,
        dest: IPlatformFile,
        headers: Map<String, String>,
        progressCallback: suspend (current: Long, max: Long, bps: Long) -> Unit,
    ): DownloadTask {
        val existingTask = try {
            ketch.tasks.value.find { it.request.value.url == url }
                ?.let { download ->
                    println(download.state.value)
                    if (download.state.value !is DownloadState.Completed) {
                        download.updateHeaders(headers)
                        download.resume(Destination(dest.getAbsolutePath()))
                        download
                    } else {
                        download.remove()
                        null
                    }
                }
        } catch (e: KetchError.Http) {
            e.printStackTrace()
            if (e.code == 401) {
                ketch.tasks.value.findLast { it.request.value.url == url }?.remove()
                null
            } else {
                throw e
            }
        }

        val task = existingTask ?: ketch.download(
            DownloadRequest(
                url = url,
                destination = Destination(dest.getAbsolutePath()),
                headers = headers,
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

        return task
    }

    /**
     * Download a file from Samsung's server.
     * @param fileName the name of the file to download.
     * @param start an optional offset. Used for resuming downloads.
     */
    suspend fun downloadFile(
        fileName: String,
        start: Long = 0,
        size: Long,
        dest: IPlatformFile,
        progressCallback: suspend (current: Long, max: Long, bps: Long) -> Unit,
    ): String? {
        val authV = getAuthV()
        val url = getDownloadUrl(fileName)
        val headers = createHeaders(authV)

        val md5Request = globalHttpClient.prepareRequest {
            method = HttpMethod.Head
            url(url)
            headers {
                append("Authorization", authV)
                append("User-Agent", "SMART 2.0")
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

        var task = createDownloadTask(
            url = url,
            fileName = fileName,
            start = start,
            size = size,
            dest = dest,
            headers = headers,
            progressCallback = progressCallback,
        )

        try {
            while (true) {
                val result = task.await()

                if (result.isSuccess) {
                    break
                }

                (result.exceptionOrNull() as? KetchError)?.let { error ->
                    if (!error.isRetryable) {
                        task.remove()
                    }

                    task = createDownloadTask(
                        url = url,
                        fileName = fileName,
                        start = start,
                        size = size,
                        dest = dest,
                        headers = headers,
                        progressCallback = progressCallback,
                    )
                }
            }
        } catch (_: CancellationException) {
            task.pause()
        }

        return md5
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
