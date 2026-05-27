package com.github.damontecres.stashapp.folders.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.folders.data.FolderScene
import com.github.damontecres.stashapp.ui.components.CircularProgress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

@Composable
fun FolderVideoListPane(
    folderPath: String?,
    items: LazyPagingItems<FolderScene>,
    focusedVideoIndex: Int,
    sort: FolderVideoSort,
    modifier: Modifier = Modifier,
    onVisibleRowCountChange: (Int) -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusedVideoIndex, items.itemCount) {
        if (focusedVideoIndex in 0 until items.itemCount) {
            runCatching { listState.animateScrollToItem(focusedVideoIndex) }
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
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 1.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = folderPath ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            folderPath.isNullOrBlank() -> {
                EmptyVideoList(text = stringResource(R.string.folders_no_direct_videos))
            }

            items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> {
                CircularProgress()
            }

            items.itemCount == 0 -> {
                EmptyVideoList(text = stringResource(R.string.folders_no_direct_videos))
            }

            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    items(
                        count = items.itemCount,
                        key = items.itemKey { it.sceneId },
                    ) { index ->
                        val scene = items[index]
                        if (scene != null) {
                            FolderVideoRow(
                                scene = scene,
                                isSelected = focusedVideoIndex == index,
                                sort = sort,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyVideoList(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderVideoRow(
    scene: FolderScene,
    isSelected: Boolean,
    sort: FolderVideoSort,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val displayTitle = remember(scene.title, scene.path) { folderVideoDisplayTitle(scene) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderVideoThumbnail(
            scene = scene,
            contentDescription = displayTitle,
            modifier =
                Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = videoMetaLine(scene, sort),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = textColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderVideoThumbnail(
    scene: FolderScene,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val placeholder = painterResource(id = R.drawable.default_scene)
    if (!scene.screenshotUrl.isNullOrBlank()) {
        val request =
            remember(scene.screenshotUrl) {
                ImageRequest.Builder(context)
                    .data(scene.screenshotUrl)
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
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

private fun videoMetaLine(
    scene: FolderScene,
    sort: FolderVideoSort,
): String {
    val runtime = formatDuration(scene.durationSeconds)
    val age = formatEpochMs(scene.updatedAtEpochMs)
    return when (sort) {
        FolderVideoSort.Newest -> "$age - $runtime"
        FolderVideoSort.Longest -> "$runtime - $age"
    }
}

private fun formatDuration(durationSeconds: Double?): String {
    if (durationSeconds == null || durationSeconds <= 0.0) return "--"
    val totalMinutes = (durationSeconds / 60.0).roundToLong().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

private fun formatEpochMs(epochMs: Long): String =
    VIDEO_DATE_FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private val VIDEO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
