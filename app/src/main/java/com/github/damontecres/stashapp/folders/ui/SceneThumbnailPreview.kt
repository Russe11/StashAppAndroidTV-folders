package com.github.damontecres.stashapp.folders.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.playback.maybeMuteAudio
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.LocalGlobalContext
import com.github.damontecres.stashapp.ui.LocalPlayerContext
import com.github.damontecres.stashapp.util.isNotNullOrBlank
import kotlinx.coroutines.delay

@Composable
internal fun SceneThumbnailPreview(
    thumbnailUrl: String?,
    previewUrl: String?,
    contentDescription: String,
    selected: Boolean,
    uiConfig: ComposeUiConfig,
    modifier: Modifier = Modifier,
) {
    var selectedAfterDelay by remember { mutableStateOf(false) }
    val playVideoPreviews = uiConfig.preferences.interfacePreferences.playVideoPreviews
    LaunchedEffect(selected, previewUrl, playVideoPreviews) {
        selectedAfterDelay = false
        if (selected && playVideoPreviews && previewUrl.isNotNullOrBlank()) {
            delay(uiConfig.preferences.interfacePreferences.cardPreviewDelayMs)
            selectedAfterDelay = true
        }
    }

    Box(modifier = modifier) {
        SceneThumbnailStillImage(
            thumbnailUrl = thumbnailUrl,
            contentDescription = contentDescription,
            modifier = Modifier.matchParentSize(),
        )
        if (
            shouldPlaySceneThumbnailPreview(
                selectedAfterDelay = selectedAfterDelay,
                playVideoPreviews = playVideoPreviews,
                previewUrl = previewUrl,
            )
        ) {
            SceneThumbnailPreviewVideo(
                previewUrl = previewUrl!!,
                uiConfig = uiConfig,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
internal fun BoxScope.SceneThumbnailStillImage(
    thumbnailUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val placeholder = painterResource(id = R.drawable.default_scene)
    if (!thumbnailUrl.isNullOrBlank()) {
        val request =
            remember(thumbnailUrl) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .crossfade(false)
                    .precision(Precision.INEXACT)
                    .build()
            }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
        )
    } else {
        Image(
            painter = placeholder,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SceneThumbnailPreviewVideo(
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
    val presentationState = rememberPresentationState(player)
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier =
            modifier.resizeWithContentScale(
                contentScale = ContentScale.Crop,
                sourceSizeDp = presentationState.videoSizeDp,
            ),
    )
}

internal fun shouldPlaySceneThumbnailPreview(
    selectedAfterDelay: Boolean,
    playVideoPreviews: Boolean,
    previewUrl: String?,
): Boolean = selectedAfterDelay && playVideoPreviews && previewUrl.isNotNullOrBlank()
