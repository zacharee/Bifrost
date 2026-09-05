package tk.zwander.commonCompose.view.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.zwander.compose.alertdialog.InWindowAlertDialog
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.ColumnScrollbar
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.GradleConfig
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.*
import tk.zwander.common.tools.delegates.Downloader
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.isAccessoryModel
import tk.zwander.commonCompose.locals.LocalDownloadModel
import tk.zwander.commonCompose.util.ThemeConstants
import tk.zwander.commonCompose.util.collectAsMutableState
import tk.zwander.commonCompose.view.LocalMenuBarHeight
import tk.zwander.commonCompose.view.components.ChangelogDisplay
import tk.zwander.commonCompose.view.components.ExpandButton
import tk.zwander.commonCompose.view.components.HybridButton
import tk.zwander.commonCompose.view.components.MRFLayout
import tk.zwander.commonCompose.view.components.ProgressInfo
import kotlin.time.ExperimentalTime

/**
 * The Downloader View.
 */
@ExperimentalTime
@Composable
internal fun DownloadView() {
    val model = LocalDownloadModel.current

    val hasRunningJobs by model.hasRunningJobs.collectAsState(false)
    var manual by model.manual.collectAsMutableState()
    val modelModel by model.model.collectAsState()
    val region by model.region.collectAsState()
    val fw by model.fw.collectAsState()
    val osCode by model.osCode.collectAsState()
    val progress by model.progress.collectAsState()
    val statusText by model.statusText.collectAsState()
    val changelog by model.changelog.collectAsState()
    var changelogExpanded by model.changelogExpanded.collectAsMutableState()

    val canCheckVersion = !manual && modelModel.isNotBlank() && region.isNotBlank() && !hasRunningJobs

    val canDownload = modelModel.isNotBlank() && region.isNotBlank() && fw.isNotBlank()
            && !hasRunningJobs

    val canChangeOption = !hasRunningJobs

    val scope = rememberCoroutineScope()

    var downloadErrorInfo by remember {
        mutableStateOf<Downloader.DownloadErrorInfo?>(null)
    }

    val scrollState = rememberScrollState()

    ColumnScrollbar(
        state = scrollState,
        settings = ThemeConstants.ScrollBarSettings.Default,
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(8.dp)
                .verticalScroll(scrollState),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = 8.dp, top = LocalMenuBarHeight.current),
            ) {
                val constraints = constraints
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HybridButton(
                        onClick = {
                            model.launchJob {
                                Downloader.onDownload(
                                    model,
                                    confirmCallback = object : Downloader.DownloadErrorCallback {
                                        override fun onError(info: Downloader.DownloadErrorInfo) {
                                            downloadErrorInfo = info
                                        }
                                    },
                                )
                            }
                        },
                        enabled = canDownload,
                        vectorIcon = painterResource(Res.drawable.download),
                        text = stringResource(Res.string.download),
                        description = stringResource(Res.string.downloadFirmware),
                        parentSize = constraints.maxWidth
                    )

                    Spacer(Modifier.width(8.dp))

                    HybridButton(
                        onClick = {
                            model.launchJob {
                                Downloader.onFetch(model)
                            }
                        },
                        enabled = canCheckVersion,
                        text = stringResource(Res.string.checkForUpdates),
                        vectorIcon = painterResource(Res.drawable.refresh),
                        description = stringResource(Res.string.checkForUpdatesDesc),
                        parentSize = constraints.maxWidth,
                    )

                    Spacer(Modifier.weight(1f))

                    HybridButton(
                        onClick = {
                            scope.launch {
                                eventManager.sendEvent(Event.Download.Finish)
                            }
                            model.endJob("")
                        },
                        enabled = hasRunningJobs,
                        text = stringResource(Res.string.cancel),
                        description = stringResource(Res.string.cancel),
                        vectorIcon = painterResource(Res.drawable.cancel),
                        parentSize = constraints.maxWidth
                    )
                }
            }

            val boxSource = remember { MutableInteractionSource() }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.animateContentSize()
                    .padding(bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier.clickable(
                        interactionSource = boxSource,
                        indication = null,
                        enabled = canChangeOption,
                    ) {
                        manual = !manual
                    }
                        .padding(4.dp)
                ) {
                    Checkbox(
                        checked = manual,
                        onCheckedChange = {
                            manual = it
                        },
                        modifier = Modifier.align(Alignment.CenterVertically),
                        enabled = canChangeOption,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                        interactionSource = boxSource
                    )

                    Spacer(Modifier.width(8.dp))


                    Text(
                        text = stringResource(Res.string.manual),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }

                AnimatedVisibility(
                    visible = manual,
                    enter = fadeIn() + expandIn(expandFrom = Alignment.CenterStart),
                    exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.CenterStart),
                ) {
                    var showingRequestWarningDialog by remember { mutableStateOf(false) }

                    val textStyle =
                        LocalTextStyle.current.copy(LocalContentColor.current)

                    Row {
                        Spacer(Modifier.width(32.dp))

                        val manualWarning = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                append(stringResource(Res.string.manualWarning))
                                append(" ")

                                withStyle(
                                    SpanStyle(textDecoration = TextDecoration.Underline),
                                ) {
                                    withLink(LinkAnnotation.Clickable("moreInfo", linkInteractionListener = {
                                        showingRequestWarningDialog = true
                                    })) {
                                        append(stringResource(Res.string.moreInfo))
                                    }
                                }
                            }
                        }

                        Text(
                            text = manualWarning,
                            style = LocalTextStyle.current,
                        )
                    }

                    InWindowAlertDialog(
                        showing = showingRequestWarningDialog,
                        title = {
                            Text(text = stringResource(Res.string.moreInfo))
                        },
                        text = {
                            val info = buildAnnotatedString {
                                append(
                                    stringResource(
                                        Res.string.manualWarningDetails,
                                        GradleConfig.appName,
                                        GradleConfig.appName,
                                    ),
                                )
                                append(" ")
                                withLink(LinkAnnotation.Url("https://github.com/zacharee/SamloaderKotlin/issues/10")) {
                                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                        append(stringResource(Res.string.manualWarningDetails2))
                                    }
                                }
                                append(" ")
                                append(stringResource(Res.string.manualWarningDetails3))
                            }

                            Text(
                                text = info,
                                style = textStyle,
                            )
                        },
                        buttons = {
                            TextButton(
                                onClick = {
                                    showingRequestWarningDialog = false
                                }
                            ) {
                                Text(stringResource(Res.string.ok))
                            }
                        },
                        onDismissRequest = {
                            showingRequestWarningDialog = false
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = modelModel.isAccessoryModel,
                enter = fadeIn() + expandIn(expandFrom = Alignment.CenterStart),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.CenterStart),
            ) {
                Text(
                    text = stringResource(Res.string.accessory_model_warning),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            MRFLayout(
                model,
                canChangeOption,
                manual && canChangeOption,
            )

            AnimatedVisibility(
                visible = !manual && osCode.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val displayCode = remember {
                    osCode
                }

                Column {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = stringResource(Res.string.osVersion, displayCode),
                    )
                }
            }

            AnimatedVisibility(
                visible = hasRunningJobs || progress.first > 0 || progress.second > 0 || statusText.isNotBlank(),
            ) {
                Column {
                    Spacer(modifier = Modifier.size(4.dp))

                    ProgressInfo(model)
                }
            }

            val changelogCondition =
                changelog != null && !manual && !hasRunningJobs && fw.isNotBlank()

            AnimatedVisibility(
                visible = changelogCondition,
            ) {
                ExpandButton(
                    changelogExpanded,
                    stringResource(Res.string.changelog),
                    modifier = Modifier.fillMaxWidth(),
                ) { changelogExpanded = it }
            }

            AnimatedVisibility(
                visible = changelogExpanded && changelogCondition,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ChangelogDisplay(changelog)
            }
        }
    }

    InWindowAlertDialog(
        showing = downloadErrorInfo != null,
        onDismissRequest = {
            model.launchJob {
                downloadErrorInfo?.callback?.onCancel?.invoke()
                downloadErrorInfo = null
            }
        },
        title = {
            Text(text = stringResource(Res.string.warning))
        },
        text = {
            Text(text = downloadErrorInfo?.message ?: "")
        },
        buttons = {
            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = {
                    model.launchJob {
                        val info = downloadErrorInfo
                        downloadErrorInfo = null
                        info?.callback?.onCancel?.invoke()
                    }
                }
            ) {
                Text(text = stringResource(Res.string.no))
            }

            TextButton(
                onClick = {
                    model.launchJob {
                        val info = downloadErrorInfo
                        downloadErrorInfo = null
                        info?.callback?.onAccept?.invoke()
                    }
                }
            ) {
                Text(
                    text = stringResource(Res.string.yes),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}
