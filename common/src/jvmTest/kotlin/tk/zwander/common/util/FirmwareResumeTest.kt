package tk.zwander.common.util

import com.linroid.ketch.api.Destination
import com.linroid.ketch.api.DownloadConfig
import com.linroid.ketch.api.DownloadRequest
import com.linroid.ketch.core.Ketch
import com.linroid.ketch.core.KetchDispatchers
import com.linroid.ketch.core.engine.HttpEngine
import com.linroid.ketch.core.engine.ServerInfo
import com.linroid.ketch.sqlite.DriverFactory
import com.linroid.ketch.sqlite.SqliteTaskStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirmwareResumeTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restoredTaskResumesWithFreshCredentialsWithoutRedownloadingSavedBytes() = runTest {
        val directory = Files.createTempDirectory("bifrost-resume-").toFile()
        val output = directory.resolve("firmware.bin")
        val content = ByteArray(16) { it.toByte() }
        val url = "https://example.com/firmware.bin"
        val staleHeaders = mapOf("Authorization" to "expired")
        val freshHeaders = mapOf("Authorization" to "renewed")
        var expectedHeaders = staleHeaders
        var interruptTransfer = true
        val ranges = mutableListOf<LongRange?>()
        val delegate = object : HttpEngine {
            override suspend fun head(url: String, headers: Map<String, String>): ServerInfo {
                assertEquals(expectedHeaders, headers)
                return ServerInfo(content.size.toLong(), true, "firmware-v1", null)
            }

            override suspend fun download(
                url: String,
                range: LongRange?,
                headers: Map<String, String>,
                onData: suspend (ByteArray) -> Unit,
            ) {
                assertEquals(expectedHeaders, headers)
                ranges.add(range)
                requireNotNull(range)
                if (interruptTransfer) {
                    onData(content.copyOfRange(0, 8))
                    awaitCancellation()
                }
                onData(content.copyOfRange(range.first.toInt(), range.last.toInt() + 1))
            }

            override fun close() = Unit
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val driverFactory = DriverFactory(directory.resolve("ketch.db").absolutePath)
        var driver = driverFactory.createDriver()
        var engine = FirmwareHttpEngine(delegate)
        fun createKetch() = Ketch(
            httpEngine = engine,
            taskStore = SqliteTaskStore(driver),
            config = DownloadConfig(retryCount = 0, maxConnectionsPerDownload = 1),
            dispatchers = KetchDispatchers(dispatcher, dispatcher, dispatcher),
        )
        var ketch = createKetch()
        try {
            ketch.start()
            val original = ketch.download(DownloadRequest(
                url = url,
                destination = Destination(output.absolutePath),
                headers = staleHeaders,
            ))
            runCurrent()
            original.pause()
            assertEquals(8L, original.segments.value.sumOf { it.downloadedBytes })
            ketch.close()
            driver.close()

            // Reopen the database, as on app restart. Its request still has expired credentials.
            driver = driverFactory.createDriver()
            engine = FirmwareHttpEngine(delegate)
            ketch = createKetch()
            ketch.start()
            val restored = ketch.tasks.value.single()
            assertEquals(original.taskId, restored.taskId)
            assertEquals(staleHeaders, restored.request.headers)
            interruptTransfer = false
            expectedHeaders = freshHeaders

            engine.updateHeaders(url, freshHeaders)
            restored.resume(Destination(output.absolutePath))
            val result = restored.await()

            assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
            assertEquals(listOf<LongRange?>(0L..15L, 8L..15L), ranges)
            assertContentEquals(content, output.readBytes())
        } finally {
            ketch.close()
            driver.close()
            directory.deleteRecursively()
        }
    }
}
