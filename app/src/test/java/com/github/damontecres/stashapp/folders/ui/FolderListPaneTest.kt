package com.github.damontecres.stashapp.folders.ui

import com.github.damontecres.stashapp.folders.data.FolderListRow
import com.github.damontecres.stashapp.folders.data.FolderNode
import com.github.damontecres.stashapp.folders.data.FolderScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderListPaneTest {
    @Test
    fun folderRowTargetAt_entersFolderFromListRowAfterParentRow() {
        val child = folderListRow(path = "/People/Ada/", name = "Ada", thumbnailUrl = "https://example.test/ada.jpg")

        val target = folderRowTargetAt(showParent = true, focusedRowIndex = 1, children = listOf(child))

        assertEquals(FolderRowTarget.Enter(child.node), target)
    }

    @Test
    fun folderRowTargetAt_returnsNullWhenListRowIndexIsOutOfRange() {
        assertNull(folderRowTargetAt(showParent = false, focusedRowIndex = 1, children = listOf(folderListRow())))
    }

    @Test
    fun folderRowTargetAt_returnsNullWhileChildrenAreLoading() {
        assertNull(folderRowTargetAt(showParent = true, focusedRowIndex = 0, children = null))
    }

    @Test
    fun folderRowCount_returnsZeroWhileChildrenAreLoading() {
        assertEquals(0, folderRowCount(showParent = true, children = null))
    }

    @Test
    fun showFolderThumbnailsForParentPath_suppressesTopTwoFolderLevels() {
        assertEquals(false, showFolderThumbnailsForParentPath("/"))
        assertEquals(false, showFolderThumbnailsForParentPath("/Studios/"))
        assertEquals(true, showFolderThumbnailsForParentPath("/Studios/Network/"))
    }

    @Test
    fun rememberedFocusForPath_restoresParentFolderRowAfterReturningFromChild() {
        val focusByPath = mutableMapOf<String, Int>()

        focusByPath.rememberFolderFocus("/", 23)
        focusByPath.rememberFolderFocus("/Studios/", 4)

        assertEquals(23, focusByPath.restoreFolderFocus("/"))
        assertEquals(4, focusByPath.restoreFolderFocus("/Studios/"))
        assertEquals(0, focusByPath.restoreFolderFocus("/Missing/"))
    }

    @Test
    fun folderPageJumpIndex_movesByVisibleRowCountAndClampsToBounds() {
        assertEquals(17, folderPageJumpIndex(currentIndex = 5, rowCount = 100, visibleRowCount = 12, direction = 1))
        assertEquals(0, folderPageJumpIndex(currentIndex = 5, rowCount = 100, visibleRowCount = 12, direction = -1))
        assertEquals(99, folderPageJumpIndex(currentIndex = 95, rowCount = 100, visibleRowCount = 12, direction = 1))
    }

    @Test
    fun folderPaneRowTargetAt_includesThisFolderAndParentBeforeChildren() {
        val child = folderListRow(path = "/People/Ada/", name = "Ada", childFolderCount = 1)
        val children = listOf(child)

        assertEquals(FolderPaneTarget.ThisFolder("/People/"), folderPaneTargetAt("/People/", 0, children))
        assertEquals(FolderPaneTarget.ParentFolder("/"), folderPaneTargetAt("/People/", 1, children))
        assertEquals(FolderPaneTarget.ChildFolder(child), folderPaneTargetAt("/People/", 2, children))
        assertNull(folderPaneTargetAt("/People/", 3, children))
    }

    @Test
    fun canDrillIntoFolder_requiresChildFolders() {
        assertEquals(false, canDrillIntoFolder(folderListRow(childFolderCount = 0, directCount = 5, recursiveCount = 5)))
        assertEquals(true, canDrillIntoFolder(folderListRow(childFolderCount = 1, directCount = 0, recursiveCount = 5)))
    }

    @Test
    fun folderPaneRowCount_alwaysIncludesThisFolderAndOptionalParent() {
        assertEquals(1, folderPaneRowCount(currentPath = "/", childCount = 0))
        assertEquals(3, folderPaneRowCount(currentPath = "/", childCount = 2))
        assertEquals(2, folderPaneRowCount(currentPath = "/People/", childCount = 0))
        assertEquals(4, folderPaneRowCount(currentPath = "/People/", childCount = 2))
    }

    @Test
    fun sortFolderScenes_ordersByNewestOrLongest() {
        val oldLong = folderScene(sceneId = "1", path = "/Movies/old-long.mp4", durationSeconds = 3600.0, updatedAtEpochMs = 10)
        val newShort = folderScene(sceneId = "2", path = "/Movies/new-short.mp4", durationSeconds = 90.0, updatedAtEpochMs = 30)
        val noDuration = folderScene(sceneId = "3", path = "/Movies/no-duration.mp4", durationSeconds = null, updatedAtEpochMs = 20)

        assertEquals(listOf("2", "3", "1"), sortFolderScenes(listOf(oldLong, newShort, noDuration), FolderVideoSort.Newest).map { it.sceneId })
        assertEquals(listOf("1", "2", "3"), sortFolderScenes(listOf(oldLong, newShort, noDuration), FolderVideoSort.Longest).map { it.sceneId })
    }

    @Test
    fun rememberedVideoFocusForPath_restoresAndClamps() {
        val focusByPath = mutableMapOf<String, Int>()

        focusByPath.rememberVideoFocus("/Movies/", 8)

        assertEquals(8, focusByPath.restoreVideoFocus("/Movies/"))
        assertEquals(0, focusByPath.restoreVideoFocus("/Missing/"))
        assertEquals(2, clampVideoFocus(index = 8, itemCount = 3))
        assertEquals(0, clampVideoFocus(index = -1, itemCount = 3))
        assertEquals(0, clampVideoFocus(index = 8, itemCount = 0))
    }

    @Test
    fun folderVideoDisplayTitle_prefersTitleThenBasenameWithoutExtension() {
        assertEquals("A title", folderVideoDisplayTitle(folderScene(title = " A title ", path = "/Movies/file-name.mp4")))
        assertEquals("file-name", folderVideoDisplayTitle(folderScene(title = "", path = "/Movies/file-name.mp4")))
        assertEquals("Untitled", folderVideoDisplayTitle(folderScene(title = "", path = "/")))
    }

    @Test
    fun retainedFoldersPageState_preservesCurrentPathAndSelectedVideoAcrossPlaybackRecreation() {
        val state =
            FoldersPageRetainedState()
                .withCurrentPath("/Movies/")
                .rememberFolderFocus("/Movies/", 3)
                .rememberVideoFocus("/Movies/Action/", 7)
                .rememberActivePane(FoldersRetainedPane.Video)

        assertEquals("/Movies/", state.currentPath)
        assertEquals(3, state.restoreFolderFocus("/Movies/"))
        assertEquals(7, state.restoreVideoFocus("/Movies/Action/"))
        assertEquals(FoldersRetainedPane.Video, state.activePane)
    }

    @Test
    fun foldersRootFocusReclaim_allowsIntentionalNavigationDrawerHandoff() {
        assertEquals(
            true,
            shouldReclaimFoldersRootFocus(
                paneFocus = FoldersPaneFocus.Video,
                hasFocus = false,
                navigationDrawerOpen = false,
                allowingNavigationDrawerFocusTransfer = false,
            ),
        )
        assertEquals(
            false,
            shouldReclaimFoldersRootFocus(
                paneFocus = FoldersPaneFocus.Folder,
                hasFocus = false,
                navigationDrawerOpen = false,
                allowingNavigationDrawerFocusTransfer = true,
            ),
        )
        assertEquals(
            false,
            shouldReclaimFoldersRootFocus(
                paneFocus = FoldersPaneFocus.Folder,
                hasFocus = false,
                navigationDrawerOpen = true,
                allowingNavigationDrawerFocusTransfer = false,
            ),
        )
        assertEquals(
            false,
            shouldReclaimFoldersRootFocus(
                paneFocus = FoldersPaneFocus.Details,
                hasFocus = false,
                navigationDrawerOpen = false,
                allowingNavigationDrawerFocusTransfer = false,
            ),
        )
        assertEquals(
            false,
            shouldReclaimFoldersRootFocus(
                paneFocus = FoldersPaneFocus.Video,
                hasFocus = true,
                navigationDrawerOpen = false,
                allowingNavigationDrawerFocusTransfer = false,
            ),
        )
    }

    private fun folderListRow(
        path: String = "/People/",
        name: String = "People",
        thumbnailUrl: String? = null,
        recursiveCount: Int = 1,
        directCount: Int = 0,
        childFolderCount: Int = 0,
    ): FolderListRow =
        FolderListRow(
            node =
                FolderNode(
                    serverUrl = "https://stash.example.test",
                    path = path,
                    name = name,
                    parentPath = "/",
                    recursiveCount = recursiveCount,
                    directCount = directCount,
                    thumbnailUrl = thumbnailUrl,
                ),
            childFolderCount = childFolderCount,
        )

    private fun folderScene(
        sceneId: String = "1",
        path: String = "/People/video.mp4",
        title: String? = null,
        durationSeconds: Double? = 10.0,
        updatedAtEpochMs: Long = 100,
    ): FolderScene =
        FolderScene(
            serverUrl = "https://stash.example.test",
            sceneId = sceneId,
            path = path,
            parentPath = path.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { "/" } + "/",
            title = title,
            durationSeconds = durationSeconds,
            rating100 = null,
            organized = false,
            screenshotUrl = null,
            previewUrl = null,
            updatedAtEpochMs = updatedAtEpochMs,
        )
}
