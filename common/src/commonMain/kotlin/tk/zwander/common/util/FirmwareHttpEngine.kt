package tk.zwander.common.util

import com.linroid.ketch.core.engine.HttpEngine
import com.linroid.ketch.core.engine.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Supplies fresh Samsung credentials when resuming a task with persisted request headers.
 * Ketch's public task API cannot replace headers, so override them before the delegate sends
 * each request. Match only the original URL; the delegate still controls redirect headers.
 */
internal class FirmwareHttpEngine(private val delegate: HttpEngine) : HttpEngine {
    private val headersByUrl = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    fun updateHeaders(url: String, headers: Map<String, String>) {
        headersByUrl.update { it + (url to headers.toMap()) }
    }

    private fun headersFor(url: String, fallback: Map<String, String>): Map<String, String> =
        headersByUrl.value[url] ?: fallback

    override suspend fun head(url: String, headers: Map<String, String>): ServerInfo =
        delegate.head(url, headersFor(url, headers))

    override suspend fun probe(url: String, headers: Map<String, String>): ServerInfo =
        delegate.probe(url, headersFor(url, headers))

    override suspend fun download(
        url: String,
        range: LongRange?,
        headers: Map<String, String>,
        onData: suspend (ByteArray) -> Unit,
    ) {
        delegate.download(url, range, headersFor(url, headers), onData)
    }

    override suspend fun downloadResource(
        url: String,
        headers: Map<String, String>,
        onData: suspend (ByteArray) -> Unit,
    ): String = delegate.downloadResource(url, headersFor(url, headers), onData)

    override fun close() {
        headersByUrl.value = emptyMap()
        delegate.close()
    }
}
