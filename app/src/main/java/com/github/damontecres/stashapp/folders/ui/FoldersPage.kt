package com.github.damontecres.stashapp.folders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.folders.data.FolderScene
import com.github.damontecres.stashapp.navigation.Destination
import com.github.damontecres.stashapp.navigation.NavigationManagerCompose
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.compat.Button
import com.github.damontecres.stashapp.ui.components.ItemOnClicker
import com.github.damontecres.stashapp.ui.components.LongClicker
import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.launch

/**
 * The "Folders" top-level destination — a Stash-library folder-tree browser
 * backed by the local Room cache populated by `LibraryIndexer`.
 *
 * Three regions:
 *  1. Top bar: progress chip while a sync is in flight + a "Force resync" button.
 *  2. Left pane: NeXTSTEP/Finder-style column tree, D-pad navigable.
 *  3. Right pane: paging scene grid for the activated folder.
 *
 * The grid is driven by `FoldersViewModel.scenesFlow`, which keys off the
 * currently-selected folder path; activating a different folder re-keys Paging
 * and the grid refreshes.
 */
@Composable
fun FoldersPage(
    server: StashServer,
    navigationManager: NavigationManagerCompose,
    uiConfig: ComposeUiConfig,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    modifier: Modifier = Modifier,
    onUpdateTitle: ((AnnotatedString) -> Unit)? = null,
    viewModel: FoldersViewModel = viewModel(),
) {
    val title = stringResource(R.string.folders)
    LaunchedEffect(title) {
        onUpdateTitle?.invoke(AnnotatedString(title))
    }

    LaunchedEffect(server.url) {
        viewModel.bindServer(server.url)
    }

    val columnStack by viewModel.columnStack.collectAsState()
    val selectedPath by viewModel.selectedPath.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val pagingItems = viewModel.scenesFlow.collectAsLazyPagingItems()

    val coroutineScope = rememberCoroutineScope()
    val gridFocusRequester = remember { FocusRequester() }
    val treeFocusRequester = remember { FocusRequester() }

    // Scene click → navigate to the Stash item page. We bypass the generic
    // ItemOnClicker because `FolderScene` isn't one of the data types it knows
    // about; the destination's `Destination.Item(DataType.SCENE, id)` is the
    // canonical entrypoint regardless.
    val onSceneClick: (FolderScene) -> Unit = { scene ->
        navigationManager.navigate(Destination.Item(DataType.SCENE, scene.sceneId))
    }

    Column(
        modifier =
            modifier
                .fillMaxSize(),
    ) {
        FoldersTopBar(
            progress = syncProgress,
            onForceResync = {
                coroutineScope.launch {
                    LibraryIndexerBridge.forceResync(server.url)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
        ) {
            // Left pane: column tree. Wider than the scene grid because folder
            // navigation is the primary action; on a 960dp logical-width TV this
            // is the difference between fitting two readable columns and clipping
            // the focused one off-screen.
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(0.45f),
            ) {
                FolderTreeColumns(
                    columnStack = columnStack,
                    observeColumn = viewModel::observeColumn,
                    onDrillIn = viewModel::pushColumn,
                    onDrillOut = { viewModel.popColumn() },
                    onActivate = viewModel::selectFolder,
                    onMoveFocusToGrid = {
                        runCatching { gridFocusRequester.requestFocus() }
                    },
                    treeFocusRequester = treeFocusRequester,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Right pane: paging scene grid.
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(0.55f),
            ) {
                FolderSceneGrid(
                    items = pagingItems,
                    selectedPath = selectedPath,
                    onSceneClick = onSceneClick,
                    focusRequester = gridFocusRequester,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun FoldersTopBar(
    progress: SyncProgressUiState,
    onForceResync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (val p = progress) {
                is SyncProgressUiState.Running -> {
                    Text(
                        text = stringResource(R.string.folders_indexing, p.done, p.total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is SyncProgressUiState.Failed -> {
                    Text(
                        text = p.message ?: stringResource(R.string.folders_indexing, 0, 0),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.errorContainer,
                    )
                }

                SyncProgressUiState.Completed, SyncProgressUiState.Idle -> {
                    // Hidden when nothing interesting is happening.
                }
            }
        }
        Box(modifier = Modifier.width(8.dp))
        Button(onClick = onForceResync) {
            Text(text = stringResource(R.string.folders_force_resync))
        }
    }
}
