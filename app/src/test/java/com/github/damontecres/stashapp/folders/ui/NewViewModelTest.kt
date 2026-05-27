package com.github.damontecres.stashapp.folders.ui

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NewViewModelTest {
    @Test
    fun newFeedPagingConfig_loadsSixtyItemsInitially() {
        assertEquals(60, NEW_FEED_PAGING_CONFIG.pageSize)
        assertEquals(60, NEW_FEED_PAGING_CONFIG.initialLoadSize)
        assertEquals(10, NEW_FEED_PAGING_CONFIG.prefetchDistance)
        assertFalse(NEW_FEED_PAGING_CONFIG.enablePlaceholders)
    }

    @Test
    fun clampNewFeedFocus_keepsSelectionInsideLoadedRows() {
        assertEquals(0, clampNewFeedFocus(0, 0))
        assertEquals(0, clampNewFeedFocus(5, 0))
        assertEquals(0, clampNewFeedFocus(-1, 3))
        assertEquals(2, clampNewFeedFocus(9, 3))
        assertEquals(1, clampNewFeedFocus(1, 3))
    }

    @Test
    fun newFeedPageJumpIndex_usesVisibleRowsAndClampsToBounds() {
        assertEquals(0, newFeedPageJumpIndex(currentIndex = 0, itemCount = 0, visibleRowCount = 8, direction = 1))
        assertEquals(8, newFeedPageJumpIndex(currentIndex = 0, itemCount = 30, visibleRowCount = 8, direction = 1))
        assertEquals(0, newFeedPageJumpIndex(currentIndex = 4, itemCount = 30, visibleRowCount = 8, direction = -1))
        assertEquals(29, newFeedPageJumpIndex(currentIndex = 26, itemCount = 30, visibleRowCount = 8, direction = 1))
        assertEquals(11, newFeedPageJumpIndex(currentIndex = 10, itemCount = 30, visibleRowCount = 0, direction = 1))
    }

    @Test
    fun shouldPreviewExitNewInspector_doesNotStealLeftFromDetailsButtons() {
        assertFalse(shouldPreviewExitNewInspector(isInspectorFocused = true, key = Key.DirectionLeft))
        assertEquals(true, shouldPreviewExitNewInspector(isInspectorFocused = true, key = Key.Back))
    }

    @Test
    fun newBrowseHistory_pushesFoldersAndPopsBackToPreviousLocation() {
        val root = NewBrowseHistory()
        val movies = root.enterFolder("/Movies/")
        val sub = movies.enterFolder("/Movies/Sub/")

        assertEquals(null, root.currentFolderPath)
        assertEquals("/Movies/", movies.currentFolderPath)
        assertEquals("/Movies/Sub/", sub.currentFolderPath)
        assertEquals(NewBrowseHistory(listOf("/Movies/")), sub.goBack().history)
        assertEquals(true, sub.goBack().moved)
        assertEquals(NewBrowseHistory(), movies.goBack().history)
        assertEquals(false, root.goBack().moved)
    }

    @Test
    fun retainedNewPageState_preservesFolderPathAndSelectedRowsAcrossPlaybackRecreation() {
        val state =
            NewPageRetainedState()
                .enterFolder("/Movies/")
                .rememberFocus(NEW_GLOBAL_BROWSE_KEY, 12)
                .rememberFocus("/Movies/", 4)

        assertEquals("/Movies/", state.currentFolderPath)
        assertEquals(12, state.restoreFocus(NEW_GLOBAL_BROWSE_KEY))
        assertEquals(4, state.restoreFocus("/Movies/"))
    }
}
