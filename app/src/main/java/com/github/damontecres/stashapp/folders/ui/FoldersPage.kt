package com.github.damontecres.stashapp.folders.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.folders.data.FolderScene
import com.github.damontecres.stashapp.navigation.NavigationManagerCompose
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.compat.Button
import com.github.damontecres.stashapp.ui.components.ItemOnClicker
import com.github.damontecres.stashapp.ui.components.LongClicker
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.preferences
import com.github.damontecres.stashapp.util.updateInterfacePreferences
import kotlinx.coroutines.launch

/**
 * TV-only three-pane Folders browser:
 *  1. folder rows (`This folder`, optional `..`, then child folders),
 *  2. one-column direct video list for the highlighted folder row,
 *  3. embedded scene details preview for the highlighted video.
 *
 * Folder and video panes remain host-driven so D-pad movement cannot leak back
 * into the app drawer. Compose focus is only handed to the details pane when
 * the user explicitly presses Right/Enter from a selected video.
 */
@Composable
fun FoldersPage(
    server: StashServer,
    navigationManager: NavigationManagerCompose,
    uiConfig: ComposeUiConfig,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    modifier: Modifier = Modifier,
    initialPath: String? = null,
    onUpdateTitle: ((AnnotatedString) -> Unit)? = null,
    onOpenNavigationDrawer: () -> Unit = {},
    navigationDrawerOpen: Boolean = false,
    viewModel: FoldersViewModel = viewModel(),
) {
    val title = stringResource(R.string.folders)
    LaunchedEffect(title) {
        onUpdateTitle?.invoke(AnnotatedString(title))
    }

    LaunchedEffect(server.url, initialPath) {
        viewModel.bindServer(server.url, initialPath)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentPath by viewModel.currentPath.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val childItems = viewModel.childrenFlow.collectAsLazyPagingItems()
    val videoItems = viewModel.videoScenesFlow.collectAsLazyPagingItems()
    val childRows = childItems.itemSnapshotList.items

    var videoSort by rememberSaveable {
        mutableStateOf(uiConfig.preferences.interfacePreferences.folderVideoSort.toFolderVideoSort())
    }
    LaunchedEffect(uiConfig.preferences.interfacePreferences.folderVideoSort) {
        videoSort = uiConfig.preferences.interfacePreferences.folderVideoSort.toFolderVideoSort()
    }
    LaunchedEffect(videoSort) {
        viewModel.setVideoSort(videoSort)
    }

    val rowCount = folderPaneRowCount(currentPath, childItems.itemCount)
    var focusedFolderRowIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedVideoIndex by rememberSaveable { mutableIntStateOf(0) }
    var visibleFolderRows by rememberSaveable { mutableIntStateOf(8) }
    var visibleVideoRows by rememberSaveable { mutableIntStateOf(6) }
    var paneFocus by remember { mutableStateOf(viewModel.restoreActivePane().toPaneFocus()) }
    var paneBeforeSort by remember { mutableStateOf(FoldersPaneFocus.Folder) }
    var detailsFocusRequest by remember { mutableIntStateOf(0) }

    fun setPaneFocus(focus: FoldersPaneFocus) {
        paneFocus = focus
        focus.toRetainedPane()?.let(viewModel::rememberActivePane)
    }

    LaunchedEffect(currentPath) {
        focusedFolderRowIndex = viewModel.restoreFolderFocus(currentPath)
    }
    LaunchedEffect(currentPath, focusedFolderRowIndex) {
        viewModel.rememberFolderFocus(currentPath, focusedFolderRowIndex)
    }
    LaunchedEffect(rowCount) {
        if (focusedFolderRowIndex >= rowCount) {
            focusedFolderRowIndex = (rowCount - 1).coerceAtLeast(0)
        }
    }

    val folderTarget = folderPaneTargetAt(currentPath, focusedFolderRowIndex, childRows)
    val folderTargetPath = folderTarget.pathOrNull()
    LaunchedEffect(folderTargetPath) {
        if (folderTargetPath != null) {
            viewModel.setVideoPanePath(folderTargetPath)
            focusedVideoIndex = viewModel.restoreVideoFocus(folderTargetPath)
        }
    }
    LaunchedEffect(videoItems.itemCount) {
        focusedVideoIndex = clampVideoFocus(focusedVideoIndex, videoItems.itemCount)
    }
    LaunchedEffect(folderTargetPath, focusedVideoIndex) {
        if (folderTargetPath != null) {
            viewModel.rememberVideoFocus(folderTargetPath, focusedVideoIndex)
        }
    }

    val selectedVideo = videoItems.itemAtOrNull(focusedVideoIndex)
    val rootFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    var rootFocusReclaimSignal by remember { mutableIntStateOf(0) }
    var allowingNavigationDrawerFocusTransfer by remember { mutableStateOf(false) }

    LaunchedEffect(paneFocus, rootFocusReclaimSignal) {
        when (paneFocus) {
            FoldersPaneFocus.Folder,
            FoldersPaneFocus.Video,
            -> runCatching { rootFocus.requestFocus() }

            FoldersPaneFocus.Sort -> runCatching { sortFocus.requestFocus() }
            FoldersPaneFocus.Details -> Unit
        }
    }
    LaunchedEffect(navigationDrawerOpen) {
        if (!navigationDrawerOpen) {
            allowingNavigationDrawerFocusTransfer = false
        }
    }

    fun openNavigationDrawerFromFolders() {
        allowingNavigationDrawerFocusTransfer = true
        onOpenNavigationDrawer()
    }

    BackHandler(enabled = !navigationDrawerOpen) {
        openNavigationDrawerFromFolders()
    }

    val goUpOrOpenDrawer = {
        if (!viewModel.goUp()) {
            openNavigationDrawerFromFolders()
        }
    }

    val activateFolderRow = {
        when (val target = folderTarget) {
            is FolderPaneTarget.ChildFolder -> {
                if (canDrillIntoFolder(target.row)) {
                    viewModel.enterFolder(target.row.node)
                }
            }
            is FolderPaneTarget.ParentFolder -> goUpOrOpenDrawer()
            is FolderPaneTarget.ThisFolder,
            null,
            -> Unit
        }
    }

    val setVideoSort: (FolderVideoSort) -> Unit = { sort ->
        videoSort = sort
        coroutineScope.launch {
            context.preferences.updateData { prefs ->
                prefs.updateInterfacePreferences {
                    folderVideoSort = sort.toPreference()
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .onFocusChanged { focusState ->
                    if (
                        shouldReclaimFoldersRootFocus(
                            paneFocus = paneFocus,
                            hasFocus = focusState.hasFocus,
                            navigationDrawerOpen = navigationDrawerOpen,
                            allowingNavigationDrawerFocusTransfer = allowingNavigationDrawerFocusTransfer,
                        )
                    ) {
                        rootFocusReclaimSignal++
                    }
                }
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    if (paneFocus == FoldersPaneFocus.Details) {
                        return@onPreviewKeyEvent false
                    }
                    if (paneFocus == FoldersPaneFocus.Sort) {
                        return@onPreviewKeyEvent false
                    }
                    if (event.isPageUpKey()) {
                        when (paneFocus) {
                            FoldersPaneFocus.Folder -> {
                                focusedFolderRowIndex =
                                    folderPageJumpIndex(
                                        currentIndex = focusedFolderRowIndex,
                                        rowCount = rowCount,
                                        visibleRowCount = visibleFolderRows,
                                        direction = -1,
                                    )
                                true
                            }

                            FoldersPaneFocus.Video -> {
                                focusedVideoIndex =
                                    folderPageJumpIndex(
                                        currentIndex = focusedVideoIndex,
                                        rowCount = videoItems.itemCount,
                                        visibleRowCount = visibleVideoRows,
                                        direction = -1,
                                    )
                                true
                            }

                            else -> false
                        }.let { return@onPreviewKeyEvent it }
                    }
                    if (event.isPageDownKey()) {
                        when (paneFocus) {
                            FoldersPaneFocus.Folder -> {
                                focusedFolderRowIndex =
                                    folderPageJumpIndex(
                                        currentIndex = focusedFolderRowIndex,
                                        rowCount = rowCount,
                                        visibleRowCount = visibleFolderRows,
                                        direction = 1,
                                    )
                                true
                            }

                            FoldersPaneFocus.Video -> {
                                focusedVideoIndex =
                                    folderPageJumpIndex(
                                        currentIndex = focusedVideoIndex,
                                        rowCount = videoItems.itemCount,
                                        visibleRowCount = visibleVideoRows,
                                        direction = 1,
                                    )
                                true
                            }

                            else -> false
                        }.let { return@onPreviewKeyEvent it }
                    }
                    when (paneFocus) {
                        FoldersPaneFocus.Folder ->
                            handleFolderPaneKey(
                                key = event.key,
                                rowCount = rowCount,
                                focusedFolderRowIndex = focusedFolderRowIndex,
                                setFocusedFolderRowIndex = { focusedFolderRowIndex = it },
                                enterSort = {
                                    paneBeforeSort = FoldersPaneFocus.Folder
                                    setPaneFocus(FoldersPaneFocus.Sort)
                                },
                                goLeft = goUpOrOpenDrawer,
                                goRight = {
                                    if (videoItems.itemCount > 0) {
                                        setPaneFocus(FoldersPaneFocus.Video)
                                    }
                                },
                                activate = activateFolderRow,
                            )

                        FoldersPaneFocus.Video ->
                            handleVideoPaneKey(
                                key = event.key,
                                itemCount = videoItems.itemCount,
                                focusedVideoIndex = focusedVideoIndex,
                                setFocusedVideoIndex = { focusedVideoIndex = it },
                                enterSort = {
                                    paneBeforeSort = FoldersPaneFocus.Video
                                    setPaneFocus(FoldersPaneFocus.Sort)
                                },
                                goLeft = { setPaneFocus(FoldersPaneFocus.Folder) },
                                enterDetails = {
                                    if (selectedVideo != null) {
                                        setPaneFocus(FoldersPaneFocus.Details)
                                        detailsFocusRequest++
                                    }
                                },
                            )

                        FoldersPaneFocus.Sort,
                        FoldersPaneFocus.Details,
                        -> false
                    }
                },
    ) {
        FoldersTopBar(
            progress = syncProgress,
            sort = videoSort,
            sortFocusRequester = sortFocus,
            onSortChange = setVideoSort,
            onSortExitDown = { setPaneFocus(paneBeforeSort) },
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
                        .weight(0.18f)
                        .foldersPaneBorder(paneFocus == FoldersPaneFocus.Folder),
            ) {
                FolderListPane(
                    currentPath = currentPath,
                    children = childItems,
                    focusedRowIndex = focusedFolderRowIndex,
                    onVisibleRowCountChange = { visibleFolderRows = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(0.32f)
                        .foldersPaneBorder(paneFocus == FoldersPaneFocus.Video),
            ) {
                FolderVideoListPane(
                    folderPath = folderTargetPath,
                    items = videoItems,
                    focusedVideoIndex = focusedVideoIndex,
                    sort = videoSort,
                    onVisibleRowCountChange = { visibleVideoRows = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(0.50f)
                        .foldersPaneBorder(paneFocus == FoldersPaneFocus.Details),
            ) {
                FolderDetailsPane(
                    folderPath = folderTargetPath,
                    selectedScene = selectedVideo,
                    server = server,
                    navigationManager = navigationManager,
                    uiConfig = uiConfig,
                    itemOnClick = itemOnClick,
                    focusRequestSignal =
                        if (paneFocus == FoldersPaneFocus.Details) {
                            detailsFocusRequest
                        } else {
                            0
                        },
                    onExitDetails = { setPaneFocus(FoldersPaneFocus.Video) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

internal enum class FoldersPaneFocus { Folder, Video, Details, Sort }

internal fun FoldersPaneFocus.ownsHostFocus(): Boolean =
    this == FoldersPaneFocus.Folder || this == FoldersPaneFocus.Video

internal fun shouldReclaimFoldersRootFocus(
    paneFocus: FoldersPaneFocus,
    hasFocus: Boolean,
    navigationDrawerOpen: Boolean,
    allowingNavigationDrawerFocusTransfer: Boolean,
): Boolean =
    paneFocus.ownsHostFocus() &&
        !hasFocus &&
        !navigationDrawerOpen &&
        !allowingNavigationDrawerFocusTransfer

private fun FoldersRetainedPane.toPaneFocus(): FoldersPaneFocus =
    when (this) {
        FoldersRetainedPane.Folder -> FoldersPaneFocus.Folder
        FoldersRetainedPane.Video -> FoldersPaneFocus.Video
    }

private fun FoldersPaneFocus.toRetainedPane(): FoldersRetainedPane? =
    when (this) {
        FoldersPaneFocus.Folder -> FoldersRetainedPane.Folder
        FoldersPaneFocus.Video -> FoldersRetainedPane.Video
        FoldersPaneFocus.Details,
        FoldersPaneFocus.Sort,
        -> null
    }

@Composable
private fun FoldersTopBar(
    progress: SyncProgressUiState,
    sort: FolderVideoSort,
    sortFocusRequester: FocusRequester,
    onSortChange: (FolderVideoSort) -> Unit,
    onSortExitDown: () -> Unit,
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

                SyncProgressUiState.Completed,
                SyncProgressUiState.Idle,
                -> Unit
            }
        }
        FolderSortControl(
            sort = sort,
            focusRequester = sortFocusRequester,
            onSortChange = onSortChange,
            onExitDown = onSortExitDown,
        )
        Box(modifier = Modifier.width(12.dp))
        Button(onClick = onForceResync) {
            Text(text = stringResource(R.string.folders_force_resync))
        }
    }
}

@Composable
private fun FolderSortControl(
    sort: FolderVideoSort,
    focusRequester: FocusRequester,
    onSortChange: (FolderVideoSort) -> Unit,
    onExitDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .focusRequester(focusRequester)
                .focusable()
                .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSortChange(FolderVideoSort.Newest)
                            true
                        }

                        Key.DirectionRight -> {
                            onSortChange(FolderVideoSort.Longest)
                            true
                        }

                        Key.DirectionCenter,
                        Key.Enter,
                        -> {
                            onSortChange(
                                if (sort == FolderVideoSort.Newest) {
                                    FolderVideoSort.Longest
                                } else {
                                    FolderVideoSort.Newest
                                },
                            )
                            true
                        }

                        Key.DirectionDown -> {
                            onExitDown()
                            true
                        }

                        Key.DirectionUp -> true
                        else -> false
                    }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortChip(
            label = stringResource(R.string.folders_sort_newest),
            selected = sort == FolderVideoSort.Newest,
        )
        SortChip(
            label = stringResource(R.string.folders_sort_longest),
            selected = sort == FolderVideoSort.Longest,
        )
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            modifier
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = MaterialTheme.shapes.extraSmall,
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun handleFolderPaneKey(
    key: Key,
    rowCount: Int,
    focusedFolderRowIndex: Int,
    setFocusedFolderRowIndex: (Int) -> Unit,
    enterSort: () -> Unit,
    goLeft: () -> Unit,
    goRight: () -> Unit,
    activate: () -> Unit,
): Boolean =
    when (key) {
        Key.DirectionUp -> {
            if (focusedFolderRowIndex > 0) {
                setFocusedFolderRowIndex(focusedFolderRowIndex - 1)
            } else {
                enterSort()
            }
            true
        }

        Key.DirectionDown -> {
            if (focusedFolderRowIndex < rowCount - 1) {
                setFocusedFolderRowIndex(focusedFolderRowIndex + 1)
            }
            true
        }

        Key.DirectionLeft -> {
            goLeft()
            true
        }

        Key.DirectionRight -> {
            goRight()
            true
        }

        Key.DirectionCenter,
        Key.Enter,
        -> {
            activate()
            true
        }

        else -> false
    }

private fun handleVideoPaneKey(
    key: Key,
    itemCount: Int,
    focusedVideoIndex: Int,
    setFocusedVideoIndex: (Int) -> Unit,
    enterSort: () -> Unit,
    goLeft: () -> Unit,
    enterDetails: () -> Unit,
): Boolean =
    when (key) {
        Key.DirectionUp -> {
            if (focusedVideoIndex > 0) {
                setFocusedVideoIndex(focusedVideoIndex - 1)
            } else {
                enterSort()
            }
            true
        }

        Key.DirectionDown -> {
            if (focusedVideoIndex < itemCount - 1) {
                setFocusedVideoIndex(focusedVideoIndex + 1)
            }
            true
        }

        Key.DirectionLeft -> {
            goLeft()
            true
        }

        Key.DirectionRight,
        Key.DirectionCenter,
        Key.Enter,
        -> {
            enterDetails()
            true
        }

        else -> false
    }

@Composable
private fun Modifier.foldersPaneBorder(isActive: Boolean): Modifier =
    border(
        width = if (isActive) 2.dp else 1.dp,
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    )

private fun FolderPaneTarget?.pathOrNull(): String? =
    when (this) {
        is FolderPaneTarget.ThisFolder -> path
        is FolderPaneTarget.ParentFolder -> path
        is FolderPaneTarget.ChildFolder -> row.node.path
        null -> null
    }

private fun LazyPagingItems<FolderScene>.itemAtOrNull(index: Int): FolderScene? =
    if (index in 0 until itemCount) this[index] else null

private fun androidx.compose.ui.input.key.KeyEvent.isPageUpKey(): Boolean =
    nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_PAGE_UP ||
        nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CHANNEL_UP

private fun androidx.compose.ui.input.key.KeyEvent.isPageDownKey(): Boolean =
    nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_PAGE_DOWN ||
        nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CHANNEL_DOWN
