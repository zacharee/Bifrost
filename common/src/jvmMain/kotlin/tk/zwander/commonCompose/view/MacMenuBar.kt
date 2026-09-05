package tk.zwander.commonCompose.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import dev.zwander.kmp.platform.HostOS
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.*
import tk.zwander.common.util.UrlHandler

@Composable
fun FrameWindowScope.MacMenuBar(
    mainWindowState: WindowState,
    applicationScope: ApplicationScope,
) {
    if (HostOS.current == HostOS.MacOS) {
        MenuBar {
            Menu(
                text = stringResource(Res.string.window),
            ) {
                Item(
                    text = stringResource(Res.string.minimize),
                    onClick = {
                        mainWindowState.isMinimized = true
                    },
                    shortcut = KeyShortcut(Key.M, meta = true),
                )

                Item(
                    text = stringResource(Res.string.zoom),
                    onClick = {
                        mainWindowState.placement = WindowPlacement.Maximized
                    },
                )

                Item(
                    text = stringResource(Res.string.close),
                    onClick = {
                        applicationScope.exitApplication()
                    },
                    shortcut = KeyShortcut(Key.W, meta = true),
                )
            }

            Menu(
                text = stringResource(Res.string.help),
            ) {
                Item(
                    text = stringResource(Res.string.github),
                    onClick = {
                        UrlHandler.launchUrl("https://github.com/zacharee/SamloaderKotlin")
                    },
                )

                Item(
                    text = stringResource(Res.string.mastodon),
                    onClick = {
                        UrlHandler.launchUrl("https://androiddev.social/@wander1236")
                    },
                )

                Item(
                    text = stringResource(Res.string.patreon),
                    onClick = {
                        UrlHandler.launchUrl("https://patreon.com/zacharywander")
                    },
                )

                Item(
                    text = stringResource(Res.string.donate),
                    onClick = {
                        UrlHandler.launchUrl("https://www.paypal.com/donate/?hosted_button_id=EWAPDSENZ7U44")
                    },
                )
            }
        }
    }
}
