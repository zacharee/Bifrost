package tk.zwander.commonCompose.view.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import io.ktor.http.URLBuilder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.*
import tk.zwander.common.util.LocalPhoneInfo
import tk.zwander.common.util.UrlHandler
import tk.zwander.commonCompose.util.asClipEntry

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneInfoView(
    modifier: Modifier = Modifier,
) {
    val phoneInfo = LocalPhoneInfo.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var expanded by remember {
        mutableStateOf(true)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExpandButton(
            expanded = expanded,
            text = stringResource(Res.string.phoneInfo),
            onExpandChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(
            visible = !expanded,
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.tacFormat, phoneInfo?.tac ?: ""),
                    )

                    Text(
                        text = stringResource(Res.string.modelFormat, phoneInfo?.model ?: ""),
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        buildString {
                                            appendLine("TAC,Model")
                                            appendLine("${phoneInfo?.tac},${phoneInfo?.model}")
                                        }.asClipEntry(),
                                    )
                                }
                            },
                        ) {
                            Text(text = stringResource(Res.string.copy))
                        }

                        Button(
                            onClick = {
                                val urlBuilder =
                                    URLBuilder("https://github.com/zacharee/SamloaderKotlin/issues/new")
                                urlBuilder.parameters["template"] = "imei-database-request.md"
                                urlBuilder.parameters["title"] =
                                    "[Device IMEI Request] ${phoneInfo?.model}"
                                urlBuilder.parameters["body"] = buildString {
                                    appendLine("TAC,Model")
                                    appendLine("${phoneInfo?.tac},${phoneInfo?.model}")
                                }

                                UrlHandler.launchUrl(urlBuilder.buildString(), true)
                            },
                        ) {
                            Text(text = stringResource(Res.string.fileIssue))
                        }
                    }
                }
            }
        }
    }
}
