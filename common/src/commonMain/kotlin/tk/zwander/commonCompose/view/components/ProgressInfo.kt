package tk.zwander.commonCompose.view.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.*
import tk.zwander.common.util.toStringDecimal
import tk.zwander.commonCompose.model.BaseModel
import tk.zwander.commonCompose.util.CustomArrangement
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * A progress indicator.
 * @param model the view model to use as a data source.
 */
@Composable
internal fun ProgressInfo(model: BaseModel) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        val statusText by model.statusText.collectAsState()
        val progress by model.progress.collectAsState()
        val hasRunningJobs by model.hasRunningJobs.collectAsState(false)
        val speed by model.speed.collectAsState()

        if (statusText.isNotBlank()) {
            SelectionContainer {
                Text(text = statusText)
            }
        }

        val isIndeterminate = progress.first <= 0 || progress.second <= 0
        val hasProgress = hasRunningJobs || (progress.first > 0 && progress.second > 0)

        AnimatedVisibility(visible = hasProgress) {
            Column {
                Spacer(Modifier.size(8.dp))

                if (isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier.height(16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    val newProgress = (progress.first.toFloat() / progress.second)

                    val animatedProgress by animateFloatAsState(
                        targetValue = minOf(1f, round(newProgress * 1000f) / 1000f), // Get rid of some precision to reduce lag.
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                        visibilityThreshold = 1 / 10000f,
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.height(16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                    )

                    Spacer(Modifier.size(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = CustomArrangement.SpaceBetweenWithSpacing(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val speedKBps = speed / 1024.0
                        val shouldUseMB = speedKBps >= 1 * 1024
                        val finalSpeed =
                            (((if (shouldUseMB) (speedKBps / 1024.0) else speedKBps) * 100.0).roundToInt() / 100.0).toStringDecimal(2)

                        FormatText(
                            text = if (shouldUseMB) stringResource(Res.string.mibs) else stringResource(Res.string.kibs),
                            textFormat = finalSpeed,
                            valueFontFamily = FontFamily.Monospace,
                        )

                        val currentMB = ((progress.first.toFloat() / 1024.0 / 1024.0 * 100.0).roundToInt() / 100.0).toStringDecimal(2)
                        val totalMB = ((progress.second.toFloat() / 1024.0 / 1024.0 * 100.0).roundToInt() / 100.0).toStringDecimal(2)

                        FormatText(
                            text = stringResource(Res.string.mib),
                            textFormat = "$currentMB/$totalMB",
                            valueFontFamily = FontFamily.Monospace,
                        )

                        FormatText(
                            text = "%",
                            textFormat = try {
                                (progress.first.toFloat() / progress.second * 100 * 100.0).roundToInt() / 100.0
                            } catch (_: IllegalArgumentException) {
                                0.0
                            }.toStringDecimal(2),
                            valueFontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
