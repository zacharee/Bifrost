package tk.zwander.common.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import tk.zwander.common.util.SettingsKey

sealed interface IOptionItem {
    val label: StringResource
    val desc: StringResource?
    val listKey: String

    data class ActionOptionItem(
        override val label: StringResource,
        override val desc: StringResource?,
        override val listKey: String,
        val action: suspend () -> Unit,
    ) : IOptionItem

    data class LiteralOptionItem(
        override val label: StringResource,
        override val desc: StringResource?,
        override val listKey: String,
        val render: @Composable (Modifier) -> Unit,
    ) : IOptionItem

    sealed interface BasicOptionItem<T> : IOptionItem {
        val key: SettingsKey<T>
        override val listKey: String
            get() = key.key

        data class BooleanItem(
            override val label: StringResource,
            override val desc: StringResource?,
            override val key: SettingsKey<Boolean>,
            val validator: () -> Boolean = { true },
            val onEnabledAction: () -> Unit = {},
        ) : BasicOptionItem<Boolean>
    }
}
