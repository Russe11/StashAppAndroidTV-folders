package com.github.damontecres.stashapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic color constants for recurring accent concepts used across the app.
 *
 * All hex values are preserved exactly from their original inline sites so that
 * consolidating them here produces zero visible change. Use these symbols instead
 * of raw Color(0x…) literals so that a future theming pass has a single place to
 * update.
 */
object SemanticColors {
    /** Filled / active star in the rating bar (gold). */
    val RatingStarFilled = Color(0xFFFFC700)

    /** Empty / background star in the rating bar (semi-transparent gold). */
    val RatingStarEmpty = Color(0x2AFFC700)

    /** Tint applied to the "organized" check-circle icon when a scene is organised. */
    val Organized = Color(0xFF66BB6A)

    /** Tint applied to a star icon when the matching rating ladder entry is selected (gold). */
    val StarSelected = Color(0xFFFFC700)

    /** Job status tint for RUNNING and FINISHED states. */
    val JobRunning = Color(0xFF006800)

    /** Job status tint for FINISHED state (same value as JobRunning). */
    val JobFinished = Color(0xFF006800)
}
