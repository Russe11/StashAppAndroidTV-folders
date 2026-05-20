package com.github.damontecres.stashapp.folders.ui

import com.github.damontecres.stashapp.folders.data.FolderListRow
import com.github.damontecres.stashapp.folders.data.FolderNode
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

    private fun folderListRow(
        path: String = "/People/",
        name: String = "People",
        thumbnailUrl: String? = null,
    ): FolderListRow =
        FolderListRow(
            node =
                FolderNode(
                    serverUrl = "https://stash.example.test",
                    path = path,
                    name = name,
                    parentPath = "/",
                    recursiveCount = 1,
                    directCount = 0,
                ),
            thumbnailUrl = thumbnailUrl,
        )
}
