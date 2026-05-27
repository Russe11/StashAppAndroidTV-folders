package com.github.damontecres.stashapp.folders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.folders.data.FolderScene
import com.github.damontecres.stashapp.navigation.Destination
import com.github.damontecres.stashapp.navigation.NavigationManagerCompose
import com.github.damontecres.stashapp.playback.PlaybackMode
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.components.ItemOnClicker
import com.github.damontecres.stashapp.ui.pages.SceneDetailsPage
import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.delay

@Composable
fun FolderDetailsPane(
    folderPath: String?,
    selectedScene: FolderScene?,
    server: StashServer,
    navigationManager: NavigationManagerCompose,
    uiConfig: ComposeUiConfig,
    itemOnClick: ItemOnClicker<Any>,
    focusRequestSignal: Int,
    onExitDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewSceneId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedScene?.sceneId) {
        if (selectedScene == null) {
            previewSceneId = null
        } else {
            delay(DETAILS_PREVIEW_DELAY_MS)
            previewSceneId = selectedScene.sceneId
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    event.type == KeyEventType.KeyDown &&
                        (
                            event.key == Key.DirectionUp ||
                                event.key == Key.DirectionDown ||
                                event.key == Key.PageUp ||
                                event.key == Key.PageDown
                        )
                }
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val sceneId = previewSceneId
        if (sceneId == null) {
            EmptyFolderDetails(folderPath = folderPath, modifier = Modifier.fillMaxSize())
        } else {
            SceneDetailsPage(
                server = server,
                navigationManager = navigationManager,
                sceneId = sceneId,
                itemOnClick = itemOnClick,
                playOnClick = { position, mode: PlaybackMode ->
                    navigationManager.navigate(Destination.Playback(sceneId, position, mode))
                },
                uiConfig = uiConfig,
                modifier = Modifier.fillMaxSize(),
                autoFocusHeader = false,
                focusRequestSignal = focusRequestSignal,
                cancelLoadOnDispose = true,
                onFirstButtonLeft = onExitDetails,
                compactPreview = true,
            )
        }
    }
}

@Composable
private fun EmptyFolderDetails(
    folderPath: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.folders_no_direct_videos),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!folderPath.isNullOrBlank()) {
            Text(
                text = folderPath,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private const val DETAILS_PREVIEW_DELAY_MS = 150L
