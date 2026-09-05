package tk.zwander.common.tools

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import tk.zwander.common.data.FetchResult
import tk.zwander.common.data.SmartBinaryInfo
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.invalidCscError
import tk.zwander.common.generated.resources.noFirmwareFoundError
import tk.zwander.common.util.dataNode
import tk.zwander.common.util.firstDataElementDataByTagName
import tk.zwander.common.util.globalHttpClient
import tk.zwander.common.util.firstElementByTagName
import tk.zwander.common.util.invoke
import tk.zwander.common.util.textNode

/**
 * Handle fetching the latest version for a given model and region.
 */
object VersionFetch {
    suspend fun hybridGetLatestVersion(model: String, region: String): FetchResult.VersionFetchResult {
        val smartDocument = performHistoryRequest(model, region)
        val history = parseHistoryInfos(smartDocument)

        if (history.isNotEmpty()) {
            return FetchResult.VersionFetchResult(
                versionCode = history.last().swVersion,
                androidVersion = history.last().osName ?: "",
                rawOutput = smartDocument.toString(),
            )
        }

        return getLatestVersion(model, region)
    }

    /**
     * Get the latest firmware version for a given model and region.
     * @param model the device model.
     * @param region the device region.
     */
    suspend fun getLatestVersion(model: String, region: String): FetchResult.VersionFetchResult {
        try {
            val response = globalHttpClient.get(
                urlString = "https://fota-cloud-dn.ospserver.net:443/firmware/${region}/${model}/version.xml",
            ) {
                userAgent("Kiss2.0_FUS")
            }

            val responseXml = Ksoup.parse(response.bodyAsText())

            if (responseXml.tagName() == "Error") {
                val code = responseXml.firstElementByTagName("Code")!!.text()
                val message = responseXml.firstElementByTagName("Message")!!.text()

                return FetchResult.VersionFetchResult(
                    error = IllegalStateException("Code: ${code}, Message: $message"),
                    rawOutput = responseXml.toString()
                )
            }

            try {
                val latest = responseXml.firstElementByTagName("firmware")
                    ?.firstElementByTagName("version")
                    ?.firstElementByTagName("latest")

                val latestText = latest?.text()

                if (latestText.isNullOrBlank()) {
                    val hasAccessDenied = responseXml.firstElementByTagName("code")
                        ?.text() == "AccessDenied"

                    return FetchResult.VersionFetchResult(
                        error = Exception(
                            if (hasAccessDenied) {
                                Res.string.invalidCscError()
                            } else {
                                Res.string.noFirmwareFoundError()
                            },
                        ),
                    )
                }

                val vc = latestText.split("/").toMutableList()

                if (vc.size == 3) {
                    vc.add(vc[0])
                }
                if (vc[2] == "") {
                    vc[2] = vc[0]
                }

                return FetchResult.VersionFetchResult(
                    versionCode = vc.joinToString("/"),
                    androidVersion = latest.attribute("o")?.value ?: "",
                    rawOutput = responseXml.toString()
                )
            } catch (e: Exception) {
                return FetchResult.VersionFetchResult(
                    error = e,
                    rawOutput = responseXml.toString()
                )
            }
        } catch (e: Exception) {
            return FetchResult.VersionFetchResult(
                error = e,
            )
        }
    }

    fun parseHistoryInfos(
        historyDoc: Document,
    ): List<SmartBinaryInfo> {
        val allInfos = historyDoc.getElementsByTag("BINARY_INFO")

        if (allInfos.isEmpty()) {
            return listOf()
        }

        return allInfos.map { info ->
            SmartBinaryInfo(
                index = info.firstDataElementDataByTagName("BINARY_INDEX")?.toIntOrNull(),
                sequence = info.firstDataElementDataByTagName("BINARY_SEQUENCE")?.toInt()!!,
                modelName = info.firstDataElementDataByTagName("BINARY_MODEL_NAME") ?: "",
                displayName = info.firstDataElementDataByTagName("BINARY_MODEL_DISPLAYNAME"),
                swVersion = info.firstDataElementDataByTagName("BINARY_SW_VERSION") ?: "",
                displayVersion = info.firstDataElementDataByTagName("BINARY_SW_DISPLAYVERSION"),
                directVersion = info.firstDataElementDataByTagName("BINARY_DIRECT_VERSION"),
                localCode = info.firstDataElementDataByTagName("BINARY_LOCAL_CODE") ?: "",
                buyerCode = info.firstDataElementDataByTagName("BINARY_BUYER_CODE"),
                nature = info.firstDataElementDataByTagName("BINARY_NATURE")?.toIntOrNull(),
                status = info.firstDataElementDataByTagName("BINARY_STATUS")?.toIntOrNull(),
                exists = info.firstDataElementDataByTagName("BINARY_EXIST")?.toIntOrNull(),
                osName = info.firstDataElementDataByTagName("BINARY_OS_NAME"),
                platform = info.firstDataElementDataByTagName("DEVICE_PLATFORM"),
                openDate = info.firstDataElementDataByTagName("BINARY_OPEN_DATE"),
                sharing = info.firstDataElementDataByTagName("SHARING_BINARY")?.toIntOrNull(),
                category = info.firstDataElementDataByTagName("BINARY_CATEGORY"),
                open = info.firstDataElementDataByTagName("AID_OPEN")?.toIntOrNull(),
            )
        }
            // Exclude beta firmware
            .filterNot { it.osName == "Z(Android 99)" }
            .sortedBy { it.sequence }
    }

    suspend fun performHistoryRequest(
        model: String,
        region: String,
    ): Document {
        val requestContent = createHistoryRequest(model, region)

        val response = IFusClient.selectClientAndMakeRequest(
            request = FusClient.Request.HISTORY,
            data = requestContent,
            signature = model,
        )

        return Ksoup.parse(response)
    }

    private fun createHistoryRequest(
        model: String,
        region: String,
    ): String {
        val xml = xml("FUSMsg") {
            "FUSHdr" {
                textNode("ProtoVer", "1")
                textNode("SessionID", "0")
                textNode("MsgID", "1")
            }
            "FUSBody" {
                "Put" {
                    dataNode("ACCESS_MODE", "1")
                    dataNode("BINARY_MODEL_NAME", model)
                    dataNode("BINARY_LOCAL_CODE", region)
                }
            }
        }

        return xml.toString(PrintOptions(singleLineTextElements = true))
    }
}
