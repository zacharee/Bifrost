package tk.zwander.commonCompose.view.pages

import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zwander.kmp.platform.HostOS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyVerticalStaggeredGridScrollbar
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.data.IOptionItem
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.*
import tk.zwander.common.util.BifrostSettings
import tk.zwander.common.util.LocalPhoneInfo
import tk.zwander.common.util.UpdateUtil
import tk.zwander.commonCompose.util.ThemeConstants
import tk.zwander.commonCompose.util.grid.AdaptiveFixed
import tk.zwander.commonCompose.view.LocalMenuBarHeight
import tk.zwander.commonCompose.view.components.FooterView
import tk.zwander.commonCompose.view.components.PhoneInfoView
import tk.zwander.commonCompose.view.components.settingsitems.ActionPreference
import tk.zwander.commonCompose.view.components.settingsitems.BooleanPreference
import tk.zwander.commonCompose.view.components.settingsitems.LiteralPreference

val options = arrayListOf<IOptionItem>().apply {
//    if (isWindows11) {
//        add(
//            IOptionItem.BasicOptionItem.BooleanItem(
//                label = Res.string.useMicaEffect,
//                desc = Res.string.useMicaEffectDesc,
//                key = BifrostSettings.Keys.useMicaEffect,
//            ),
//        )
//    }

    if (HostOS.current == HostOS.MacOS) {
        add(
            IOptionItem.BasicOptionItem.BooleanItem(
                label = Res.string.useVibrancyEffect,
                desc = Res.string.useVibrancyEffectDesc,
                key = BifrostSettings.Keys.useVibrancyEffect,
            ),
        )
    }

    if (HostOS.current == HostOS.Android) {
        add(
            IOptionItem.BasicOptionItem.BooleanItem(
                label = Res.string.use_file_framework,
                desc = Res.string.use_file_framework_desc,
                key = BifrostSettings.Keys.useFileFramework,
                validator = { PlatformSettingsActions.androidHasStoragePermission() },
                onEnabledAction = { PlatformSettingsActions.androidRequestStoragePermission() },
            ),
        )
    }

    add(
        IOptionItem.BasicOptionItem.BooleanItem(
            label = Res.string.allowLowercaseCharacters,
            desc = Res.string.allowLowercaseCharactersDesc,
            key = BifrostSettings.Keys.allowLowercaseCharacters,
        ),
    )
    add(
        IOptionItem.BasicOptionItem.BooleanItem(
            label = Res.string.autoDeleteEncryptedFirmware,
            desc = Res.string.autoDeleteEncryptedFirmwareDesc,
            key = BifrostSettings.Keys.autoDeleteEncryptedFirmware,
        ),
    )
    add(
        IOptionItem.BasicOptionItem.BooleanItem(
            label = Res.string.enable_offline_decryption,
            desc = Res.string.enable_offline_decryption_desc,
            key = BifrostSettings.Keys.enableDecryptKeySave,
        ),
    )
    add(
        IOptionItem.ActionOptionItem(
            label = Res.string.removeSavedData,
            desc = Res.string.removeSavedDataDesc,
            listKey = "remove_saved_data",
            action = {
                BifrostSettings.settings.clear()
            },
        ),
    )
    if (HostOS.current != HostOS.IOS) {
        add(
            IOptionItem.LiteralOptionItem(
                label = Res.string.updates,
                desc = null,
                listKey = "update_checker",
                render = {
                    val scope = rememberCoroutineScope()
                    var availableVersion by rememberSaveable {
                        mutableStateOf<String?>(null)
                    }
                    var loading by remember {
                        mutableStateOf(false)
                    }

                    Row(
                        modifier = it,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier.weight(1f)
                                .heightIn(min = 48.dp)
                                .align(Alignment.CenterVertically),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = loading,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.align(Alignment.Center),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = !loading && availableVersion != null,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Text(
                                    text = availableVersion
                                        ?.takeIf { version -> version.isNotBlank() }
                                        ?.let { version -> stringResource(Res.string.update_available, version) }
                                        ?: stringResource(Res.string.no_updates_available),
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = !loading && availableVersion == null,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Text(
                                    text = stringResource(Res.string.check_for_updates),
                                )
                            }
                        }

                        Crossfade(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            targetState = availableVersion?.isNotBlank() == true,
                        ) { updateAvailable ->
                            Box(
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                if (updateAvailable) {
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                loading = true
                                                UpdateUtil.installUpdate()
                                                loading = false
                                            }
                                        },
                                        enabled = !loading,
                                    ) {
                                        Text(text = stringResource(Res.string.update))
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                loading = true
                                                availableVersion = UpdateUtil.checkForUpdate()?.newVersion ?: ""
                                                loading = false
                                            }
                                        },
                                        enabled = !loading,
                                    ) {
                                        Text(text = stringResource(Res.string.check))
                                    }
                                }
                            }
                        }
                    }
                },
            ),
        )
    }
}

@Composable
fun SettingsAboutView() {
    val gridState = rememberLazyStaggeredGridState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyVerticalStaggeredGridScrollbar(
            state = gridState,
            modifier = Modifier.weight(1f),
            settings = ThemeConstants.ScrollBarSettings.Default,
        ) {
            LazyVerticalStaggeredGrid(
                columns = AdaptiveFixed(minSize = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
                state = gridState,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 8.dp + LocalMenuBarHeight.current,
                    bottom = 8.dp,
                ),
            ) {
                items(items = options, key = { it.listKey }) { item ->
                    Box(
                        modifier = Modifier.widthIn(max = 400.dp),
                    ) {
                        when (item) {
                            is IOptionItem.ActionOptionItem -> {
                                ActionPreference(item = item)
                            }

                            is IOptionItem.BasicOptionItem.BooleanItem -> {
                                BooleanPreference(item = item)
                            }

                            is IOptionItem.LiteralOptionItem -> {
                                LiteralPreference(item = item)
                            }

                            // TODO: Layouts for other settings types.
                        }
                    }
                }
            }
        }

        if (HostOS.current == HostOS.Android && LocalPhoneInfo.current != null) {
            PhoneInfoView(modifier = Modifier.fillMaxWidth())
        }

        FooterView(
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

expect object PlatformSettingsActions {
    fun androidHasStoragePermission(): Boolean
    fun androidRequestStoragePermission()
}

