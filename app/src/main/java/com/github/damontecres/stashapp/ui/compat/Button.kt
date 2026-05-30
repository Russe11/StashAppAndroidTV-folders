package com.github.damontecres.stashapp.ui.compat

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonGlow
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ButtonShape
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.stashapp.ui.DeviceType
import com.github.damontecres.stashapp.ui.LocalDeviceType

val DefaultButtonColors: androidx.compose.material3.ButtonColors
    @Composable
    get() =
        androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = ButtonDefaults.scale(),
    glow: ButtonGlow = ButtonDefaults.glow(),
    shape: ButtonShape = ButtonDefaults.shape(),
    colors: ButtonColors = ButtonDefaults.colors(),
    border: ButtonBorder = ButtonDefaults.border(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalDeviceType.current == DeviceType.TV) {
        Button(
            onClick = onClick,
            modifier = modifier,
            onLongClick = onLongClick,
            enabled = enabled,
            scale = scale,
            glow = glow,
            shape = shape,
            colors = colors,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        // Force tv.Text to use the right color, then render a properly-styled Material3 button
        // (theme shape + colors) instead of the bare default.
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        ) {
            // tv.ButtonColors' properties are internal, so the tv `colors`/`border` params can't be
            // mapped; use the shared DefaultButtonColors plus the Material3 small shape.
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = androidx.compose.material3.MaterialTheme.shapes.small,
                colors = DefaultButtonColors,
                contentPadding = contentPadding,
                interactionSource = interactionSource,
                content = content,
            )
        }
    }
}
