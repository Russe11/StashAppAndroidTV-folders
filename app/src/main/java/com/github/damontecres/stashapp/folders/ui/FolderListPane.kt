package com.github.damontecres.stashapp.folders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.folders.data.FolderListRow
import com.github.damontecres.stashapp.folders.data.FolderNode
import com.github.damontecres.stashapp.ui.components.CircularProgress

/**
 * Single-level folder browser. Shows the immediate children of [currentPath]
 * as a vertical list, with a `..` row pinned at the top when the user is not
 * already at the root. The host owns navigation: it tracks [focusedRowIndex]
 * (0 = `..` when shown, else first child) and reacts to D-pad input through
 * the [onMoveSelection] / [onActivateRow] / [onGoUp] callbacks.
 *
 * Decoupling navigation from Compose's focus graph this way sidesteps the
 * timing problems that come with LazyColumn + FocusRequester on TV: lazy items
 * don't compose until the layout runs, so a `requestFocus()` in a
 * `LaunchedEffect` races against composition and silently drops focus on the
 * floor. Driving the highlight from pane-level state means the visual is
 * always correct and the activation always fires on the highlighted row.
 *
 * D-pad model (interpreted by the host activity):
 *  - Up/Down: bump [focusedRowIndex] within `[0, rowCount)`.
 *  - Right or DPAD_CENTER / Enter: call [onActivateRow]. The `..` row routes
 *    to [onGoUp]; child rows route to [onEnterFolder].
 *  - Left or hardware Back: [onGoUp].
 */
@Composable
fun FolderListPane(
    currentPath: String,
    children: LazyPagingItems<FolderListRow>,
    focusedRowIndex: Int,
    modifier: Modifier = Modifier,
    onVisibleRowCountChange: (Int) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val showParent = currentPath != ROOT_PATH
    val rowCount = folderPaneRowCount(currentPath, children.itemCount)

    // Keep the focused row in view as the user navigates with the D-pad.
    LaunchedEffect(focusedRowIndex, rowCount) {
        if (focusedRowIndex in 0 until rowCount) {
            runCatching { listState.scrollToItem(focusedRowIndex) }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.size }
            .collect { visibleRows ->
                if (visibleRows > 0) {
                    onVisibleRowCountChange(visibleRows)
                }
            }
    }

    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(end = 1.dp),
    ) {
        BreadcrumbHeader(
            currentPath = currentPath,
            modifier = Modifier.fillMaxWidth(),
        )

        if (children.loadState.refresh is LoadState.Loading && rowCount == 0) {
            CircularProgress()
            return@Column
        }

        if (rowCount == 0) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "(no folders)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxHeight(),
        ) {
            item(key = THIS_FOLDER_ROW_KEY) {
                ThisFolderRow(
                    isSelected = focusedRowIndex == 0,
                )
            }
            if (showParent) {
                item(key = PARENT_ROW_KEY) {
                    ParentRow(isSelected = focusedRowIndex == 1)
                }
            }
            items(
                count = children.itemCount,
                key = children.itemKey { it.node.path },
            ) { i ->
                val rowIndex = 1 + (if (showParent) 1 else 0) + i
                val row = children[i]
                if (row == null) {
                    FolderPlaceholderRow(isSelected = focusedRowIndex == rowIndex)
                } else {
                    FolderRow(
                        row = row,
                        isSelected = focusedRowIndex == rowIndex,
                    )
                }
            }
        }
    }
}

/**
 * Helper for the host: figure out what a row activation at [focusedRowIndex]
 * means. Returns `null` when the index is out of range — defensive guard so
 * the host doesn't have to redo the bookkeeping.
 */
sealed class FolderRowTarget {
    object GoUp : FolderRowTarget()

    data class Enter(val node: FolderNode) : FolderRowTarget()
}

fun folderRowTargetAt(
    showParent: Boolean,
    focusedRowIndex: Int,
    children: List<FolderListRow>?,
): FolderRowTarget? {
    if (children == null) return null
    if (focusedRowIndex < 0) return null
    if (showParent && focusedRowIndex == 0) return FolderRowTarget.GoUp
    val childIdx = focusedRowIndex - (if (showParent) 1 else 0)
    return children.getOrNull(childIdx)?.node?.let(FolderRowTarget::Enter)
}

fun folderRowCount(
    showParent: Boolean,
    children: List<FolderListRow>?,
): Int {
    if (children == null) return 0
    return folderRowCount(showParent, children.size)
}

fun folderRowCount(
    showParent: Boolean,
    childCount: Int,
): Int = (if (showParent) 1 else 0) + childCount.coerceAtLeast(0)

internal fun MutableMap<String, Int>.rememberFolderFocus(
    path: String,
    focusedRowIndex: Int,
) {
    this[path] = focusedRowIndex.coerceAtLeast(0)
}

internal fun Map<String, Int>.restoreFolderFocus(path: String): Int = this[path]?.coerceAtLeast(0) ?: 0

internal fun folderPageJumpIndex(
    currentIndex: Int,
    rowCount: Int,
    visibleRowCount: Int,
    direction: Int,
): Int {
    if (rowCount <= 0) return 0
    val step = visibleRowCount.coerceAtLeast(1)
    val target = currentIndex + (step * direction.coerceIn(-1, 1))
    return target.coerceIn(0, rowCount - 1)
}

@Composable
private fun BreadcrumbHeader(
    currentPath: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = if (currentPath == ROOT_PATH) "/" else currentPath,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            // Truncate from the start so the trailing folder (the one the user
            // is actually browsing) always stays visible on deep paths.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ParentRow(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    UtilityFolderRow(
        label = "..",
        isSelected = isSelected,
        modifier = modifier,
    )
}

@Composable
private fun ThisFolderRow(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    UtilityFolderRow(
        label = stringResource(R.string.folders_this_folder),
        isSelected = isSelected,
        modifier = modifier,
    )
}

@Composable
private fun UtilityFolderRow(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderRow(
    row: FolderListRow,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val node = row.node
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
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
        if (canDrillIntoFolder(row)) {
            Text(
                text = ">",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun FolderPlaceholderRow(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val ROOT_PATH = "/"
private const val THIS_FOLDER_ROW_KEY = "__this_folder__"
private const val PARENT_ROW_KEY = "__parent__"
