package com.github.damontecres.stashapp.folders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.folders.data.FolderNode

/**
 * NeXTSTEP/Finder column-style folder tree. Each visible column shows the children
 * of the previously-focused folder. The leftmost column always shows the server's
 * top-level folders.
 *
 * D-pad model:
 *  - Up/Down: move focus among siblings inside the active column.
 *  - Right: drill in. Pushes a new column for the focused folder's children.
 *  - Left: drill out. Pops the rightmost column (no-op when only the root is left).
 *  - DPAD_CENTER / Enter: activate the focused folder — its scenes drive the right pane.
 *
 * When the stack grows past [MAX_VISIBLE_COLUMNS] we render only the last N columns
 * plus a small breadcrumb summarising the hidden depth, so very deep trees don't
 * pancake into unreadable thin strips on a 10-foot UI.
 */
private const val MAX_VISIBLE_COLUMNS = 4
private const val COLUMN_WIDTH_DP = 240

@Composable
fun FolderTreeColumns(
    columnStack: List<String>,
    observeColumn: (String) -> kotlinx.coroutines.flow.Flow<List<FolderNode>>,
    onDrillIn: (FolderNode) -> Unit,
    onDrillOut: () -> Boolean,
    onActivate: (FolderNode) -> Unit,
    onMoveFocusToGrid: () -> Unit,
    modifier: Modifier = Modifier,
    treeFocusRequester: FocusRequester? = null,
) {
    val visibleColumns =
        if (columnStack.size <= MAX_VISIBLE_COLUMNS) {
            columnStack
        } else {
            columnStack.takeLast(MAX_VISIBLE_COLUMNS)
        }
    val hiddenDepth = columnStack.size - visibleColumns.size

    Row(
        modifier =
            modifier
                .fillMaxHeight(),
    ) {
        if (hiddenDepth > 0) {
            BreadcrumbCollapsedSummary(
                hiddenDepth = hiddenDepth,
                modifier = Modifier.fillMaxHeight(),
            )
        }
        visibleColumns.forEachIndexed { index, parentPath ->
            val isRightmost = index == visibleColumns.lastIndex
            // Only the rightmost column hosts the focus initially; deeper drill-ins
            // re-target the requester. Earlier columns are read-only context.
            val perColumnFocus =
                remember(parentPath) { if (isRightmost) treeFocusRequester else null }
            FolderTreeColumn(
                parentPath = parentPath,
                observeColumn = observeColumn,
                isRightmost = isRightmost,
                onDrillIn = onDrillIn,
                onDrillOut = onDrillOut,
                onActivate = onActivate,
                onMoveFocusToGrid = onMoveFocusToGrid,
                focusRequester = perColumnFocus,
                modifier =
                    Modifier
                        .width(COLUMN_WIDTH_DP.dp)
                        .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun BreadcrumbCollapsedSummary(
    hiddenDepth: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$hiddenDepth",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderTreeColumn(
    parentPath: String,
    observeColumn: (String) -> kotlinx.coroutines.flow.Flow<List<FolderNode>>,
    isRightmost: Boolean,
    onDrillIn: (FolderNode) -> Unit,
    onDrillOut: () -> Boolean,
    onActivate: (FolderNode) -> Unit,
    onMoveFocusToGrid: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val flow = remember(parentPath) { observeColumn(parentPath) }
    val children by flow.collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    var focusedIndex by rememberSaveable(parentPath) { mutableIntStateOf(0) }

    // Keep focusedIndex in range when the underlying list shrinks (e.g. resync).
    LaunchedEffect(children.size) {
        if (focusedIndex >= children.size) {
            focusedIndex = (children.size - 1).coerceAtLeast(0)
        }
    }

    val firstItemFocus = remember(parentPath) { FocusRequester() }

    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(end = 1.dp),
    ) {
        if (children.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = parentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m },
            ) {
                items(items = children, key = { it.path }) { node ->
                    val index = children.indexOf(node)
                    val isFocused = index == focusedIndex
                    val itemMod =
                        if (isFocused) Modifier.focusRequester(firstItemFocus) else Modifier
                    FolderRow(
                        node = node,
                        isFocused = isFocused,
                        modifier =
                            itemMod
                                .onFocusChanged { state ->
                                    if (state.isFocused) focusedIndex = index
                                }.onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        return@onPreviewKeyEvent false
                                    }
                                    when (event.key) {
                                        Key.DirectionRight -> {
                                            onDrillIn(node)
                                            true
                                        }

                                        Key.DirectionLeft -> {
                                            // If this is not the root column, pop.
                                            // The host handles "back from root".
                                            onDrillOut()
                                        }

                                        Key.DirectionCenter, Key.Enter -> {
                                            onActivate(node)
                                            onMoveFocusToGrid()
                                            true
                                        }

                                        else -> false
                                    }
                                },
                    )
                }
            }

            // Request focus on the first item the first time this column appears
            // — only for the rightmost column, since older columns are background.
            if (isRightmost) {
                LaunchedEffect(parentPath, children.isNotEmpty()) {
                    if (children.isNotEmpty()) {
                        runCatching { firstItemFocus.requestFocus() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    node: FolderNode,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isFocused) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    val textColor =
        if (isFocused) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .focusable()
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
        )
        if (node.recursiveCount > 0) {
            Text(
                text = node.recursiveCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
            )
        }
    }
}
