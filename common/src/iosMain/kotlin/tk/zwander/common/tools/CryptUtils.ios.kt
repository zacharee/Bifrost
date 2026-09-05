package tk.zwander.common.tools

import dev.zwander.kotlin.file.PlatformFile
import kotlinx.cinterop.*
import kotlinx.io.asSource
import platform.Foundation.*
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.util.RandomAccessStream

@OptIn(ExperimentalForeignApi::class)
actual object AuthParamsHandler {
    val tempFile = PlatformFile(
        PlatformFile(
            NSFileManager.defaultManager.URLsForDirectory(
                NSDocumentDirectory,
                NSUserDomainMask
            ).first() as NSURL
        ),
        "auth_param.dat",
    )

    actual suspend fun extractFile() {
        tempFile.delete()
        tempFile.createNewFile()
        NSInputStream(NSURL.URLWithString(Res.getUri("files/auth_param.dat"))!!).asSource().use { input ->
            tempFile.openOutputStream(append = false, truncate = false)?.use { output ->
                output.transferFrom(input)
            }
        }

        println("TEMP FILE ${tempFile.getAbsolutePath()} ${tempFile.getLength()}")
    }

    @OptIn(BetaInteropApi::class)
    actual suspend fun getAuthParamStream(): RandomAccessStream {
        return object : RandomAccessStream {
            val stream = NSFileHandle.fileHandleForReadingAtPath(tempFile.getAbsolutePath())

            override fun get(pos: Long): UByte {
                return get(pos, 1).first()
            }

            override fun get(pos: Long, len: Int): UByteArray {

                return memScoped {
                    val error = alloc<ObjCObjectVar<NSError?>>()
                    stream?.seekToOffset(pos.toULong(), error.ptr)

                    if (error.value != null) {
                        throw IllegalStateException(error.value?.toString())
                    }

                    try {
                        stream?.readDataUpToLength(len.toULong(), error.ptr)?.bytes?.readBytes(len)
                            ?.toUByteArray() ?: ubyteArrayOf()
                    } finally {
                        if (error.value != null) {
                            throw IllegalStateException(error.value?.toString())
                        }
                    }
                }
            }
        }
    }
}
