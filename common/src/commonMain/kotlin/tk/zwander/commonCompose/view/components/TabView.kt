@file:OptIn(ExperimentalTime::class)

package tk.zwander.commonCompose.view.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.*
import tk.zwander.commonCompose.view.LocalUseTransparencyEffects
import tk.zwander.commonCompose.view.components.Page.Decrypter
import tk.zwander.commonCompose.view.components.Page.Downloader
import tk.zwander.commonCompose.view.components.Page.History
import tk.zwander.commonCompose.view.components.Page.SettingsAbout
import tk.zwander.commonCompose.view.pages.DecryptView
import tk.zwander.commonCompose.view.pages.DownloadView
import tk.zwander.commonCompose.view.pages.HistoryView
import tk.zwander.commonCompose.view.pages.SettingsAboutView
import kotlin.time.ExperimentalTime

val pages = arrayOf(Downloader, Decrypter, History, SettingsAbout)

/**
 * Represents the available pages.
 */
sealed class Page(
    val render: @Composable () -> Unit,
    val labelRes: StringResource,
    val iconRes: DrawableResource,
    val index: Int,
) {
    data object Downloader : Page({ DownloadView() }, Res.string.downloader, Res.drawable.download, 0)

    data object Decrypter :
        Page({ DecryptView() }, Res.string.decrypter, Res.drawable.lock_open_outline, 1)

    data object History : Page({ HistoryView() }, Res.string.history, Res.drawable.history, 2)

    data object SettingsAbout : Page({ SettingsAboutView() }, Res.string.more, Res.drawable.cog, 3)
}

/**
 * Allows the user to switch among the different app pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabView(
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
) {
    val density = LocalDensity.current

    val ellipses = remember(density.fontScale, density.density) {
        mutableStateMapOf<Int, Boolean>()
    }
    val anyEllipsis by remember {
        derivedStateOf { ellipses.values.any { it } }
    }

    PrimaryTabRow(
        modifier = Modifier.fillMaxWidth()
            .height(48.dp),
        selectedTabIndex = selectedPage,
        divider = {},
        containerColor = if (LocalUseTransparencyEffects.current) Color.Transparent else TabRowDefaults.primaryContainerColor,
    ) {
        pages.forEachIndexed { index, page ->
            Tab(
                selected = selectedPage == page.index,
                text = {
                    Crossfade(
                        targetState = anyEllipsis,
                    ) {
                        if (it) {
                            Box(
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(page.labelRes),
                                    maxLines = 1,
                                    onTextLayout = { layoutResult ->
                                        ellipses[index] = layoutResult.multiParagraph.didExceedMaxLines
                                    },
                                    modifier = Modifier.alpha(0f),
                                    overflow = TextOverflow.Clip,
                                    color = Color.Transparent,
                                )

                                Icon(
                                    painter = painterResource(page.iconRes),
                                    contentDescription = stringResource(page.labelRes),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(page.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                        .alpha(0f),
                                )

                                Text(
                                    text = stringResource(page.labelRes),
                                    maxLines = 1,
                                    onTextLayout = { layoutResult ->
                                        ellipses[index] = layoutResult.multiParagraph.didExceedMaxLines
                                    },
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                },
                onClick = {
                    onPageSelected(page.index)
                },
                modifier = Modifier.animateContentSize(),
            )
        }
    }
}
