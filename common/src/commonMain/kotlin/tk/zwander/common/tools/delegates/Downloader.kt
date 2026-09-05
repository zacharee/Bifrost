package tk.zwander.common.tools.delegates

import com.linroid.ketch.api.KetchError
import dev.zwander.kmp.platform.HostOS
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import tk.zwander.common.data.BinaryFileInfo
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.checkingCRC
import tk.zwander.common.generated.resources.checkingMD5
import tk.zwander.common.generated.resources.crcCheckFailed
import tk.zwander.common.generated.resources.decrypting
import tk.zwander.common.generated.resources.done
import tk.zwander.common.generated.resources.downloading
import tk.zwander.common.generated.resources.firmwareCheckError
import tk.zwander.common.generated.resources.md5CheckFailed
import tk.zwander.common.tools.CryptUtils
import tk.zwander.common.tools.FusClient
import tk.zwander.common.tools.FusClientLegacy
import tk.zwander.common.tools.IFusClient
import tk.zwander.common.tools.Request
import tk.zwander.common.tools.VersionFetch
import tk.zwander.common.util.BifrostSettings
import tk.zwander.common.util.ChangelogHandler
import tk.zwander.common.util.Event
import tk.zwander.common.util.FileManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.invoke
import tk.zwander.common.util.streamOperationWithProgress
import tk.zwander.commonCompose.model.DownloadModel

object Downloader {
    interface DownloadErrorCallback {
        fun onError(info: DownloadErrorInfo)
    }

    data class DownloadErrorInfo(
        val message: String,
        val callback: DownloadErrorConfirmCallback,
    )

    data class DownloadErrorConfirmCallback(
        val onAccept: suspend () -> Unit,
        val onCancel: suspend () -> Unit,
    )

    suspend fun onDownload(
        model: DownloadModel,
        confirmCallback: DownloadErrorCallback,
    ) {
        val standard = onDownload(
            model = model,
            confirmCallback = confirmCallback,
            legacy = false,
            onFinish = { error, message ->
                if (!error) {
                    model.endJob(message)
                    eventManager.sendEvent(Event.Download.Finish)
                    return@onDownload
                }
            },
        )

        println("Standard success $standard")

        if (standard) {
            return
        }

        // Legacy fallback
        onDownload(
            model = model,
            confirmCallback = confirmCallback,
            legacy = true,
            onFinish = { _, message ->
                model.endJob(message)
                eventManager.sendEvent(Event.Download.Finish)
            },
        )
    }

    suspend fun onDownload(
        model: DownloadModel,
        confirmCallback: DownloadErrorCallback,
        legacy: Boolean,
        onFinish: suspend (error: Boolean, message: String) -> Unit,
    ): Boolean {
        println("Downloading $legacy")

        eventManager.sendEvent(Event.Download.Start)
        model.statusText.value = Res.string.downloading()

        val info = Request.retrieveBinaryFileInfo(
            fw = model.fw.value,
            model = model.model.value,
            region = model.region.value,
            onVersionException = { exception, info ->
                confirmCallback.onError(
                    info = DownloadErrorInfo(
                        message = exception.message!!,
                        callback = DownloadErrorConfirmCallback(
                            onAccept = {
                                if (!performDownload(
                                        info = info!!,
                                        model = model,
                                        legacy = legacy,
                                        onFinish = onFinish,
                                    )) {
                                    onFinish(true, "")
                                }
                            },
                            onCancel = {
                                onFinish(false, "")
                            },
                        )
                    ),
                )
            },
            onErrorFinish = {
                onFinish(true, it)
            },
            shouldReportError = {
                !model.manual.value
            },
            imeiSerial = model.imeiSerial.value,
            legacy = legacy,
        )

        return if (info != null) {
            try {
                performDownload(info = info, model = model, legacy = legacy, onFinish = onFinish)
            } catch (e: Throwable) {
                println("Error with download $legacy")
                if (legacy) throw e
                false
            }
        } else {
            println("Unable to retrieve binary info")
            false
        }
    }

    private suspend fun performDownload(
        info: BinaryFileInfo,
        model: DownloadModel,
        legacy: Boolean,
        onFinish: suspend (error: Boolean, message: String) -> Unit,
    ): Boolean {
        return try {
            val (path, fileName, size, crc32, v4Key, fwVer, modelType) = info
            val request = Request.createBinaryInit(
                fileName = fileName,
                nonce = IFusClient.getNonce(legacy),
                fw = fwVer,
                modelType = modelType,
                region = model.region.value,
                legacy = legacy,
            )

            val result = IFusClient.selectClientAndMakeRequest(
                request = if (legacy) {
                    FusClientLegacy.Request.BINARY_INIT
                } else {
                    FusClient.Request.BINARY_INIT
                },
                data = request,
            )

            println("Binary init result $result")

            val fullFileName = fileName.replace(
                ".zip",
                "_${model.fw.value.replace("/", "_")}_${model.region.value}.zip",
            ).substringAfterLast("/")

            val decryptionKeyFileName = if (BifrostSettings.Keys.enableDecryptKeySave()) {
                "DecryptionKey_${fullFileName}.txt"
            } else {
                null
            }

            val downloadDirectory = FileManager.pickDirectory()
            val tempDirectory = FileManager.getTempDirectory()

            val encFile = (tempDirectory ?: downloadDirectory)?.child(fullFileName, false) ?: run {
                onFinish(false, "")
                return true
            }
            val extractedEncFile = downloadDirectory?.child(fullFileName, false) ?: run {
                onFinish(false, "")
                return true
            }
            val decFile = downloadDirectory.child(
                fullFileName.replace(".enc2", "")
                    .replace(".enc4", ""),
                false,
            )
            val decKeyFile = downloadDirectory.let { dir ->
                decryptionKeyFileName?.let { dec ->
                    dir.child(dec, false)
                }
            }

            decKeyFile?.openOutputStream(false)?.use { output ->
                if (fullFileName.endsWith(".enc2")) {
                    output.write(
                        CryptUtils.getV2Key(
                            model.fw.value,
                            model.model.value,
                            model.region.value,
                        ).second.toByteArray(),
                    )
                }

                v4Key?.let {
                    output.write(v4Key.second.toByteArray())
                }
            }

            val md5 = if (HostOS.current != HostOS.Android || extractedEncFile.getLength() < size) {
                IFusClient.downloadFile(
                    fileName = path + fileName,
                    start = encFile.getLength(),
                    size = size,
                    dest = encFile,
                    legacy = legacy,
                ) { current, max, bps ->
                    model.progress.value = current to max
                    model.speed.value = bps

                    eventManager.sendEvent(
                        Event.Download.Progress(
                            status = Res.string.downloading(),
                            current = current,
                            max = max,
                        )
                    )
                }
            } else {
                null
            }

            if (crc32 != null) {
                model.speed.value = 0L
                model.statusText.value = Res.string.checkingCRC()
                val result = CryptUtils.checkCrc32(
                    encFile.openInputStream() ?: return true,
                    encFile.getLength(),
                    crc32,
                ) { current, max, bps ->
                    model.progress.value = current to max
                    model.speed.value = bps

                    eventManager.sendEvent(
                        Event.Download.Progress(
                            status = Res.string.checkingCRC(),
                            current = current,
                            max = max,
                        )
                    )
                }

                if (!result) {
                    model.endJob(Res.string.crcCheckFailed())
                    return true
                }
            }

            if (md5 != null) {
                model.speed.value = 0L
                model.statusText.value = Res.string.checkingMD5()

                eventManager.sendEvent(
                    Event.Download.Progress(
                        status = Res.string.checkingMD5(),
                        current = 0,
                        max = 1,
                    )
                )

                val result = withContext(Dispatchers.Default) {
                    CryptUtils.checkMD5(
                        md5,
                        encFile.openInputStream(),
                    )
                }

                if (!result) {
                    model.endJob(Res.string.md5CheckFailed())
                    return true
                }
            }

            if (tempDirectory != null && tempDirectory != downloadDirectory && extractedEncFile.getLength() < size) {
                model.speed.value = 0L
                model.statusText.value = "Copying"

                val input = encFile.openInputStream() ?: run {
                    model.endJob("")
                    return true
                }
                val output = extractedEncFile.openOutputStream() ?: run {
                    model.endJob("")
                    return true
                }

                try {
                    streamOperationWithProgress(
                        input = input,
                        output = output,
                        size = encFile.getLength(),
                        progressCallback = { current, max, bps ->
                            model.progress.value = current to max
                            model.speed.value = bps

                            eventManager.sendEvent(
                                Event.Download.Progress(
                                    status = "Copying",
                                    current = current,
                                    max = max,
                                )
                            )
                        },
                    )
                } finally {
                    input.close()
                    output.close()
                    encFile.delete()
                }
            }

            model.speed.value = 0L
            model.statusText.value = Res.string.decrypting()

            val key =
                if (fullFileName.endsWith(".enc2")) {
                    CryptUtils.getV2Key(
                        model.fw.value,
                        model.model.value,
                        model.region.value,
                    ).first
                } else {
                    info.v4Key?.first!!
                }

            CryptUtils.decryptProgress(
                extractedEncFile.openInputStream() ?: return false,
                decFile?.openOutputStream() ?: return false,
                key,
                size,
            ) { current, max, bps ->
                model.progress.value = current to max
                model.speed.value = bps

                eventManager.sendEvent(
                    Event.Download.Progress(
                        status = Res.string.decrypting(),
                        current = current,
                        max = max,
                    )
                )
            }

            if (BifrostSettings.Keys.autoDeleteEncryptedFirmware()) {
                encFile.delete()
                extractedEncFile.delete()
            }

            onFinish(false, Res.string.done())
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            val message = if (e !is CancellationException) "${e.message}" else ""
            onFinish(e is KetchError.Http && e.code == 401, message)
            false
        }
    }

    suspend fun onFetch(model: DownloadModel) {
        model.statusText.value = ""
        model.changelog.value = null
        model.osCode.value = ""

        val (fw, os, error, output) = VersionFetch.hybridGetLatestVersion(
            model.model.value,
            model.region.value,
        )

        if (error != null) {
            model.endJob(
                Res.string.firmwareCheckError(
                    error.message.toString(),
                    output.replace("\t", "  ")
                )
            )
            return
        }

        model.changelog.value = ChangelogHandler.getChangelog(
            model.model.value,
            model.region.value,
            fw.split("/")[0],
        )

        model.fw.value = fw
        model.osCode.value = os

        model.endJob("")
    }
}
