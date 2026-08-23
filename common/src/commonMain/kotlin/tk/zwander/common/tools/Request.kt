package tk.zwander.common.tools

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import dev.whyoleg.cryptography.DelicateCryptographyApi
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import tk.zwander.common.data.BinaryFileInfo
import tk.zwander.common.data.FetchResult
import tk.zwander.common.data.exception.VersionCheckException
import tk.zwander.common.data.exception.VersionException
import tk.zwander.common.data.exception.VersionMismatchException
import tk.zwander.common.exceptions.DownloadError
import tk.zwander.common.exceptions.NoBinaryFileError
import tk.zwander.common.util.CrossPlatformBugsnag
import tk.zwander.common.util.dataNode
import tk.zwander.common.util.firstDataElementDataByTagName
import tk.zwander.common.util.firstElementByTagName
import tk.zwander.common.util.invoke
import tk.zwander.common.util.isAccessoryModel
import tk.zwander.common.util.textNode
import tk.zwander.samloaderkotlin.resources.MR
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handle some requests to Samsung's servers.
 */
object Request {
    /**
     * Generate a logic-check for a given input.
     * @param input the value that needs a logic-check.
     * @param nonce the nonce used to generate the logic-check.
     * @return the logic-check.
     */
    fun getLogicCheck(input: String, nonce: String): String {
        if (input.length < 16) {
            return ""
        }

        return buildString {
            nonce.forEach { char ->
                append(input[char.code and 0xf])
            }
        }
    }

    suspend fun performBinaryInformRetry(
        fw: String,
        model: String,
        region: String,
        imeiSerial: String,
        includeNonce: Boolean,
        legacy: Boolean = false,
    ): Pair<String, Document> {
        val splitImeiSerial = imeiSerial.split("\n").flatMap { it.split(";") }

        var latestRequest = ""
        var latestResult: Document = Ksoup.parse("")
        var latestError: Throwable? = null

        splitImeiSerial.forEachIndexed { index, imei ->
            latestRequest = createBinaryInform(
                fw = fw,
                model = model,
                region = region,
                nonce = IFusClient.getNonce(legacy),
                legacy = legacy,
                imeiSerial = imei,
            )

            if (index % 10 == 0) {
                delay(1000.milliseconds)
            }

            latestResult = try {
                val response =
                    IFusClient.performBinaryInform(
                        data = latestRequest,
                        includeNonce = includeNonce,
                        legacy = legacy,
                    )

                Ksoup.parse(response)
            } catch (e: Throwable) {
                latestError = e
                e.printStackTrace()
                return@forEachIndexed
            }

            latestResult.let { result ->
                val status = result.firstElementByTagName("FUSBody")
                    ?.firstElementByTagName("Results")
                    ?.firstElementByTagName("Status")
                    ?.text()

                println("Status for IMEI $imei: $status")

                if (status != "408") {
                    return (latestRequest to result)
                }
            }
        }

        latestError?.let { throw it }

        return (latestRequest to latestResult)
    }

    /**
     * Generate the XML needed to perform a binary inform.
     * @param fw the firmware string.
     * @param model the device model.
     * @param region the device region.
     * @param nonce the session nonce.
     * @return the needed XML.
     */
    private fun createBinaryInform(
        fw: String,
        model: String,
        region: String,
        nonce: String,
        imeiSerial: String,
        legacy: Boolean,
    ): String {
        val logicCheck = try {
            getLogicCheck(fw, nonce)
        } catch (e: Throwable) {
            e.printStackTrace()
            ""
        }
        val split = fw.split("/")
        val (pda, csc, phone, data) = Array(4) { split.getOrNull(it) }

        val xml = xml("FUSMsg") {
            "FUSHdr" {
                textNode("ProtoVer", if (legacy) "1.0" else "1")
                textNode("SessionID", "0")
                textNode("MsgID", "1")
            }
            "FUSBody" {
                "Put" {
                    if (!legacy) {
                        textNode("CmdID", "1")
                        dataNode("REQUEST_TYPE", "2")
                        dataNode("BINARY_SW_VERSION", fw)
                        dataNode("DEVICE_SN_NUMBER", "")
                        dataNode("BINARY_LOCAL_CODE", region)
                        dataNode("BINARY_MODEL_NAME", model)
                    } else {
                        dataNode("CLIENT_PRODUCT", "Smart Switch")
                        dataNode("CLIENT_VERSION", "4.3.23123_1")
                        dataNode("DEVICE_IMEI_PUSH", imeiSerial.trim())

                        dataNode("DEVICE_FW_VERSION", fw.trim())
                        dataNode("DEVICE_LOCAL_CODE", region.trim())
                        dataNode("DEVICE_AID_CODE", region.trim())
                        dataNode("DEVICE_MODEL_NAME", model.trim())

                        dataNode("DEVICE_CONTENTS_DATA_VERSION", data?.trim() ?: "")
                        dataNode("DEVICE_CSC_CODE2_VERSION", csc?.trim() ?: "")
                        dataNode("DEVICE_PDA_CODE1_VERSION", pda?.trim() ?: "")
                        dataNode("DEVICE_PHONE_FONT_VERSION", phone?.trim() ?: "")
                    }
                    dataNode("ACCESS_MODE", if (legacy) "2" else "1")
                    dataNode("BINARY_NATURE", "1")
                    dataNode("LOGIC_CHECK", logicCheck.trim())

                    "CLIENT_LANGUAGE" {
                        textNode("Type", "String")
                        textNode("Type", "ISO 3166-1-alpha-3")
                        textNode("Data", "1033")
                    }

                    // Some regions need extra properties specified.
                    // TODO: Make these settable in the UI?
                    val (cc, mcc, mnc) = when (region) {
                        "EUX" -> Triple("DE", "262", "01")
                        "EUY" -> Triple("RS", "220", "01")
                        else -> Triple(null, null, null)
                    }

                    cc?.let { dataNode("DEVICE_CC_CODE", it) }
                    mcc?.let { dataNode("MCC_NUM", it) }
                    mnc?.let { dataNode("MNC_NUM", it) }
                }

                "Get" {
                    textNode("CmdID", "2")
                    if (legacy) {
                        "LATEST_FW_VERSION"()
                    } else {
                        "BINARY_SW_VERSION"()
                    }
                }
            }
        }

        return xml.toString(PrintOptions(singleLineTextElements = true))
    }

    /**
     * Generate the XML needed to perform a binary init.
     * @param fileName the name of the firmware file.
     * @param nonce the session nonce.
     * @return the needed XML.
     */
    fun createBinaryInit(
        fileName: String,
        nonce: String,
        fw: String?,
        modelType: String?,
        region: String,
        legacy: Boolean,
    ): String {
        val logicCheck = run {
            val special = if (legacy) {
                fileName.split(".").first().run { slice(this.length - (16 % this.length)..this.lastIndex) }
            } else {
                fileName.slice(fileName.length - 25 until fileName.length - 9)
            }
            getLogicCheck(special, nonce)
        }

        val xml = xml("FUSMsg") {
            "FUSHdr" {
                textNode("ProtoVer", if (legacy) "1.0" else "1")

                if (!legacy) {
                    textNode("SessionID", "0")
                    textNode("MsgID", "1")
                }
            }
            "FUSBody" {
                "Put" {
                    dataNode(if (legacy) "BINARY_FILE_NAME" else "BINARY_NAME", fileName)
                    if (!legacy) {
                        fw?.let { dataNode("BINARY_SW_VERSION", it) }
                        dataNode("DEVICE_LOCAL_CODE", region)
                        modelType?.let { dataNode("DEVICE_MODEL_TYPE", it) }
                    }
                    dataNode("LOGIC_CHECK", logicCheck)
                }
            }
        }

        return xml.toString(PrintOptions(singleLineTextElements = true))
    }

    suspend fun retrieveBinaryFileInfo(
        fw: String,
        model: String,
        region: String,
        imeiSerial: String,
        legacy: Boolean,
        onErrorFinish: suspend (String) -> Unit,
        onVersionException: (suspend (VersionException, BinaryFileInfo?) -> Unit)? = null,
        shouldReportError: suspend (Exception) -> Boolean = { true },
    ): BinaryFileInfo? {
        val result = getBinaryFile(
            fw = fw, model = model, region = region, imeiSerial = imeiSerial, legacy = legacy,
        )

        val (info, error, output, requestBody) = result

        if (error is VersionException && onVersionException != null) {
            onVersionException(error, info)
            return null
        } else if (error != null) {
            onErrorFinish("${error.message ?: MR.strings.error()}\n\n${output}")
            if (result.isReportableCode() &&
                !output.contains("Incapsula") &&
                error !is CancellationException &&
                shouldReportError(error) &&
                !model.isAccessoryModel
            ) {
                CrossPlatformBugsnag.notify(DownloadError(requestBody, output, error))
            }
        }

        return info
    }

    /**
     * Retrieve the file information for a given firmware.
     * @param fw the firmware version string.
     * @param model the device model.
     * @param region the device region.
     * @return a BinaryFileInfo instance representing the file.
     */
    private suspend fun getBinaryFile(
        fw: String,
        model: String,
        region: String,
        imeiSerial: String,
        legacy: Boolean,
    ): FetchResult.GetBinaryFileResult {
        val (request, responseXml) = try {
            performBinaryInformRetry(
                fw = fw.uppercase(),
                model = model,
                region = region,
                imeiSerial = imeiSerial,
                includeNonce = false,
                legacy = legacy,
            )
        } catch (e: Exception) {
            CrossPlatformBugsnag.notify(e)

            return FetchResult.GetBinaryFileResult(
                error = e,
                rawOutput = mapOf(
                    "firmware" to fw,
                    "model" to model,
                    "region" to region,
                ).toString(),
                requestBody = "",
            )
        }

        try {
            val status = responseXml.firstElementByTagName("FUSBody")
                ?.firstElementByTagName("Results")
                ?.firstElementByTagName("Status")
                ?.text()

            if (status == "F01") {
                return FetchResult.GetBinaryFileResult(
                    error = Exception(MR.strings.invalidFirmwareError()),
                    rawOutput = responseXml.toString(),
                    requestBody = request,
                    responseCode = status,
                )
            }

            if (status == "408") {
                return FetchResult.GetBinaryFileResult(
                    error = Exception(MR.strings.invalid_imei_or_serial()),
                    rawOutput = responseXml.toString(),
                    requestBody = request,
                    responseCode = status,
                )
            }

            if (status != "200" && status != "S00") {
                return FetchResult.GetBinaryFileResult(
                    error = Exception(MR.strings.badReturnStatus(status.toString())),
                    rawOutput = responseXml.toString(),
                    requestBody = request,
                    responseCode = status,
                )
            }

            val noBinaryError = {
                FetchResult.GetBinaryFileResult(
                    error = NoBinaryFileError(model, region),
                    rawOutput = responseXml.toString(),
                    requestBody = request,
                    responseCode = status,
                )
            }

            val size = responseXml.firstElementByTagName("FUSBody")
                ?.firstElementByTagName("Put")
                ?.firstDataElementDataByTagName("BINARY_BYTE_SIZE")
                .run {
                    if (isNullOrBlank()) {
                        return noBinaryError()
                    } else {
                        toLong()
                    }
                }

            val fileName = responseXml.firstElementByTagName("FUSBody")
                ?.firstElementByTagName("Put")
                ?.firstDataElementDataByTagName("BINARY_NAME")
                ?: return noBinaryError()

            fun checkAgainstModelString(fileSegment: String, modelString: String): Boolean {
                if (modelString.isEmpty() || modelString.endsWith('-')) {
                    return false
                }

                val modelSuffix = modelString.split("-").getOrElse(1) { modelString }
                val joinedModel = modelString.replace("-", "")

                if (fileSegment.startsWith(modelSuffix) ||
                    fileSegment.startsWith(joinedModel)) {
                    return true
                }

                return checkAgainstModelString(fileSegment, modelString.dropLast(1))
            }

            fun getIndex(file: String?): Int? {
                if (file.isNullOrBlank()) return null

                val fileSplit = file.split("_")

                return fileSplit.indexOfFirst {
                    checkAgainstModelString(it, model)
                }
            }

            suspend fun generateInfo(): BinaryFileInfo {
                val path = responseXml.firstElementByTagName("FUSBody")
                    ?.firstElementByTagName("Put")
                    ?.firstDataElementDataByTagName("MODEL_PATH")!!

                val crc32 = responseXml.firstElementByTagName("FUSBody")
                    ?.firstElementByTagName("Put")
                    ?.firstDataElementDataByTagName("BINARY_CRC")
                    ?.toLongOrNull()

                val v4Key = try {
                    responseXml.extractV4Key()
                        ?: CryptUtils.getV4Key(fw, model, region, imeiSerial, legacy = legacy)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                val fwVer = responseXml.firstElementByTagName("FUSBody")
                    ?.firstElementByTagName("Put")
                    ?.firstDataElementDataByTagName("BINARY_SW_VERSION")

                val modelType = responseXml.firstElementByTagName("FUSBody")
                    ?.firstElementByTagName("Put")
                    ?.firstDataElementDataByTagName("DEVICE_MODEL_TYPE")

                val logicVal = responseXml.firstElementByTagName("FUSBody")
                    ?.firstElementByTagName("Put")
                    .run {
                        this?.firstDataElementDataByTagName("LOGIC_VALUE_FACTORY")
                            ?: this?.firstDataElementDataByTagName("LOGIC_VALUE_HOME")!!
                    }

                return BinaryFileInfo(
                    path = path,
                    fileName = fileName,
                    size = size,
                    crc32 = crc32,
                    v4Key = v4Key,
                    fwVer = fwVer,
                    modelType = modelType,
                    logicVal = logicVal,
                )
            }

            fun getSuffix(str: String): String? {
                return str.split("_").getOrNull(1)
            }

            val dataKeys = arrayOf(
                "DEVICE_USER_DATA_FILE",
                "DEVICE_BOOT_FILE",
                "DEVICE_PDA_CODE1_FILE"
            )

            val dataFile = dataKeys.firstNotNullOfOrNull {
                responseXml.firstElementByTagName("FUSBody")
                    ?.firstElementByTagName("Put")
                    ?.firstDataElementDataByTagName(it)
                    .run { if (isNullOrBlank()) null else this }
            }

            if (dataFile.isNullOrBlank()) {
                return FetchResult.GetBinaryFileResult(
                    info = generateInfo(),
                    error = VersionCheckException(MR.strings.versionCheckError()),
                    requestBody = request,
                    responseCode = status,
                )
            }

            val dataIndex = getIndex(dataFile)

            val cscFile = responseXml.firstElementByTagName("FUSBody")
                ?.firstElementByTagName("Put")
                ?.firstElementByTagName("DEVICE_CSC_HOME_FILE")
                ?.text().run {
                    if (isNullOrBlank()) {
                        responseXml.firstElementByTagName("FUSBody")
                            ?.firstElementByTagName("Put")
                            ?.firstDataElementDataByTagName("DEVICE_CSC_FILE")
                    } else {
                        this
                    }
                }

            val cscIndex = getIndex(cscFile)

            val cpFile = responseXml.firstElementByTagName("FUSBody")
                ?.firstElementByTagName("Put")
                ?.firstElementByTagName("DEVICE_PHONE_FONT_FILE")
                ?.text()

            val cpIndex = getIndex(cpFile)

            val pdaFile = responseXml.firstElementByTagName("FUSBody")
                ?.firstElementByTagName("Put")
                ?.firstElementByTagName("DEVICE_PDA_CODE1_FILE")
                ?.text()

            val pdaIndex = getIndex(pdaFile)

            dataFile.let { f ->
                val (fwVersion, fwCsc, fwCp, fwPda) = fw.split("/")
                val fwCscSuffix = getSuffix(fwCsc)
                val fwCpSuffix = getSuffix(fwCp)

                val split = f.split("_")
                val (version, versionSuffix) = split[dataIndex!!] to split.getOrNull(dataIndex + 1)

                val (servedCsc, cscSuffix) = cscFile?.split("_")
                    ?.takeIf { cscIndex != null }
                    ?.run { getOrNull(cscIndex!!) to getOrNull(cscIndex + 1) } ?: (null to null)
                val (servedCp, cpSuffix) = cpFile?.split("_").run {
                    this?.getOrNull(cpIndex ?: -1) to this?.getOrNull(
                        cpIndex?.plus(1) ?: -1
                    )
                }
                val servedPda = pdaFile?.split("_")?.getOrNull(pdaIndex ?: -1)

                val served =
                    "$version/${servedCsc ?: versionSuffix}/${servedCp ?: version}/${servedPda ?: version}"

                val cscMatch = fwCsc == (servedCsc ?: versionSuffix)
                val cpMatch = fwCp == (servedCp ?: version)
                val fwVersionMatch = fwVersion == version
                val fwPdaMatch = fwPda == servedPda

                val cscSuffixMatch = if (fwCscSuffix != null) fwCscSuffix == cscSuffix else true
                val cpSuffixMatch = if (fwCpSuffix != null) fwCpSuffix == cpSuffix else true

                if (served != fw || !cscMatch || !cpMatch || !fwVersionMatch ||
                    !fwPdaMatch || !cscSuffixMatch || !cpSuffixMatch
                ) {
                    return FetchResult.GetBinaryFileResult(
                        info = generateInfo(),
                        error = VersionMismatchException(MR.strings.versionMismatch(fw, served)),
                        requestBody = request,
                        responseCode = status,
                    )
                }
            }

            return FetchResult.GetBinaryFileResult(
                info = generateInfo(),
                requestBody = request,
                responseCode = status,
            )
        } catch (e: Exception) {
            return FetchResult.GetBinaryFileResult(
                error = e,
                rawOutput = responseXml.toString(),
                requestBody = request,
            )
        }
    }
}

@OptIn(DelicateCryptographyApi::class)
fun Document.extractV4Key(): Pair<ByteArray, String>? {
    val fwVer = firstElementByTagName("FUSBody")
        ?.firstElementByTagName("Results")
        .run {
            this?.firstDataElementDataByTagName("LATEST_FW_VERSION") ?:
                this?.firstDataElementDataByTagName("BINARY_SW_VERSION")
        }

    val logicVal = firstElementByTagName("FUSBody")
        ?.firstElementByTagName("Put")
        .run {
            this?.firstElementByTagName("LOGIC_VALUE_FACTORY")
                ?.firstDataElementDataByTagName("Data")
                ?: this?.firstElementByTagName("LOGIC_VALUE_HOME")
                ?.firstDataElementDataByTagName("Data")
        }

    return if (fwVer != null && logicVal != null) {
        val decKey = Request.getLogicCheck(fwVer, logicVal)

        CryptUtils.md5Provider
            .hasher()
            .hashBlocking(decKey.toByteArray()) to decKey
    } else {
        null
    }
}
