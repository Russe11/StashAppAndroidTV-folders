package com.github.damontecres.stashapp.ui.compat

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.LocalContentColor
import com.github.damontecres.stashapp.ui.DeviceType
import com.github.damontecres.stashapp.ui.LocalDeviceType

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: CardShape = CardDefaults.shape(),
    colors: CardColors = CardDefaults.colors(),
    scale: CardScale = CardDefaults.scale(),
    border: CardBorder = CardDefaults.border(),
    glow: CardGlow = CardDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    if (LocalDeviceType.current == DeviceType.TV) {
        androidx.tv.material3.Card(
            onClick = onClick,
            modifier = modifier,
            onLongClick = onLongClick,
            shape = shape,
            colors = colors,
            scale = scale,
            border = border,
            glow = glow,
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        // Force tv.Text to use the right color, then render a properly-styled Material3 card
        // (rounded shape + tonal/elevated surface) instead of dropping the styling params.
        CompositionLocalProvider(
            LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        ) {
            androidx.compose.material3.ElevatedCard(
                shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                modifier =
                    modifier
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = onClick,
                            onLongClick = onLongClick?.let { original ->
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    original()
                                }
                            },
                        ),
                content = content,
            )
        }
    }
}
