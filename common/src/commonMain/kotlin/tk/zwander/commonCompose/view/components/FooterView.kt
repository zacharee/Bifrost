package tk.zwander.commonCompose.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zwander.compose.alertdialog.InWindowAlertDialog
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.about
import tk.zwander.common.generated.resources.currency_usd
import tk.zwander.common.generated.resources.donate
import tk.zwander.common.generated.resources.github
import tk.zwander.common.generated.resources.heart
import tk.zwander.common.generated.resources.mastodon
import tk.zwander.common.generated.resources.ok
import tk.zwander.common.generated.resources.patreon
import tk.zwander.common.generated.resources.patreonSupporters
import tk.zwander.common.util.UrlHandler
import tk.zwander.commonCompose.util.rememberIsOverScaledThreshold

/**
 * The footer shown on all pages.
 */
@Composable
fun FooterView(
    modifier: Modifier = Modifier,
) {
    var showingSupportersDialog by remember { mutableStateOf(false) }
    var showingAboutDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val overThreshold = rememberIsOverScaledThreshold(constraints.maxWidth, 600)

        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(8.dp)
        ) {
            if (overThreshold) {
                AboutInfo()

                Spacer(Modifier.weight(1f))
            }

            LazyRow(
                modifier = Modifier.align(Alignment.Bottom).then(
                    if (overThreshold) {
                        Modifier
                    } else {
                        Modifier.fillMaxWidth()
                    }
                ),
                horizontalArrangement = if (overThreshold) Arrangement.Start else Arrangement.SpaceEvenly
            ) {
                if (!overThreshold) {
                    item {
                        IconButton(
                            onClick = {
                                showingAboutDialog = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(Res.string.about)
                            )
                        }
                    }
                }

                item {
                    IconButton(
                        onClick = {
                            showingSupportersDialog = true
                        }
                    ) {
                        Icon(
                            painterResource(Res.drawable.heart), stringResource(Res.string.patreonSupporters),
                            modifier = Modifier.padding(8.dp).size(24.dp)
                        )
                    }
                }

                item {
                    IconButton(
                        onClick = {
                            UrlHandler.launchUrl("https://github.com/zacharee/SamloaderKotlin")
                        }
                    ) {
                        Icon(
                            painterResource(Res.drawable.github), stringResource(Res.string.github),
                            modifier = Modifier.padding(8.dp).size(24.dp)
                        )
                    }
                }

                item {
                    IconButton(
                        onClick = {
                            UrlHandler.launchUrl("https://androiddev.social/@wander1236")
                        },
                    ) {
                        Icon(
                            painterResource(Res.drawable.mastodon), stringResource(Res.string.mastodon),
                            modifier = Modifier.padding(8.dp).size(24.dp)
                        )
                    }
                }

                item {
                    IconButton(
                        onClick = {
                            UrlHandler.launchUrl("https://patreon.com/zacharywander")
                        },
                    ) {
                        Icon(
                            painterResource(Res.drawable.patreon), stringResource(Res.string.patreon),
                            modifier = Modifier.padding(8.dp).size(24.dp)
                        )
                    }
                }

                item {
                    IconButton(
                        onClick = {
                            UrlHandler.launchUrl("https://www.paypal.com/donate/?hosted_button_id=EWAPDSENZ7U44")
                        },
                    ) {
                        Icon(
                            painterResource(Res.drawable.currency_usd), stringResource(Res.string.donate),
                            modifier = Modifier.padding(8.dp).size(24.dp)
                        )
                    }
                }
            }
        }
    }

    PatreonSupportersDialog(showingSupportersDialog) {
        showingSupportersDialog = false
    }

    InWindowAlertDialog(
        showing = showingAboutDialog,
        onDismissRequest = { showingAboutDialog = false },
        title = {
            Text(text = stringResource(Res.string.about))
        },
        text = {
            AboutInfo()
        },
        buttons = {
            TextButton(onClick = { showingAboutDialog = false }) {
                Text(text = stringResource(Res.string.ok))
            }
        }
    )
}
