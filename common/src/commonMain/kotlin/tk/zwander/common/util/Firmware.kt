package tk.zwander.common.util

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

suspend fun getFirmwareHistoryStringFromSamsung(model: String, region: String): String? {
    return globalHttpClient.get(
        urlString = "https://fota-cloud-dn.ospserver.net:443/firmware/${region}/${model}/version.xml",
    ) {
        userAgent("Kiss2.0_FUS")
    }.run { if (status.isSuccess()) bodyAsText() else null }
}
