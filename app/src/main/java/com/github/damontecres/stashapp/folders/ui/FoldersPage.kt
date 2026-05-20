package com.github.damontecres.stashapp.folders.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
 * The "Folders" top-level destination — a Stash-library folder browser backed
 * by the local Room cache populated by `LibraryIndexer`.
 *
 * Three regions:
 *  1. Top bar: progress chip while a sync is in flight + a "Force resync" button.
 *  2. Left pane: single-level subfolder list for the current folder, with a `..`
 *     row pinned at the top when not at root. See [FolderListPane].
 *  3. Right pane: paging scene grid for the current folder (recursive).
 *
 * Navigation is host-driven: this composable owns a `focusedRowIndex` and
 * dispatches D-pad input from a root-level `onPreviewKeyEvent` handler. Doing
 * it that way avoids the LazyColumn-on-TV focus-timing problems we ran into
 * during the prototype — see the comment block in [FolderListPane] for the
 * full story.
 *
 * Hardware Back: handled here by [BackHandler], which calls
 * [FoldersViewModel.goUp]. When already at the root the handler is disabled so
 * the host nav controller can pop the destination as usual.
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
    onOpenNavigationDrawer: () -> Unit = {},
    navigationDrawerOpen: Boolean = false,
    viewModel: FoldersViewModel = viewModel(),
) {
    val title = stringResource(R.string.folders)
    LaunchedEffect(title) {
        onUpdateTitle?.invoke(AnnotatedString(title))
    }

    LaunchedEffect(server.url) {
        viewModel.bindServer(server.url)
    }

    val currentPath by viewModel.currentPath.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val pagingItems = viewModel.scenesFlow.collectAsLazyPagingItems()
    val childItems = viewModel.childrenFlow.collectAsLazyPagingItems()

    val coroutineScope = rememberCoroutineScope()
    val showParent = currentPath != FoldersViewModel.ROOT_PARENT
    val rowCount = folderRowCount(showParent, childItems.itemCount)

    val focusByPath = remember { mutableMapOf<String, Int>() }
    var focusedRowIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(currentPath) {
        focusedRowIndex = focusByPath.restoreFolderFocus(currentPath)
    }
    LaunchedEffect(currentPath, focusedRowIndex) {
        focusByPath.rememberFolderFocus(currentPath, focusedRowIndex)
    }
    LaunchedEffect(rowCount) {
        if (focusedRowIndex >= rowCount) {
            focusedRowIndex = (rowCount - 1).coerceAtLeast(0)
        }
    }
    var visibleFolderRows by rememberSaveable { mutableIntStateOf(8) }

    // Which pane currently owns input focus. Left = the host-driven subfolder
    // list (see FolderListPane); Right = the scene grid (Compose-focus-managed
    // via TV Card composables). DPAD_RIGHT from the left pane transfers to
    // Right; hardware Back / DPAD_LEFT from the right pane returns to Left.
    var paneFocus by remember { mutableStateOf(PaneFocus.Left) }

    // Hardware Back is a shell/menu action for Folders. Left handles spatial
    // navigation inside the page: videos -> folders -> parent/root menu.
    BackHandler(enabled = !navigationDrawerOpen) {
        onOpenNavigationDrawer()
    }

    val onSceneClick: (FolderScene) -> Unit = { scene ->
        navigationManager.navigate(Destination.Item(DataType.SCENE, scene.sceneId))
    }

    val rootFocus = remember { FocusRequester() }
    val sceneGridFocus = remember { FocusRequester() }
    // Re-claim focus on the root whenever we transition back to the Left pane
    // (e.g. after Back from the scene grid). Without this the focus stays on
    // wherever the grid put it, and our root onPreviewKeyEvent never fires
    // because the focused node is in a different subtree.
    LaunchedEffect(paneFocus) {
        if (paneFocus == PaneFocus.Left) {
            runCatching { rootFocus.requestFocus() }
        }
    }

    val goUpOrOpenDrawer = {
        if (!viewModel.goUp()) {
            onOpenNavigationDrawer()
        }
    }

    val activateFolderRow = {
        when {
            showParent && focusedRowIndex == 0 -> goUpOrOpenDrawer()
            else -> {
                val childIndex = focusedRowIndex - (if (showParent) 1 else 0)
                if (childIndex >= 0) {
                    childItems[childIndex]?.let { row ->
                        viewModel.enterFolder(row.node)
                    }
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    // In the Right pane, let Compose's TV-Card focus handle
                    // DPad navigation inside the scene grid; only intercept
                    // Left as the "back to folders" escape hatch. Everything
                    // else (Up/Down/Center/Enter) falls through to the grid.
                    if (paneFocus == PaneFocus.Right) {
                        return@onPreviewKeyEvent when (event.key) {
                            Key.DirectionLeft -> {
                                paneFocus = PaneFocus.Left
                                true
                            }
                            else -> false
                        }
                    }
                    if (event.isPageUpKey()) {
                        focusedRowIndex =
                            folderPageJumpIndex(
                                currentIndex = focusedRowIndex,
                                rowCount = rowCount,
                                visibleRowCount = visibleFolderRows,
                                direction = -1,
                            )
                        return@onPreviewKeyEvent true
                    }
                    if (event.isPageDownKey()) {
                        focusedRowIndex =
                            folderPageJumpIndex(
                                currentIndex = focusedRowIndex,
                                rowCount = rowCount,
                                visibleRowCount = visibleFolderRows,
                                direction = 1,
                            )
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (focusedRowIndex > 0) focusedRowIndex--
                            true
                        }

                        Key.DirectionDown -> {
                            if (focusedRowIndex < rowCount - 1) focusedRowIndex++
                            true
                        }

                        Key.DirectionLeft -> {
                            goUpOrOpenDrawer()
                            true
                        }

                        Key.DirectionRight -> {
                            // Transfer focus to the scene grid — only if it
                            // has something focusable. When the folder is
                            // empty the grid renders a placeholder Text and
                            // requestFocus would throw IllegalStateException.
                            if (pagingItems.itemCount > 0) {
                                paneFocus = PaneFocus.Right
                                runCatching { sceneGridFocus.requestFocus() }
                            }
                            true
                        }

                        Key.DirectionCenter, Key.Enter -> {
                            activateFolderRow()
                            true
                        }

                        else -> false
                    }
                },
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
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(0.30f),
            ) {
                FolderListPane(
                    currentPath = currentPath,
                    children = childItems,
                    focusedRowIndex = focusedRowIndex,
                    onVisibleRowCountChange = { visibleFolderRows = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(0.70f),
            ) {
                FolderSceneGrid(
                    items = pagingItems,
                    selectedPath = currentPath,
                    onSceneClick = onSceneClick,
                    uiConfig = uiConfig,
                    focusRequester = sceneGridFocus,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Which of the two panes currently owns input focus.
 *
 * The Left pane (subfolder list) uses host-driven navigation via
 * [androidx.compose.ui.input.key.onPreviewKeyEvent] on a focusable root, so
 * DPad input is processed in this composable. The Right pane (scene grid)
 * delegates to Compose's native focus on TV Cards, so the root keyhandler
 * passes most events through. Tracking this state explicitly lets us route
 * Back / DPAD_LEFT correctly depending on which pane is active.
 */
private enum class PaneFocus { Left, Right }

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

private fun androidx.compose.ui.input.key.KeyEvent.isPageUpKey(): Boolean =
    nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_PAGE_UP ||
        nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CHANNEL_UP

private fun androidx.compose.ui.input.key.KeyEvent.isPageDownKey(): Boolean =
    nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_PAGE_DOWN ||
        nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CHANNEL_DOWN
