package tk.zwander.commonCompose.view.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.data.HistoryInfo
import tk.zwander.common.data.changelog.Changelog
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.android
import tk.zwander.common.generated.resources.buildDate
import tk.zwander.common.generated.resources.changelog
import tk.zwander.common.generated.resources.decrypt
import tk.zwander.common.generated.resources.download
import tk.zwander.common.generated.resources.firmware
import tk.zwander.common.generated.resources.lock_open_outline
import tk.zwander.common.generated.resources.unknown
import tk.zwander.commonCompose.util.OffsetCorrectedIdentityTransformation
import tk.zwander.commonCompose.util.monthNames

/**
 * An item in the firmware history list.
 * @param info the information about this item.
 * @param onDownload called when the user hits the "Download" button.
 * @param onDecrypt called when the user hits the "Decrypt" button.
 */
@Composable
internal fun HistoryItem(
    index: Int,
    info: HistoryInfo,
    changelog: Changelog?,
    changelogExpanded: Boolean,
    onChangelogExpanded: (Boolean) -> Unit,
    onDownload: (fw: String) -> Unit,
    onDecrypt: (fw: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        TransparencyCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${index + 1}. ${stringResource(Res.string.android, info.androidVersion 
                            ?: changelog?.androidVer?.let { Regex("[0-9]+").find(it)?.value } 
                            ?: stringResource(Res.string.unknown))}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterVertically),
                        fontSize = 20.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            onDownload(info.firmwareString)
                        },
                        modifier = Modifier.align(Alignment.Bottom)
                            .size(32.dp)
                    ) {
                        Icon(
                            painterResource(Res.drawable.download),
                            stringResource(Res.string.download),
                            Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            onDecrypt(info.firmwareString)
                        },
                        modifier = Modifier.align(Alignment.Bottom)
                            .size(32.dp)
                    ) {
                        Icon(
                            painterResource(Res.drawable.lock_open_outline),
                            stringResource(Res.string.decrypt),
                            Modifier.size(24.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            Res.string.buildDate,
                            info.date?.let {
                                val monthNames = monthNames()
                                LocalDate.Format {
                                    monthName(monthNames)
                                    char(' ')
                                    day(Padding.NONE)
                                    char(',')
                                    char(' ')
                                    year()
                                }.format(it)
                            } ?: stringResource(Res.string.unknown),
                        ),
                        modifier = Modifier.align(Alignment.Bottom),
                        fontSize = 16.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = info.firmwareString,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                            .align(Alignment.CenterVertically),
                        label = { Text(stringResource(Res.string.firmware)) },
                        singleLine = true,
                        visualTransformation = OffsetCorrectedIdentityTransformation(info.firmwareString),
                    )
                }

                if (changelog != null) {
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(Modifier.height(8.dp))

                        Column {
                            ExpandButton(
                                changelogExpanded,
                                stringResource(Res.string.changelog),
                                modifier = Modifier.fillMaxWidth(),
                            ) { onChangelogExpanded(it) }

                            AnimatedVisibility(
                                visible = changelogExpanded
                            ) {
                                Column {
                                    ChangelogDisplay(
                                        changelog = changelog,
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
