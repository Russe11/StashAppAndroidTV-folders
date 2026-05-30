package com.github.damontecres.stashapp.ui.components.devicebus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.ui.compat.Button
import com.github.damontecres.stashapp.util.realtime.DeviceCommandType
import com.github.damontecres.stashapp.util.realtime.OnlineDeviceRow

/**
 * The phone/TV-as-remote transport panel for a chosen target device (R-C CONTROLLER role): play /
 * pause / seek ±10s / previous / next. Sends each press as a `deviceCommand` to the target via the
 * [CrossDeviceViewModel]; the target obeys it (after its own TOFU confirm). Scene IDs only — this
 * surface carries no titles.
 *
 * Shown only for a target that advertises the PLAY capability (a playback target). Seek is sent as a
 * relative nudge from whatever the target last reported; if the target reports no position we seek
 * from 0 (a sensible default for a fresh remote).
 *
 * @param target the device being controlled.
 * @param lastReportedSeconds the target's last-known playback position (from its presence playback
 *   line), used as the base for relative seeks. Null ⇒ treat as 0.
 */
@Composable
fun RemoteControlSheet(
    target: OnlineDeviceRow,
    onSend: (
        type: DeviceCommandType,
        seekSeconds: Double?,
    ) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    lastReportedSeconds: Double? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Remote: ${target.name}",
            style = MaterialTheme.typography.titleMedium,
        )
        target.playbackLine?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val base = lastReportedSeconds?.takeIf { it.isFinite() && it > 0 } ?: 0.0

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { onSend(DeviceCommandType.PREV, null) }) { Text("⏮") }
            Button(onClick = { onSend(DeviceCommandType.SEEK, (base - 10).coerceAtLeast(0.0)) }) { Text("-10s") }
            Button(onClick = { onSend(DeviceCommandType.RESUME, null) }) { Text("Play") }
            Button(onClick = { onSend(DeviceCommandType.PAUSE, null) }) { Text("Pause") }
            Button(onClick = { onSend(DeviceCommandType.SEEK, base + 10) }) { Text("+10s") }
            Button(onClick = { onSend(DeviceCommandType.NEXT, null) }) { Text("⏭") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSend(DeviceCommandType.STOP, null) }) { Text("Stop") }
            Button(onClick = onClose) { Text("Close") }
        }
    }
}
