package tk.zwander.commonCompose.util

import androidx.compose.runtime.Composable
import kotlinx.datetime.format.MonthNames
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.april_short
import tk.zwander.common.generated.resources.august_short
import tk.zwander.common.generated.resources.december_short
import tk.zwander.common.generated.resources.february_short
import tk.zwander.common.generated.resources.january_short
import tk.zwander.common.generated.resources.july_short
import tk.zwander.common.generated.resources.june_short
import tk.zwander.common.generated.resources.march_short
import tk.zwander.common.generated.resources.may_short
import tk.zwander.common.generated.resources.november_short
import tk.zwander.common.generated.resources.october_short
import tk.zwander.common.generated.resources.september_short

@Composable
fun monthNames(): MonthNames {
    val resources = listOf(
        Res.string.january_short,
        Res.string.february_short,
        Res.string.march_short,
        Res.string.april_short,
        Res.string.may_short,
        Res.string.june_short,
        Res.string.july_short,
        Res.string.august_short,
        Res.string.september_short,
        Res.string.october_short,
        Res.string.november_short,
        Res.string.december_short,
    )

    return MonthNames(resources.map { stringResource(it) })
}
