package com.github.damontecres.stashapp.folders.ui

import com.github.damontecres.stashapp.folders.data.FolderListRow
import com.github.damontecres.stashapp.folders.data.FolderScene
import com.github.damontecres.stashapp.proto.FolderVideoSortPreference

sealed class FolderPaneTarget {
    data class ThisFolder(val path: String) : FolderPaneTarget()

    data class ParentFolder(val path: String) : FolderPaneTarget()

    data class ChildFolder(val row: FolderListRow) : FolderPaneTarget()
}

enum class FolderVideoSort {
    Newest,
    Longest,
}

internal enum class FoldersRetainedPane {
    Folder,
    Video,
}

internal data class FoldersPageRetainedState(
    val currentPath: String = FoldersViewModel.ROOT_PARENT,
    val activePane: FoldersRetainedPane = FoldersRetainedPane.Folder,
    val folderFocusByPath: Map<String, Int> = emptyMap(),
    val videoFocusByPath: Map<String, Int> = emptyMap(),
) {
    fun withCurrentPath(path: String): FoldersPageRetainedState =
        copy(currentPath = path.ifBlank { FoldersViewModel.ROOT_PARENT })

    fun rememberFolderFocus(
        path: String,
        focusedRowIndex: Int,
    ): FoldersPageRetainedState =
        copy(folderFocusByPath = folderFocusByPath + (path to focusedRowIndex.coerceAtLeast(0)))

    fun restoreFolderFocus(path: String): Int = folderFocusByPath[path]?.coerceAtLeast(0) ?: 0

    fun rememberVideoFocus(
        path: String,
        focusedRowIndex: Int,
    ): FoldersPageRetainedState =
        copy(videoFocusByPath = videoFocusByPath + (path to focusedRowIndex.coerceAtLeast(0)))

    fun restoreVideoFocus(path: String): Int = videoFocusByPath[path]?.coerceAtLeast(0) ?: 0

    fun rememberActivePane(pane: FoldersRetainedPane): FoldersPageRetainedState = copy(activePane = pane)
}

internal fun FolderVideoSortPreference.toFolderVideoSort(): FolderVideoSort =
    when (this) {
        FolderVideoSortPreference.FOLDER_VIDEO_SORT_LONGEST -> FolderVideoSort.Longest
        FolderVideoSortPreference.FOLDER_VIDEO_SORT_NEWEST,
        FolderVideoSortPreference.UNRECOGNIZED,
        -> FolderVideoSort.Newest
    }

internal fun FolderVideoSort.toPreference(): FolderVideoSortPreference =
    when (this) {
        FolderVideoSort.Newest -> FolderVideoSortPreference.FOLDER_VIDEO_SORT_NEWEST
        FolderVideoSort.Longest -> FolderVideoSortPreference.FOLDER_VIDEO_SORT_LONGEST
    }

fun folderPaneTargetAt(
    currentPath: String,
    focusedRowIndex: Int,
    children: List<FolderListRow>?,
): FolderPaneTarget? {
    if (focusedRowIndex < 0) return null
    if (focusedRowIndex == 0) return FolderPaneTarget.ThisFolder(currentPath)

    val showParent = currentPath != FoldersViewModel.ROOT_PARENT
    if (showParent && focusedRowIndex == 1) {
        return FolderPaneTarget.ParentFolder(FoldersViewModel.parentOf(currentPath))
    }

    val childIndex = focusedRowIndex - 1 - if (showParent) 1 else 0
    return children?.getOrNull(childIndex)?.let(FolderPaneTarget::ChildFolder)
}

fun folderPaneRowCount(
    currentPath: String,
    childCount: Int,
): Int = 1 + (if (currentPath == FoldersViewModel.ROOT_PARENT) 0 else 1) + childCount.coerceAtLeast(0)

internal fun canDrillIntoFolder(row: FolderListRow): Boolean = row.childFolderCount > 0

fun sortFolderScenes(
    scenes: List<FolderScene>,
    sort: FolderVideoSort,
): List<FolderScene> =
    when (sort) {
        FolderVideoSort.Newest ->
            scenes.sortedWith(
                compareByDescending<FolderScene> { it.updatedAtEpochMs }
                    .thenBy { it.path.lowercase() },
            )

        FolderVideoSort.Longest ->
            scenes.sortedWith { left, right ->
                val leftDuration = left.durationSeconds
                val rightDuration = right.durationSeconds
                when {
                    leftDuration != null && rightDuration == null -> -1
                    leftDuration == null && rightDuration != null -> 1
                    leftDuration != null && rightDuration != null && leftDuration != rightDuration ->
                        rightDuration.compareTo(leftDuration)

                    left.updatedAtEpochMs != right.updatedAtEpochMs ->
                        right.updatedAtEpochMs.compareTo(left.updatedAtEpochMs)

                    else -> left.path.lowercase().compareTo(right.path.lowercase())
                }
            }
    }

internal fun MutableMap<String, Int>.rememberVideoFocus(
    path: String,
    focusedRowIndex: Int,
) {
    this[path] = focusedRowIndex.coerceAtLeast(0)
}

internal fun Map<String, Int>.restoreVideoFocus(path: String): Int = this[path]?.coerceAtLeast(0) ?: 0

internal fun clampVideoFocus(
    index: Int,
    itemCount: Int,
): Int {
    if (itemCount <= 0) return 0
    return index.coerceIn(0, itemCount - 1)
}

internal fun folderVideoDisplayTitle(scene: FolderScene): String {
    val title = scene.title?.trim().orEmpty()
    if (title.isNotEmpty()) return title
    val basename = scene.path.substringAfterLast('/').substringBeforeLast('.').trim()
    return basename.ifEmpty { "Untitled" }
}
