package com.github.damontecres.stashapp.util

/**
 * Pure, headless-testable decision for the screenshot / app-switcher privacy feature.
 *
 * When enabled, [com.github.damontecres.stashapp.RootActivity] sets
 * [android.view.WindowManager.LayoutParams.FLAG_SECURE] on its window. That single flag:
 *  - blocks screenshots (the system shows "Can't take screenshot ..."),
 *  - blocks screen recording and the recorded frames are black,
 *  - blocks casting / screen-mirroring capture of the window, and
 *  - blanks the recents / app-switcher thumbnail (a placeholder is shown instead).
 *
 * This is **privacy-first ON by default** for an adult-media viewer: a fresh install (no stored
 * preference) is secured. The user can opt out via the "Block screenshots & hide in recents"
 * toggle, which persists an explicit boolean in the existing `PinPreferences` proto DataStore
 * (`block_screenshots`) mirrored to SharedPreferences (`pref_key_block_screenshots`), exactly like
 * the other privacy settings.
 *
 * The flag is applied at [com.github.damontecres.stashapp.RootActivity.onCreate]. Because
 * `FLAG_SECURE` affects the window at creation time, toggling the preference takes effect on the
 * next launch of the activity; the activity does not re-apply it live mid-session.
 */
object ScreenshotPrivacy {
    /**
     * Whether the window should carry `FLAG_SECURE`.
     *
     * @param pref the stored preference, or `null` when the user has never set it. `null` (no
     * explicit choice) is treated as the privacy-first default of `true`, so a fresh install is
     * secured until the user explicitly opts out.
     */
    fun shouldSecureWindow(pref: Boolean?): Boolean = pref ?: DEFAULT_ENABLED

    /** Privacy-first default: secure the window unless the user has explicitly opted out. */
    const val DEFAULT_ENABLED: Boolean = true
}
