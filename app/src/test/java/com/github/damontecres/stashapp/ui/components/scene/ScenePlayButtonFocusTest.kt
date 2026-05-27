package com.github.damontecres.stashapp.ui.components.scene

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePlayButtonFocusTest {
    @Test
    fun shouldExitDetailsFromFirstButton_onlyHandlesLeftOnFirstButton() {
        assertTrue(shouldExitDetailsFromFirstButton(isFirstButtonFocused = true, key = Key.DirectionLeft))
        assertFalse(shouldExitDetailsFromFirstButton(isFirstButtonFocused = false, key = Key.DirectionLeft))
        assertFalse(shouldExitDetailsFromFirstButton(isFirstButtonFocused = true, key = Key.DirectionRight))
    }
}
