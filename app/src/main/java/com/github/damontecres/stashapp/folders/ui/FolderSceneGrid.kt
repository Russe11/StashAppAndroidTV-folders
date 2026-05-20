package com.github.damontecres.stashapp.folders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.paging.compose.itemKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.folders.data.FolderScene
import androidx.compose.ui.focus.FocusRequester
import com.github.damontecres.stashapp.ui.ComposeUiConfig

/**
 * Right-pane scene grid. Backed by a Paging-Compose [LazyPagingItems] over the
 * Room cache so users can scroll past the first batch without forcing a network
 * round-trip per page.
 *
 * Renders an empty-state placeholder when no folder is selected — this happens on
 * cold start before the user activates a leaf in the column tree.
 */
@Composable
fun FolderSceneGrid(
    items: LazyPagingItems<FolderScene>,
    selectedPath: String?,
    onSceneClick: (FolderScene) -> Unit,
    uiConfig: ComposeUiConfig,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val pagingItems = items
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selectedPath.isNullOrBlank() -> {
                Text(
                    text = stringResource(R.string.folders_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            pagingItems.itemCount == 0 -> {
                // The folder exists but has no scenes (either truly empty or the
                // indexer hasn't reached it yet). Distinguish from "no folder
                // selected" so users understand why the grid is empty.
                Text(
                    text = selectedPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m },
                ) {
                    // Stable item identity via `key` lets the lazy grid reuse
                    // existing slots when paging appends new items, instead of
                    // throwing away the whole grid and rebuilding it. Stable
                    // `contentType` lets it pool composable instances — every
                    // card is the same shape, so one pool serves all rows.
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { it.sceneId },
                        contentType = { "scene_card" },
                    ) { index ->
                        val scene = pagingItems[index]
                        if (scene != null) {
                            FolderSceneCard(
                                scene = scene,
                                onClick = { onSceneClick(scene) },
                                uiConfig = uiConfig,
                            )
                        }
                    }
                }
            }
        }
    }
}
