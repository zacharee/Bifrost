package tk.zwander.commonCompose.view.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import my.nanihadesuka.compose.ColumnScrollbar
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.data.changelog.Changelog
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.release
import tk.zwander.common.generated.resources.security
import tk.zwander.commonCompose.util.ThemeConstants
import tk.zwander.commonCompose.util.toRichHtmlString

@Composable
internal fun ChangelogDisplay(
    changelog: Changelog?,
    border: BorderStroke = CardDefaults.outlinedCardBorder(),
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    var scrollHeightDp by remember {
        mutableStateOf(0.dp)
    }

    OutlinedCard(
        modifier = Modifier.padding(vertical = 4.dp)
            .heightIn(max = min(500.dp, scrollHeightDp)),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = border,
    ) {
        ColumnScrollbar(
            state = scrollState,
            settings = ThemeConstants.ScrollBarSettings.Default,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(scrollState)
                    .onSizeChanged {
                        scrollHeightDp = with (density) { it.height.toDp() }
                    }
                    .padding(8.dp),
            ) {
                val infoItems = listOfNotNull(
                    changelog?.relDate?.let { relDate ->
                        stringResource(Res.string.release, relDate)
                    },
                    changelog?.secPatch?.let { secPatch ->
                        stringResource(Res.string.security, secPatch)
                    },
                )

                if (infoItems.isNotEmpty()) {
                    Text(
                        text = infoItems.joinToString("  •  "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (changelog?.notes != null) {
                    SelectionContainer {
                        Text(
                            text = changelog.notes.toRichHtmlString(),
                            lineHeight = (LocalTextStyle.current.fontSize.value + 2).sp,
                        )
                    }
                }
            }
        }
    }
}
