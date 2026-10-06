package tk.zwander.common.util

import com.linroid.ketch.api.DownloadConfig
import com.linroid.ketch.api.log.LogLevel
import com.linroid.ketch.api.log.Logger
import com.linroid.ketch.core.Ketch
import com.linroid.ketch.engine.KtorHttpEngine
import com.linroid.ketch.sqlite.DriverFactory
import com.linroid.ketch.sqlite.createSqliteTaskStore
import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig

val globalHttpClient: HttpClient = HttpClient {
    this.followRedirects = true
    this.expectSuccess = false

    install(HttpTimeout)
}

internal val firmwareHttpEngine = FirmwareHttpEngine(
    KtorHttpEngine(globalHttpClient.config {
        install(HttpTimeout) {
            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }),
)

val ketch: Ketch = Ketch(
    httpEngine = firmwareHttpEngine,
    config = DownloadConfig(
        maxConnectionsPerDownload = 8,
    ),
    taskStore = createSqliteTaskStore(ketchDb),
    logger = Logger.console(minLevel = LogLevel.WARN),
)

expect val ketchDb: DriverFactory
