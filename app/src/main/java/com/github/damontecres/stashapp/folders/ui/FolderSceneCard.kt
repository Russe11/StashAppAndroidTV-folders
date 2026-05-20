package com.github.damontecres.stashapp.folders.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.folders.data.FolderScene

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
    modifier: Modifier = Modifier,
) {
    val displayTitle = remember(scene.title, scene.path) { folderSceneDisplayTitle(scene) }

    Card(
        onClick = onClick,
        modifier = modifier,
        scale = CardDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
        ) {
            if (!scene.screenshotUrl.isNullOrBlank()) {
                AsyncImage(
                    model = scene.screenshotUrl,
                    contentDescription = displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.default_scene),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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
