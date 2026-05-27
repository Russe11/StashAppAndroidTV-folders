package com.github.damontecres.stashapp.folders.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.folders.data.NewItemRow
import com.github.damontecres.stashapp.navigation.Destination
import com.github.damontecres.stashapp.navigation.NavigationManagerCompose
import com.github.damontecres.stashapp.playback.PlaybackMode
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.DeviceType
import com.github.damontecres.stashapp.ui.LocalDeviceType
import com.github.damontecres.stashapp.ui.compat.Button
import com.github.damontecres.stashapp.ui.compat.ListItem
import com.github.damontecres.stashapp.ui.components.CircularProgress
import com.github.damontecres.stashapp.ui.components.ItemOnClicker
import com.github.damontecres.stashapp.ui.pages.SceneDetailsPage
import com.github.damontecres.stashapp.ui.tryRequestFocus
import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NewPage(
    server: StashServer,
    navigationManager: NavigationManagerCompose,
    uiConfig: ComposeUiConfig,
    itemOnClick: ItemOnClicker<Any>,
    modifier: Modifier = Modifier,
    onUpdateTitle: ((AnnotatedString) -> Unit)? = null,
    viewModel: NewViewModel = viewModel(),
) {
    val title = stringResource(R.string.new_items)
    LaunchedEffect(title) {
        onUpdateTitle?.invoke(AnnotatedString(title))
    }
    LaunchedEffect(server.url) {
        viewModel.bindServer(server.url)
    }

    val items = viewModel.itemsFlow.collectAsLazyPagingItems()
    val currentFolderPath by viewModel.currentFolderPath.collectAsState()
    val browseKey = currentFolderPath ?: NEW_GLOBAL_BROWSE_KEY
    var focusedRowIndex by rememberSaveable(server.url) { mutableIntStateOf(viewModel.restoreFocus(browseKey)) }
    var paneFocus by remember { mutableStateOf(NewPaneFocus.List) }
    var visibleRows by rememberSaveable { mutableIntStateOf(8) }
    var inspectorFocusRequest by rememberSaveable { mutableIntStateOf(0) }
    var inspectorItem by remember { mutableStateOf<NewItemRow?>(null) }

    fun updateFocusedRowIndex(index: Int) {
        focusedRowIndex = clampNewFeedFocus(index, items.itemCount)
        viewModel.rememberFocus(browseKey, focusedRowIndex)
    }

    LaunchedEffect(browseKey) {
        paneFocus = NewPaneFocus.List
        focusedRowIndex = viewModel.restoreFocus(browseKey)
        inspectorItem = null
    }

    LaunchedEffect(items.itemCount) {
        updateFocusedRowIndex(focusedRowIndex)
    }

    val selectedItem = items.getOrNull(focusedRowIndex)
    LaunchedEffect(selectedItem?.itemType, selectedItem?.itemId) {
        delay(150)
        inspectorItem = selectedItem
    }
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(paneFocus) {
        if (paneFocus == NewPaneFocus.List) {
            rootFocus.tryRequestFocus()
        }
    }

    val enterInspector: () -> Unit = {
        if (selectedItem?.isScene == true) {
            inspectorItem = selectedItem
            paneFocus = NewPaneFocus.Inspector
            inspectorFocusRequest++
        }
    }

    val enterFolder: (NewItemRow) -> Unit = { item ->
        viewModel.rememberFocus(browseKey, focusedRowIndex)
        inspectorItem = null
        viewModel.enterFolder(item.path)
    }

    val goBackInFolder: () -> Boolean = {
        viewModel.rememberFocus(browseKey, focusedRowIndex)
        viewModel.goBackInFolder()
    }

    val activateSelected: () -> Unit = {
        selectedItem?.let { item ->
            if (item.isFolder) {
                enterFolder(item)
            } else {
                enterInspector()
            }
        }
    }

    BackHandler(enabled = paneFocus == NewPaneFocus.Inspector || currentFolderPath != null) {
        if (paneFocus == NewPaneFocus.Inspector) {
            paneFocus = NewPaneFocus.List
        } else {
            goBackInFolder()
        }
    }

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    if (shouldPreviewExitNewInspector(paneFocus == NewPaneFocus.Inspector, event.key)) {
                        paneFocus = NewPaneFocus.List
                        return@onPreviewKeyEvent true
                    }
                    if (paneFocus == NewPaneFocus.Inspector) {
                        return@onPreviewKeyEvent false
                    }
                    if (event.isPageUpKey()) {
                        focusedRowIndex =
                            newFeedPageJumpIndex(
                                currentIndex = focusedRowIndex,
                                itemCount = items.itemCount,
                                visibleRowCount = visibleRows,
                                direction = -1,
                            )
                        return@onPreviewKeyEvent true
                    }
                    if (event.isPageDownKey()) {
                        focusedRowIndex =
                            newFeedPageJumpIndex(
                                currentIndex = focusedRowIndex,
                                itemCount = items.itemCount,
                                visibleRowCount = visibleRows,
                                direction = 1,
                            )
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.DirectionUp -> {
                            updateFocusedRowIndex(focusedRowIndex - 1)
                            true
                        }

                        Key.DirectionDown -> {
                            updateFocusedRowIndex(focusedRowIndex + 1)
                            true
                        }

                        Key.DirectionRight -> {
                            enterInspector()
                            selectedItem?.isScene == true
                        }

                        Key.DirectionCenter, Key.Enter -> {
                            activateSelected()
                            selectedItem != null
                        }

                        else -> false
                    }
                },
    ) {
        NewFeedListPane(
            items = items,
            focusedRowIndex = focusedRowIndex,
            uiConfig = uiConfig,
            previewsActive = paneFocus == NewPaneFocus.List,
            onFocusedRowChange = { updateFocusedRowIndex(it) },
            onActivateRow = activateSelected,
            onVisibleRowCountChange = { visibleRows = it },
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(0.42f),
        )

        NewInspectorPane(
            item = inspectorItem,
            server = server,
            navigationManager = navigationManager,
            uiConfig = uiConfig,
            itemOnClick = itemOnClick,
            focusRequestSignal = inspectorFocusRequest,
            onExitInspector = { paneFocus = NewPaneFocus.List },
            onOpenFolder = enterFolder,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(0.58f),
        )
    }
}

@Composable
private fun NewFeedListPane(
    items: LazyPagingItems<NewItemRow>,
    focusedRowIndex: Int,
    uiConfig: ComposeUiConfig,
    previewsActive: Boolean,
    onFocusedRowChange: (Int) -> Unit,
    onActivateRow: () -> Unit,
    onVisibleRowCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusedRowIndex, items.itemCount) {
        if (focusedRowIndex in 0 until items.itemCount) {
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

    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        when {
            items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> {
                CircularProgress()
            }

            items.itemCount == 0 -> {
                Text(
                    text = stringResource(R.string.new_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = items.itemCount,
                        key = items.itemKey { "${it.itemType}:${it.itemId}" },
                    ) { index ->
                        val item = items[index]
                        if (item != null) {
                            NewListItem(
                                item = item,
                                selected = focusedRowIndex == index,
                                uiConfig = uiConfig,
                                previewSelected = previewsActive && focusedRowIndex == index,
                                onClick = {
                                    onFocusedRowChange(index)
                                    onActivateRow()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewInspectorPane(
    item: NewItemRow?,
    server: StashServer,
    navigationManager: NavigationManagerCompose,
    uiConfig: ComposeUiConfig,
    itemOnClick: ItemOnClicker<Any>,
    focusRequestSignal: Int,
    onExitInspector: () -> Unit,
    onOpenFolder: (NewItemRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            item == null -> {
                Text(
                    text = stringResource(R.string.new_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            item.isFolder -> {
                NewFolderInspector(
                    item = item,
                    onOpenFolder = { onOpenFolder(item) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                SceneDetailsPage(
                    server = server,
                    navigationManager = navigationManager,
                    sceneId = item.itemId,
                    itemOnClick = itemOnClick,
                    playOnClick = { position, mode: PlaybackMode ->
                        navigationManager.navigate(Destination.Playback(item.itemId, position, mode))
                    },
                    uiConfig = uiConfig,
                    modifier = Modifier.fillMaxSize(),
                    autoFocusHeader = false,
                    focusRequestSignal = focusRequestSignal,
                    cancelLoadOnDispose = true,
                    onFirstButtonLeft = onExitInspector,
                    compactPreview = true,
                )
            }
        }
    }
}

@Composable
private fun NewFolderInspector(
    item: NewItemRow,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suppressChildFocus = LocalDeviceType.current == DeviceType.TV
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .padding(24.dp),
    ) {
        NewItemThumbnail(
            item = item,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
        )
        Text(
            text = item.displayTitle(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.path,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                if (item.directCount > 0) {
                    "${item.directCount} direct videos - newest ${formatEpochMs(item.updatedAtEpochMs)}"
                } else {
                    "${item.directCount} direct videos"
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onOpenFolder,
            modifier =
                if (suppressChildFocus) {
                    Modifier.focusProperties { canFocus = false }
                } else {
                    Modifier
                },
        ) {
            Text(text = stringResource(R.string.folders))
        }
    }
}

@Composable
private fun NewListItem(
    item: NewItemRow,
    selected: Boolean,
    uiConfig: ComposeUiConfig,
    previewSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suppressChildFocus = LocalDeviceType.current == DeviceType.TV
    ListItem(
        selected = selected,
        onClick = onClick,
        overlineContent = {
            Text(
                text = item.typeLabel() + " • " + formatEpochMs(item.updatedAtEpochMs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        headlineContent = {
            Text(
                text = item.displayTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = item.path,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            SceneThumbnailPreview(
                thumbnailUrl = item.thumbnailUrl,
                previewUrl = item.previewUrl,
                contentDescription = item.displayTitle(),
                selected = previewSelected && item.isScene,
                uiConfig = uiConfig,
                modifier =
                    Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f),
            )
        },
        trailingContent = {
            if (item.isFolder) {
                Text(
                    text = item.directCount.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier =
            modifier.then(
                if (suppressChildFocus) {
                    Modifier.focusProperties { canFocus = false }
                } else {
                    Modifier
                },
            ),
        dense = true,
    )
}

@Composable
private fun NewItemThumbnail(
    item: NewItemRow,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val placeholder = painterResource(id = R.drawable.default_scene)
    if (!item.thumbnailUrl.isNullOrBlank()) {
        val request =
            remember(item.thumbnailUrl) {
                ImageRequest.Builder(context)
                    .data(item.thumbnailUrl)
                    .crossfade(false)
                    .precision(Precision.INEXACT)
                    .build()
            }
        AsyncImage(
            model = request,
            contentDescription = item.displayTitle(),
            modifier = modifier,
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
        )
    } else {
        Image(
            painter = placeholder,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

private enum class NewPaneFocus { List, Inspector }

@Composable
private fun NewItemRow.typeLabel(): String =
    stringResource(
        if (isFolder) {
            R.string.new_folder_type
        } else {
            R.string.new_video_type
        },
    )

private fun NewItemRow.displayTitle(): String {
    val trimmedTitle = title?.trim().orEmpty()
    if (trimmedTitle.isNotEmpty()) return trimmedTitle
    if (isFolder) return path.trim('/').substringAfterLast('/').ifEmpty { "/" }
    return path.substringAfterLast('/').substringBeforeLast('.').trim().ifEmpty { "Untitled" }
}

private fun LazyPagingItems<NewItemRow>.getOrNull(index: Int): NewItemRow? =
    if (index in 0 until itemCount) {
        this[index]
    } else {
        null
    }

internal fun newFeedPageJumpIndex(
    currentIndex: Int,
    itemCount: Int,
    visibleRowCount: Int,
    direction: Int,
): Int {
    if (itemCount <= 0) return 0
    val step = visibleRowCount.coerceAtLeast(1)
    val target = currentIndex + (step * direction.coerceIn(-1, 1))
    return target.coerceIn(0, itemCount - 1)
}

internal fun shouldPreviewExitNewInspector(
    isInspectorFocused: Boolean,
    key: Key,
): Boolean = isInspectorFocused && key == Key.Back

private fun androidx.compose.ui.input.key.KeyEvent.isPageUpKey(): Boolean =
    nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_PAGE_UP ||
        nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CHANNEL_UP

private fun androidx.compose.ui.input.key.KeyEvent.isPageDownKey(): Boolean =
    nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_PAGE_DOWN ||
        nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CHANNEL_DOWN

private fun formatEpochMs(epochMs: Long): String =
    DATE_FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
