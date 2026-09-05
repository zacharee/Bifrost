package tk.zwander.common.exceptions

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.noBinaryFile

class NoBinaryFileError(model: String, region: String) : Exception(runBlocking { getString(Res.string.noBinaryFile, model, region) })
