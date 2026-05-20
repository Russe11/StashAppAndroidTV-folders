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
    fun visibleChildrenForPath_hidesStaleRowsFromPreviousFolder() {
        val oldFolderRows = listOf(folderListRow(path = "/Source/Heavy/", name = "Heavy"))

        assertNull(
            visibleChildrenForPath(
                currentPath = "/Source/Heavy/",
                snapshotPath = "/Source/",
                children = oldFolderRows,
            ),
        )
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
