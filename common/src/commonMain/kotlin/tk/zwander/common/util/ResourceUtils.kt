package tk.zwander.common.util

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

suspend operator fun StringResource.invoke(vararg args: Any?) = getString(this, args)
