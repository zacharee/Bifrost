package tk.zwander.commonCompose.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.zwander.compose.alertdialog.InWindowAlertDialog
import my.nanihadesuka.compose.LazyColumnScrollbar
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.data.csc.CSCDB
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.ascending
import tk.zwander.common.generated.resources.cancel
import tk.zwander.common.generated.resources.carriers
import tk.zwander.common.generated.resources.chooseCsc
import tk.zwander.common.generated.resources.clear
import tk.zwander.common.generated.resources.countries
import tk.zwander.common.generated.resources.csc
import tk.zwander.common.generated.resources.descending
import tk.zwander.common.generated.resources.search
import tk.zwander.common.generated.resources.sort
import tk.zwander.common.generated.resources.unfold_more
import tk.zwander.commonCompose.model.CSCDialogModel
import tk.zwander.commonCompose.model.Column
import tk.zwander.commonCompose.model.SelectionState
import tk.zwander.commonCompose.util.OffsetCorrectedIdentityTransformation
import tk.zwander.commonCompose.util.ThemeConstants
import tk.zwander.commonCompose.util.collectAsImmediateMutableState
import tk.zwander.commonCompose.util.collectAsMutableState

@Composable
fun CSCChooserDialog(
    modifier: Modifier = Modifier,
    showing: Boolean,
    onDismissRequest: () -> Unit,
    onCscSelected: (String) -> Unit,
) {
    InWindowAlertDialog(
        modifier = modifier,
        showing = showing,
        title = {
            Text(text = stringResource(Res.string.chooseCsc))
        },
        text = {
            var filter by CSCDialogModel.filter.collectAsImmediateMutableState()
            var selectedColumn by CSCDialogModel.selectedColumn.collectAsMutableState()
            var sortState by CSCDialogModel.sortState.collectAsMutableState()
            var scrollPosition by CSCDialogModel.scrollPosition.collectAsMutableState()
            val items by CSCDialogModel.filteredItems.collectAsState(listOf())

            val listState = rememberLazyListState()

            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                    .collect { firstVisibleItem ->
                        if (items.isNotEmpty()) {
                            scrollPosition = firstVisibleItem
                        }
                    }
            }

            LaunchedEffect(items.size) {
                listState.scrollToItem(scrollPosition.first, scrollPosition.second)
            }

            fun onColumnClick(column: Column) {
                if (column == selectedColumn) {
                    sortState = when (sortState) {
                        SelectionState.ASCENDING -> SelectionState.DESCENDING
                        else -> SelectionState.ASCENDING
                    }
                } else {
                    selectedColumn = column
                }
            }

            val columnStates by derivedStateOf {
                mapOf(
                    Column.CSC to if (selectedColumn == Column.CSC) sortState else SelectionState.NONE,
                    Column.CARRIER to if (selectedColumn == Column.CARRIER) sortState else SelectionState.NONE,
                    Column.COUNTRY to if (selectedColumn == Column.COUNTRY) sortState else SelectionState.NONE,
                )
            }

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = {
                    Text(text = stringResource(Res.string.search))
                },
                trailingIcon = if (filter.isNotBlank()) {
                    {
                        IconButton(
                            onClick = { filter = "" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.clear),
                            )
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = OffsetCorrectedIdentityTransformation(filter),
            )

            LazyColumnScrollbar(
                state = listState,
                settings = ThemeConstants.ScrollBarSettings.Default,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = listState,
                ) {
                    val codeWeight = 0.2f
                    val carrierWeight = 0.4f
                    val countryWeight = 0.4f

                    stickyHeader {
                        Row(
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            HeaderItem(
                                text = stringResource(Res.string.csc),
                                modifier = Modifier.weight(codeWeight),
                                state = columnStates[Column.CSC]!!,
                                onClick = { onColumnClick(Column.CSC) }
                            )

                            HeaderItem(
                                text = stringResource(Res.string.countries),
                                modifier = Modifier.weight(countryWeight),
                                state = columnStates[Column.COUNTRY]!!,
                                onClick = { onColumnClick(Column.COUNTRY) }
                            )

                            HeaderItem(
                                text = stringResource(Res.string.carriers),
                                modifier = Modifier.weight(carrierWeight),
                                state = columnStates[Column.CARRIER]!!,
                                onClick = { onColumnClick(Column.CARRIER) }
                            )
                        }
                    }
                    items(items, { it.code }) { item ->
                        OutlinedCard(
                            onClick = {
                                onCscSelected(item.code)
                                onDismissRequest()
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = item.code,
                                    modifier = Modifier.weight(codeWeight)
                                )

                                Text(
                                    text = item.countries.map { CSCDB.getCountryName(it) }
                                        .sortedBy { it.lowercase() }.joinToString(",\n"),
                                    modifier = Modifier.weight(countryWeight)
                                )

                                Text(
                                    text = item.carriers?.sortedBy { it.lowercase() }
                                        ?.joinToString(",\n") ?: "",
                                    modifier = Modifier.weight(carrierWeight)
                                )
                            }
                        }
                    }
                }
            }
        },
        buttons = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(text = stringResource(Res.string.cancel))
            }
        },
        onDismissRequest = onDismissRequest,
        contentsScrollable = false,
    )
}

@Composable
private fun HeaderItem(
    text: String,
    onClick: () -> Unit,
    state: SelectionState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
        )

        Icon(
            painter = when (state) {
                SelectionState.ASCENDING -> rememberVectorPainter(Icons.Default.KeyboardArrowUp)
                SelectionState.DESCENDING -> rememberVectorPainter(Icons.Default.KeyboardArrowDown)
                SelectionState.NONE -> painterResource(Res.drawable.unfold_more)
            },
            contentDescription = when (state) {
                SelectionState.ASCENDING -> stringResource(Res.string.ascending)
                SelectionState.DESCENDING -> stringResource(Res.string.descending)
                SelectionState.NONE -> stringResource(Res.string.sort)
            },
            modifier = Modifier.alpha(if (state == SelectionState.NONE) 0.5f else 1f)
                .size(24.dp),
        )
    }
}
