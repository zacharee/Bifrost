package tk.zwander.common.util

import com.linroid.ketch.core.engine.HttpEngine
import com.linroid.ketch.core.engine.ServerInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FirmwareHttpEngineTest {
    @Test
    fun refreshedCredentialsReplacePersistedHeadersForEveryRequest() = runTest {
        val delegate = RecordingEngine()
        val engine = FirmwareHttpEngine(delegate)
        val stale = mapOf("Authorization" to "expired", "Cookie" to "old-session")
        val fresh = mapOf("Authorization" to "renewed", "User-Agent" to "SMART 2.0")
        engine.updateHeaders(URL, stale)
        engine.head(URL, stale)
        engine.updateHeaders(URL, fresh)

        engine.head(URL, stale)
        engine.probe(URL, stale)
        val received = mutableListOf<Byte>()
        engine.download(URL, 4L..7L, stale) { received.addAll(it.toList()) }
        val finalUrl = engine.downloadResource(URL, stale) {}

        assertEquals(listOf(stale, fresh, fresh, fresh, fresh), delegate.headers)
        assertEquals(4L..7L, delegate.range)
        assertContentEquals(byteArrayOf(4, 5, 6, 7), received.toByteArray())
        assertEquals("https://cdn.example/firmware", finalUrl)
    }

    @Test
    fun credentialsAreScopedToTheExactUrl() = runTest {
        val delegate = RecordingEngine()
        val engine = FirmwareHttpEngine(delegate)
        val fresh = mapOf("Authorization" to "renewed")
        val other = mapOf("Authorization" to "other-task")
        engine.updateHeaders(URL, fresh)

        engine.head("https://example.com/another-firmware", other)
        engine.head("https://other.example/firmware", emptyMap())
        engine.head(URL, emptyMap())

        assertEquals(listOf(other, emptyMap(), fresh), delegate.headers)
    }

    @Test
    fun emptyReplacementRemovesStaleCredentials() = runTest {
        val delegate = RecordingEngine()
        val engine = FirmwareHttpEngine(delegate)
        val stale = mapOf("Authorization" to "expired")
        engine.updateHeaders(URL, stale)
        engine.updateHeaders(URL, emptyMap())

        engine.head(URL, stale)

        assertEquals(listOf(emptyMap()), delegate.headers)
    }

    private class RecordingEngine : HttpEngine {
        val headers = mutableListOf<Map<String, String>>()
        var range: LongRange? = null
        private val info = ServerInfo(8, true, null, null)

        override suspend fun head(url: String, headers: Map<String, String>): ServerInfo {
            this.headers.add(headers)
            return info
        }

        override suspend fun probe(url: String, headers: Map<String, String>): ServerInfo {
            this.headers.add(headers)
            return info
        }

        override suspend fun download(
            url: String,
            range: LongRange?,
            headers: Map<String, String>,
            onData: suspend (ByteArray) -> Unit,
        ) {
            this.headers.add(headers)
            this.range = range
            onData(byteArrayOf(4, 5, 6, 7))
        }

        override suspend fun downloadResource(
            url: String,
            headers: Map<String, String>,
            onData: suspend (ByteArray) -> Unit,
        ): String {
            this.headers.add(headers)
            return "https://cdn.example/firmware"
        }

        override fun close() = Unit
    }

    private companion object {
        const val URL = "https://example.com/firmware"
    }
}
