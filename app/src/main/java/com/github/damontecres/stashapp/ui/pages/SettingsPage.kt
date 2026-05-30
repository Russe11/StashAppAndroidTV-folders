package com.github.damontecres.stashapp.ui.pages

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.stashapp.PreferenceScreenOption
import com.github.damontecres.stashapp.navigation.NavigationManager
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.compat.isTvDevice
import com.github.damontecres.stashapp.ui.compat.isNotTvDevice
import com.github.damontecres.stashapp.ui.components.prefs.PreferencesContent
import com.github.damontecres.stashapp.ui.util.ScreenSize
import com.github.damontecres.stashapp.ui.util.screenSize
import com.github.damontecres.stashapp.util.StashServer

@SuppressLint("RestrictedApi")
@Composable
fun SettingsPage(
    server: StashServer,
    navigationManager: NavigationManager,
    preferenceScreenOption: PreferenceScreenOption,
    uiConfig: ComposeUiConfig,
    modifier: Modifier = Modifier,
    onUpdateTitle: ((AnnotatedString) -> Unit)? = null,
) {
    val size = screenSize()
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        val newModifier =
            when {
                isTvDevice -> {
                    // TV: right-aligned 40%-width column — keep exactly as before.
                    Modifier
                        .fillMaxWidth(.4f)
                        .align(Alignment.TopEnd)
                }
                isNotTvDevice && (size == ScreenSize.MEDIUM || size == ScreenSize.EXPANDED) -> {
                    // Touch large screens: constrain to a comfortable reading measure, centered.
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                }
                else -> {
                    // Touch COMPACT: full-width.
                    Modifier
                }
            }
        PreferencesContent(
            server,
            navigationManager,
            uiConfig,
            preferenceScreenOption,
            newModifier,
            onUpdateTitle,
        )
    }
}
