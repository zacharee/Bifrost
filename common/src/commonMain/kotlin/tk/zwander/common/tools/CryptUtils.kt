package tk.zwander.common.tools

import com.fleeksoft.io.ByteBufferFactory
import com.fleeksoft.io.getInt
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.MD5
import io.github.andreypfau.kotlinx.crypto.CRC32
import io.ktor.utils.io.core.readAvailable
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.bytestring.toHexString
import tk.zwander.common.data.AuthHeader
import tk.zwander.common.data.AuthHeaderBlock
import tk.zwander.common.util.DEFAULT_CHUNK_SIZE
import tk.zwander.common.util.RandomAccessStream
import tk.zwander.common.util.streamOperationWithProgress
import tk.zwander.common.util.trackOperationProgress
import kotlin.io.encoding.Base64

/**
 * Handle encryption and decryption stuff.
 */
@OptIn(ExperimentalUnsignedTypes::class)
object CryptUtils {
    private val SHIFT_INDICES = intArrayOf(0, 5, 10, 15, 4, 9, 14, 3, 8, 13, 2, 7, 12, 1, 6, 11)
    private val SEL32_IDX = List(32) { it }

    /**
     * Old method decryption keys
     */
    private const val KEY_1 = "vicopx7dqu06emacgpnpy8j8zwhduwlh"
    private const val KEY_2 = "9u7qab84rpc16gvk"

    @OptIn(DelicateCryptographyApi::class)
    val md5Provider = CryptographyProvider.Default.get(MD5)
    @OptIn(DelicateCryptographyApi::class)
    val aesEcbProvider = CryptographyProvider.Default.get(AES.ECB)
    val aesCbcProvider = CryptographyProvider.Default.get(AES.CBC)

    object Legacy {
        /**
         * Samsung uses its own padding for its AES
         * encryption, so decrypted bytes need to be manually
         * unpadded.
         *
         * @param d the data to unpad.
         * @return the unpadded data.
         */
        private fun unpad(d: ByteArray): ByteArray {
            val lastElement = d.last()
            return if (lastElement >= 0) {
                d.dropLast(lastElement.toInt()).toByteArray()
            } else {
                d.take(-lastElement).toByteArray()
            }
        }

        /**
         * Manually pad data to be encrypted.
         *
         * @param d the data to pad.
         * @return the padded data.
         */
        private fun pad(d: ByteArray): ByteArray {
            val size = 16 - (d.size % 16)
            val array = ByteArray(size)

            for (i in 0 until size) {
                array[i] = size.toByte()
            }

            return d + array
        }

        /**
         * Encrypt data using AES CBC with custom padding.
         * @param input the data to encrypt.
         * @param key the key to use for encryption.
         * @return the encrypted data.
         */
        @OptIn(DelicateCryptographyApi::class)
        private fun aesEncrypt(input: ByteArray, key: ByteArray): ByteArray {
            val paddedInput = pad(input)
            val iv = key.slice(0 until 16).toByteArray()

            return aesCbcProvider
                .keyDecoder()
                .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
                .cipher(padding = false)
                .encryptWithIvBlocking(iv, paddedInput)
        }

        /**
         * Decrypt data using AES CBC with custom padding.
         * @param input the data to decrypt.
         * @param key the key to use for decryption.
         * @return the decrypted data.
         */
        @OptIn(DelicateCryptographyApi::class)
        private fun aesDecrypt(input: ByteArray, key: ByteArray): ByteArray {
            val iv = key.slice(0 until 16).toByteArray()

            return unpad(
                aesCbcProvider
                    .keyDecoder()
                    .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
                    .cipher(padding = false)
                    .decryptWithIvBlocking(iv, input),
            )
        }

        /**
         * Generate a key given a specific input.
         * @param input the input seed.
         * @return the generated key.
         */
        private fun getFKey(input: ByteArray): ByteArray {
            var key = ""
            for (i in 0 until 16) {
                key += KEY_1[input[i].toInt() % KEY_1.length]
            }
            key += KEY_2
            return key.toByteArray()
        }

        /**
         * Generate an auth token with a given nonce.
         * @param nonce the nonce seed.
         * @return an auth token based on the nonce.
         */
        fun getAuth(nonce: String): String {
            val keyData = nonce.map { (it.code % 16).toByte() }.toByteArray()
            val fKey = getFKey(keyData)

            return Base64.encode(aesEncrypt(nonce.toByteArray(), fKey))
        }

        /**
         * Decrypt a provided nonce string.
         * @param input the nonce to decrypt.
         * @return the decrypted nonce.
         */
        fun decryptNonce(input: String): String {
            val d = Base64.decode(input)
            return aesDecrypt(d, KEY_1.toByteArray())
                .decodeToString()
        }
    }

    private fun createAuthHeader(stream: RandomAccessStream): AuthHeader {
        val headerBytes = stream[0, 56]
        val values = (0 until 56 step 4).map { index ->
            val slice = headerBytes.slice(index until index + 4)

            ByteBufferFactory.wrap(slice.reversed().map { it.toByte() }.toByteArray()).getInt()
        }

        val header = AuthHeader(
            magic = values[0],
            alignment = values[1],
            block1 = AuthHeaderBlock(offset = values[2], size = values[3]),
            block2 = AuthHeaderBlock(offset = values[4], size = values[5]),
            block5 = AuthHeaderBlock(offset = values[6], size = values[7]),
            block6 = AuthHeaderBlock(offset = values[8], size = values[9]),
            block3 = AuthHeaderBlock(offset = values[10], size = values[11]),
            block4 = AuthHeaderBlock(offset = values[12], size = values[13]),
        )

        return header
    }

    suspend fun authenticateBlock(inBlock: ByteArray): ByteArray {
        val wrapped = AuthParamsHandler.getAuthParamStream()
        val header = createAuthHeader(wrapped)
        val stream = object : RandomAccessStream {
            val headerOffset = 56

            override fun get(pos: Long): UByte {
                return wrapped[pos + headerOffset]
            }

            override fun get(pos: Long, len: Int): UByteArray {
                return wrapped[pos + headerOffset, len]
            }
        }
        val tempBlock = IntArray(320)
        for (i in 0 until 16) {
            tempBlock[i] = inBlock[i].toUByte().toInt()
        }
        val outBlock = ByteArray(16)
        val v15 = IntArray(64)
        val baseFinal = header.block1.size
        val finalSrcStart = 288 // 9 * 32

        for (j in 0 until 9) {
            val srcStart = j * 32
            val nextSrcStart = (j + 1) * 32
            val blkIdBase = j * 16
            val srcMid = srcStart + 16

            for (idx in 0 until 16) {
                tempBlock[srcStart + 16 + idx] = tempBlock[srcStart + SHIFT_INDICES[idx]]
            }

            for (i in 0 until 4) {
                val i4 = i.shl(2)
                val i16 = i.shl(4)
                val blkIdRow = blkIdBase + i4
                val base262 = baseFinal.toLong() +
                        header.block2.size +
                        header.block3.size +
                        (6144 * (i + j.toLong().shl(2)))

                for (k in 0 until 4) {
                    val idxVal = tempBlock[srcMid + i4 + k]
                    val blkId = blkIdRow + k
                    val base257 = blkId.toLong().shl(12) + idxVal.shl(4)
                    val selOffset = baseFinal.toLong() + header.block2.size + blkId.toLong().shl(5)
                    val src16Ptr = stream[base257, 16]
                    val selectorPtr = stream[selOffset, 32]
                    val outStart = i16 + k.shl(2)

                    for (outIdx in 0 until 4) {
                        var acc = 0
                        val selBase = outIdx.shl(3)

                        for (bitIdx in 0 until 8) {
                            val selByte = selectorPtr[SEL32_IDX[selBase + bitIdx]].toInt()
                            val srcIdx = selByte.shr(3) and 0x1F
                            val bitPos = 7 - (selByte and 0x7)
                            val srcByte = if (srcIdx < 16) src16Ptr[srcIdx].toInt() else 0

                            acc = acc or (((srcByte shr bitPos) and 1) shl (7 - bitIdx))
                        }

                        v15[outStart + outIdx] = (acc and 0xFF)
                    }
                }

                for (k2 in 0 until 4) {
                    val a1 = v15[i16 + k2]
                    val a2 = v15[i16 + k2 + 4]
                    val a3 = v15[i16 + k2 + 8]
                    val a4 = v15[i16 + k2 + 12]
                    val tblBase = base262 + 1536 * k2
                    val tbl = stream[tblBase, 1536]
                    val hi1 = ((a1 and 0xF0) or (a2 shr 4)) and 0xFF
                    val lo1 = (((a1 and 0x0F) shl 4) or (a2 and 0x0F)) and 0xFF
                    val v6 = (((16 * tbl[hi1].toInt()) xor tbl[256 + lo1].toInt()) and 0xFF)
                    val hi2 = ((a3 and 0xF0) or (a4 shr 4)) and 0xFF
                    val lo2 = (((a3 and 0x0F) shl 4) or (a4 and 0x0F)) and 0xFF
                    val v7 = (((16 * tbl[512 + hi2].toInt()) xor tbl[768 + lo2].toInt()) and 0xFF)
                    val hi3 = ((v6 and 0xF0) or (v7 shr 4)) and 0xFF
                    val lo3 = (((v6 and 0x0F) shl 4) or (v7 and 0x0F)) and 0xFF

                    tempBlock[nextSrcStart + i4 + k2] =
                        (((16 * tbl[1024 + hi3].toInt()) xor tbl[1280 + lo3].toInt()) and 0xFF)
                }
            }
        }

        for (idx in 0 until 16) {
            val pos = baseFinal + idx.toLong().shl(8) +
                    tempBlock[SHIFT_INDICES[idx] + finalSrcStart]
            outBlock[idx] = stream[pos].toByte()
        }

        return outBlock
    }

    /**
     * Decrypt a provided nonce string.
     * @param input the nonce to decrypt.
     * @return the decrypted nonce.
     */
    suspend fun decryptNonce(input: String): String {
        return authenticateBlock(input.toByteArray()).toHexString()
    }

    /**
     * Retrieve the decryption key for a .enc4 firmware file.
     * @param version the firmware string corresponding to the file.
     * @param model the device model corresponding to the file.
     * @param region the device region corresponding to the file.
     * @return the decryption key for this firmware.
     */
    suspend fun getV4Key(
        version: String,
        model: String,
        region: String,
        imeiSerial: String,
        tries: Int = 0,
        legacy: Boolean = false,
        includeNonce: Boolean = true,
    ): Pair<ByteArray, String>? {
        val (_, responseXml) = Request.performBinaryInformRetry(
            fw = version.uppercase(),
            model = model,
            region = region,
            imeiSerial = imeiSerial,
            legacy = legacy,
            includeNonce = includeNonce,
        )

        return try {
            responseXml.extractV4Key()
        } catch (e: Exception) {
            if (tries > 4) {
                throw e
            } else {
                IFusClient.generateNonce(legacy = legacy)
                getV4Key(
                    version = version,
                    model = model,
                    region = region,
                    imeiSerial = imeiSerial,
                    tries = tries + 1,
                    legacy = legacy,
                    includeNonce = includeNonce,
                )
            }
        }
    }

    /**
     * Create the decryption key for a .enc2 firmware file.
     * @param version the firmware string corresponding to the file.
     * @param model the device model corresponding to the file.
     * @param region the device region corresponding to the file.
     * @return the decryption key for this firmware.
     */
    fun getV2Key(version: String, model: String, region: String): Pair<ByteArray, String> {
        val decKey = "${region}:${model}:${version}"

        return md5Provider.hasher().hashBlocking(decKey.toByteArray()) to decKey
    }

    /**
     * Decrypt a provided file to a specified target, with a progress callback.
     * @param inf the encrypted file to decrypt.
     * @param outf where to store the decrypted firmware.
     * @param key the decryption key for this firmware.
     * @param length the size of the encrypted file.
     * @param progressCallback a callback to keep track of the progress.
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun decryptProgress(
        inf: Source,
        outf: Sink,
        key: ByteArray,
        length: Long,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        progressCallback: suspend (current: Long, max: Long, bps: Long) -> Unit,
    ) {
        val cipher = aesEcbProvider.keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher(padding = false)

        streamOperationWithProgress(
            input = inf,
            output = outf,
            size = length,
            chunkSize = chunkSize,
            progressCallback = progressCallback,
            operation = {
                cipher.decryptBlocking(it)
            },
        )
    }

    /**
     * Check the CRC32 of a given encrypted firmware file.
     * @param enc the encrypted file to verify.
     * @param encSize the size of the encrypted file.
     * @param expected the expected CRC32.
     * @param progressCallback a callback to keep track of the progress.
     * @return true if the file's CRC32 matches the expected value.
     */
    suspend fun checkCrc32(
        enc: Source?,
        encSize: Long,
        expected: Long,
        progressCallback: suspend (current: Long, max: Long, bps: Long) -> Unit,
    ): Boolean {
        if (enc == null) {
            return false
        }

        val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
        val crc = CRC32()

        trackOperationProgress(
            size = encSize,
            progressCallback = progressCallback,
            operation = {
                val len = enc.readAtMostTo(buffer, 0, buffer.size)

                if (len > 0) {
                    crc.update(buffer, 0, len)
                }
                len.toLong()
            },
        )

        withContext(Dispatchers.IO) {
            enc.close()
        }

        return crc.intDigest() == expected.toInt()
    }

    /*
     * The MD5 checking methods are from CyanogenMod.
     *
     *
     * Copyright (C) 2012 The CyanogenMod Project
     *
     * * Licensed under the GNU GPLv2 license
     *
     * The text of the license can be found in the LICENSE file
     * or at https://www.gnu.org/licenses/gpl-2.0.txt
     */

    /**
     * Check an MD5 hash given an input stream and an expected value.
     * @param md5 the expected value.
     * @param updateFile the file to check.
     * @return true if the hashes match.
     */
    suspend fun checkMD5(md5: String, updateFile: Source?): Boolean {
        if (md5.isBlank() || updateFile == null) {
            return false
        }

        val calculatedDigest = calculateMD5(updateFile) ?: return false
        return calculatedDigest.equals(md5, ignoreCase = true)
            .also { updateFile.close() }
    }

    /**
     * Calculate an MD5 hash for a given input stream.
     * @param updateFile the file used to calculate.
     * @return the MD5 hash.
     */
    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun calculateMD5(updateFile: Source): String? {
        val md5 = md5Provider.hasher().createHashFunction()
        val buffer = ByteArray(8192)
        var read: Int
        return try {
            while (updateFile.readAvailable(buffer, 0, buffer.size).also { read = it } > 0) {
                md5.update(buffer, 0, read)
            }
            val hex = md5.hash().toHexString(format = HexFormat.UpperCase)
            val output = hex.padStart(32, '0')
            output
        } catch (e: Exception) {
            throw RuntimeException("Unable to process file for MD5", e)
        } finally {
            try {
                withContext(Dispatchers.IO) {
                    updateFile.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

expect object AuthParamsHandler {
    suspend fun extractFile()
    suspend fun getAuthParamStream(): RandomAccessStream
}
