package com.github.damontecres.stashapp.folders.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.folders.data.FolderScene
import com.github.damontecres.stashapp.playback.maybeMuteAudio
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.LocalGlobalContext
import com.github.damontecres.stashapp.ui.LocalPlayerContext
import com.github.damontecres.stashapp.util.isNotNullOrBlank
import kotlinx.coroutines.delay

/**
 * Renders a [FolderScene] directly from the local cache without round-tripping
 * back to the Stash API. Falls back to the file's basename when `title` is blank
 * so unscraped libraries stay navigable (mirrors `sceneDisplayTitle` in the
 * Subfinder web client).
 *
 * The screenshot URL is unauthenticated for some Stash deployments and presigned
 * for others; we let Coil handle either case transparently.
 */
@Composable
fun FolderSceneCard(
    scene: FolderScene,
    onClick: () -> Unit,
    uiConfig: ComposeUiConfig,
    modifier: Modifier = Modifier,
) {
    val displayTitle = remember(scene.title, scene.path) { folderSceneDisplayTitle(scene) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsState().value
    var focusedAfterDelay by remember { mutableStateOf(false) }

    if (focused) {
        LaunchedEffect(scene.sceneId) {
            delay(uiConfig.preferences.interfacePreferences.cardPreviewDelayMs)
            focusedAfterDelay = true
        }
    } else {
        focusedAfterDelay = false
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        scale = CardDefaults.scale(focusedScale = 1.05f),
        interactionSource = interactionSource,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
        ) {
            FolderSceneStillImage(
                scene = scene,
                contentDescription = displayTitle,
                modifier = Modifier.fillMaxSize(),
            )
            if (
                focusedAfterDelay &&
                uiConfig.preferences.interfacePreferences.playVideoPreviews &&
                scene.previewUrl.isNotNullOrBlank()
            ) {
                FolderScenePreviewVideo(
                    previewUrl = scene.previewUrl,
                    uiConfig = uiConfig,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderSceneStillImage(
    scene: FolderScene,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (!scene.screenshotUrl.isNullOrBlank()) {
        // Build an explicit ImageRequest so we can opt into INEXACT precision:
        // Coil scales the bitmap to the card size instead of decoding the full
        // Stash screenshot.
        val request =
            remember(scene.screenshotUrl) {
                ImageRequest.Builder(context)
                    .data(scene.screenshotUrl)
                    .crossfade(true)
                    .precision(Precision.INEXACT)
                    .build()
            }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.default_scene),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun FolderScenePreviewVideo(
    previewUrl: String,
    uiConfig: ComposeUiConfig,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player =
        LocalPlayerContext.current.player(
            context,
            LocalGlobalContext.current.server,
        )
    LaunchedEffect(player, previewUrl) {
        maybeMuteAudio(uiConfig.preferences, true, player)
        val mediaItem =
            MediaItem
                .Builder()
                .setUri(previewUrl.toUri())
                .setMimeType(MimeTypes.VIDEO_MP4)
                .build()
        player.setMediaItem(mediaItem, C.TIME_UNSET)
        player.playWhenReady = true
        player.prepare()
    }
    LifecycleStartEffect(player) {
        onStopOrDispose {
            player.stop()
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.stop()
        }
    }
    val contentScale = ContentScale.Crop
    val presentationState = rememberPresentationState(player)
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier =
            modifier.resizeWithContentScale(
                contentScale = contentScale,
                sourceSizeDp = presentationState.videoSizeDp,
            ),
    )
}

/**
 * Returns `scene.title` if non-blank, else the file's basename with the
 * extension stripped, else "Untitled".
 */
internal fun folderSceneDisplayTitle(scene: FolderScene): String {
    val title = scene.title?.trim().orEmpty()
    if (title.isNotEmpty()) return title
    val basename = scene.path.substringAfterLast('/').substringBeforeLast('.').trim()
    return basename.ifEmpty { "Untitled" }
}
