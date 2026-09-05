package tk.zwander.common.tools.delegates

import com.fleeksoft.ksoup.Ksoup
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import tk.zwander.common.data.HistoryInfo
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.historyError
import tk.zwander.common.generated.resources.historyErrorFormat
import tk.zwander.common.tools.VersionFetch
import tk.zwander.common.util.ChangelogHandler
import tk.zwander.common.util.CrossPlatformBugsnag
import tk.zwander.common.util.firstElementByTagName
import tk.zwander.common.util.getFirmwareHistoryStringFromSamsung
import tk.zwander.common.util.invoke
import tk.zwander.commonCompose.model.HistoryModel

object History {
    private val dateFormat = DateTimeComponents.Format {
        year()
        char('/')
        monthNumber()
        char('/')
        day()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

    private fun parseHistoryXml(xml: String): List<HistoryInfo> {
        val doc = Ksoup.parse(xml)

        val latest = doc.firstElementByTagName("firmware")?.firstElementByTagName("version")?.firstElementByTagName("latest")
        val historical = doc.firstElementByTagName("firmware")?.firstElementByTagName("version")?.firstElementByTagName("upgrade")
            ?.getElementsByTag("value")

        val items = arrayListOf<HistoryInfo>()

        fun parseFirmware(string: String): String {
            val firmwareParts = string.split("/").filterNot { it.isBlank() }.toMutableList()

            if (firmwareParts.size == 2) {
                firmwareParts.add(firmwareParts[0])
                firmwareParts.add(firmwareParts[0])
            }

            if (firmwareParts.size == 3) {
                firmwareParts.add(firmwareParts[0])
            }

            return firmwareParts.joinToString("/")
        }

        latest?.apply {
            val androidVersion = latest.attribute("o")?.value

            items.add(
                HistoryInfo(
                    date = null,
                    androidVersion = androidVersion,
                    firmwareString = parseFirmware(latest.text())
                )
            )
        }

        historical?.apply {
            items.addAll(
                mapNotNull {
                    val firmware = parseFirmware(it.text())

                    if (firmware.isNotBlank()) {
                        HistoryInfo(
                            date = null,
                            androidVersion = null,
                            firmwareString = firmware
                        )
                    } else {
                        null
                    }
                }.sortedByDescending {
                    it.firmwareString.let { f ->
                        f.substring(f.lastIndex - 3)
                    }
                }
            )
        }

        return items
    }

    suspend fun onFetch(model: HistoryModel) {
        try {
            val fullHistory = VersionFetch.parseHistoryInfos(VersionFetch.performHistoryRequest(model.model.value, model.region.value))
                .reversed()
            val legacyHistory = getFirmwareHistoryStringFromSamsung(model.model.value, model.region.value)

            if (fullHistory.isEmpty() && legacyHistory == null) {
                model.endJob(Res.string.historyError())
            } else {
                try {
                    val parsed = when {
                        fullHistory.isNotEmpty() -> {
                            fullHistory.map {
                                HistoryInfo(
                                    date = it.openDate?.let { date ->
                                        if (date.isNotBlank()) {
                                            try {
                                                dateFormat.parse(date).toLocalDate()
                                            } catch (e: Exception) {
                                                CrossPlatformBugsnag.notify(e)
                                                null
                                            }
                                        } else {
                                            null
                                        }
                                    },
                                    androidVersion = it.osName,
                                    firmwareString = it.swVersion,
                                )
                            }
                        }

                        legacyHistory != null -> {
                            parseHistoryXml(legacyHistory)
                        }

                        else -> {
                            model.endJob(Res.string.historyError())
                            return
                        }
                    }

                    model.changelogs.value = try {
                        ChangelogHandler.getChangelogs(
                            model.model.value,
                            model.region.value
                        )
                    } catch (e: Exception) {
                        println("Error retrieving changelogs")
                        e.printStackTrace()
                        null
                    }
                    model.historyItems.value = parsed.distinctBy { it.firmwareString }
                    model.endJob("")
                } catch (e: Exception) {
                    e.printStackTrace()
                    model.endJob(Res.string.historyErrorFormat(e.message.toString()))
                }
            }
        } catch (e: Throwable) {
            CrossPlatformBugsnag.notify(e)

            model.endJob("${Res.string.historyError()}${e.message?.let { "\n\n$it" }}")
        }
    }
}
