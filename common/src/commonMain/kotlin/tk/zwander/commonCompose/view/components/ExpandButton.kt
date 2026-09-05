package tk.zwander.commonCompose.view.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.hide
import tk.zwander.common.generated.resources.show

@Composable
internal fun ExpandButton(
    expanded: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    onExpandChange: (Boolean) -> Unit,
) {
    val expandText = buildAnnotatedString {
        append(text)
        append(" ")
        appendInlineContent("expandIcon", "[icon]")
    }
    val animatedRotation by animateFloatAsState(if (expanded) 180f else 0f)

    val inlineContent = mapOf(
        "expandIcon" to InlineTextContent(
            placeholder = Placeholder(
                16.sp,
                16.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) Res.string.hide else Res.string.show),
                modifier = Modifier.rotate(animatedRotation),
            )
            Crossfade(expanded) {
                if (it) {
                    Icon(Icons.Filled.KeyboardArrowUp, "")
                } else {
                    Icon(Icons.Filled.KeyboardArrowDown, "")
                }
            }
        }
    )

    TextButton(
        onClick = {
            onExpandChange(!expanded)
        },
        modifier = modifier,
    ) {
        Text(
            text = expandText,
            inlineContent = inlineContent,
        )
    }
}
