package com.github.damontecres.stashapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the confirm-press decision for the delete dialog. The invariant under test:
 * this is a *viewer*, so a permanent disk deletion (`delete_file=true`) must never run
 * un-gated. With no gate PIN configured the action is BLOCKED rather than performed —
 * closing the prior bypass where `deleteFiles && gatePin == null` deleted immediately.
 */
class DeleteConfirmActionTest {
    @Test
    fun dbOrGeneratedOnlyDeletePerformsImmediately() {
        // No disk delete requested -> not a destructive disk op, runs regardless of PIN.
        assertEquals(DeleteConfirmAction.PERFORM, deleteConfirmAction(deleteFiles = false, gatePin = null))
        assertEquals(DeleteConfirmAction.PERFORM, deleteConfirmAction(deleteFiles = false, gatePin = "1234"))
    }

    @Test
    fun diskDeleteWithPinRequiresPinGate() {
        assertEquals(DeleteConfirmAction.REQUIRE_PIN, deleteConfirmAction(deleteFiles = true, gatePin = "1234"))
    }

    @Test
    fun diskDeleteWithoutPinIsBlocked() {
        // The regression guard: previously this branch deleted files with no gate at all.
        assertEquals(DeleteConfirmAction.BLOCK_NO_PIN, deleteConfirmAction(deleteFiles = true, gatePin = null))
    }
}
