package tk.zwander.common.tools

import dev.zwander.kotlin.file.PlatformFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVarOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.io.asSource
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSInputStream
import platform.Foundation.NSNumber
import platform.Foundation.NSStreamFileCurrentOffsetKey
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.numberWithUnsignedLong
import tk.zwander.common.util.RandomAccessStream
import tk.zwander.samloaderkotlin.resources.MR

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
    val stream = NSInputStream(NSURL.fileURLWithPath(tempFile.getAbsolutePath()))

    actual suspend fun extractFile() {
        tempFile.delete()
        tempFile.createNewFile()
        NSInputStream(MR.files.auth_param_dat.url).asSource().use { input ->
            tempFile.openOutputStream(append = false, truncate = false)?.use { output ->
                output.transferFrom(input)
            }
        }
    }

    actual suspend fun getAuthParamStream(): RandomAccessStream {
        return object : RandomAccessStream {
            override fun get(pos: Long): UByte {
                return get(pos, 1).first()
            }

            override fun get(pos: Long, len: Int): UByteArray {
                stream.setProperty(
                    NSNumber.numberWithUnsignedLong(pos.toULong()),
                    NSStreamFileCurrentOffsetKey,
                )

                return memScoped {
                    val buffer = allocArray<UByteVarOf<UByte>>(len)

                    stream.read(buffer, len.toULong())

                    buffer.readBytes(len).toUByteArray()
                }
            }
        }
    }
}
