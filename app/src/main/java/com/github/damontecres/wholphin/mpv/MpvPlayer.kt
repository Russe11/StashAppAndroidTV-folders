package com.github.damontecres.wholphin.mpv

import android.content.Context
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Compile-only stub of the upstream `MpvPlayer` for builds that don't bundle the
 * `wholphin-mpv` AAR (no local `libs/wholphin-mpv-release.aar`, no
 * `WholphinExtensionsUsername` maven creds).
 *
 * Build wiring (`app/build.gradle.kts` source-set conditional) only adds this
 * file when both of those are absent. If the user later drops in the real AAR
 * or sets the creds, gradle drops this source set automatically and the real
 * `MpvPlayer` takes over.
 *
 * Two production references this satisfies:
 *  - Constructor `MpvPlayer(context, hardwareDecoding, gpuNext)` in
 *    `StashExoPlayer.createPlayer()`. The init block throws — anyone who picks
 *    the MPV backend on a stub-built APK gets a clean error instead of a
 *    confusing native-load crash.
 *  - `is MpvPlayer` smart-cast inside the `Player.isReleased` extension. The
 *    class extending [ForwardingPlayer] keeps that branch type-correct; at
 *    runtime nothing will ever satisfy `is MpvPlayer` because we never
 *    successfully construct one.
 *
 * The Folders destination does not need MPV — ExoPlayer is the default backend
 * and handles standard formats. This stub is purely so the apk assembles.
 */
@UnstableApi
class MpvPlayer(
    context: Context,
    @Suppress("UNUSED_PARAMETER") hardwareDecoding: Boolean,
    @Suppress("UNUSED_PARAMETER") gpuNext: Boolean,
) : ForwardingPlayer(ExoPlayer.Builder(context).build()) {
    init {
        throw UnsupportedOperationException(
            "MPV playback is not bundled in this APK build. " +
                "Switch the playback backend to ExoPlayer in settings, " +
                "or rebuild with the wholphin-mpv module available.",
        )
    }

    @Suppress("unused")
    val isReleased: Boolean get() = false
}
