package tk.zwander.common.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.zwander.common.util.RandomAccessStream
import tk.zwander.samloaderkotlin.resources.MR
import tk.zwander.samsungfirmwaredownloader.App
import java.io.File
import java.io.RandomAccessFile

actual object AuthParamsHandler {
    val tempFile: File by lazy {
        File(
            App.instance.cacheDir,
            "auth_param.dat",
        ).also {
            it.parentFile?.mkdirs()
        }
    }
    val tempFileRaf by lazy {
        RandomAccessFile(tempFile, "r")
    }

    actual suspend fun extractFile() {
        tempFile.delete()
        tempFile.createNewFile()
        withContext(Dispatchers.IO) {
            App.instance.resources.openRawResource(MR.files.auth_param_dat.rawResId).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    actual suspend fun getAuthParamStream(): RandomAccessStream {
        return object : RandomAccessStream {
            override fun get(pos: Long): UByte {
                tempFileRaf.seek(pos)
                return tempFileRaf.readByte().toUByte()
            }

            override fun get(pos: Long, len: Int): UByteArray {
                tempFileRaf.seek(pos)

                val bytes = ByteArray(len)
                tempFileRaf.readFully(bytes, 0, len)

                return bytes.toUByteArray()
            }
        }
    }
}
