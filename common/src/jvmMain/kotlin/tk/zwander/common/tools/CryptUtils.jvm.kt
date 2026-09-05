package tk.zwander.common.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.harawata.appdirs.AppDirsFactory
import tk.zwander.common.GradleConfig
import tk.zwander.common.util.RandomAccessStream
import java.io.File
import java.io.RandomAccessFile

actual object AuthParamsHandler {
    val tempFile = File(
        AppDirsFactory.getInstance()
            .getUserDataDir(GradleConfig.appName, null, "Zachary Wander"),
        "auth_param.dat",
    )
    val tempFileRaf by lazy {
        RandomAccessFile(tempFile, "r")
    }

    actual suspend fun extractFile() {
        tempFile.delete()
        tempFile.createNewFile()
        withContext(Dispatchers.IO) {
            this::class.java.classLoader.getResourceAsStream("files/auth_param.dat")?.use { input ->
                println("COPYING")
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

                return bytes.map { it.toUByte() }.toUByteArray()
            }
        }
    }
}
