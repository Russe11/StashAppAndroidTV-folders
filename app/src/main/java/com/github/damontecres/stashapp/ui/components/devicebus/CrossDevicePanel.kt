package com.github.damontecres.stashapp.ui.components.devicebus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.ui.components.SwitchWithLabel
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.realtime.DeviceKind
import com.github.damontecres.stashapp.util.realtime.OnlineDeviceRow

/**
 * The "Cross-Device" settings panel: an opt-in toggle, the editable device name, and the live
 * online-devices list (design §6/§7 — Android-TV "shows the online-devices list").
 *
 * Capability-gated: when the server doesn't advertise `deviceBus` the panel renders a single
 * explanatory line and nothing else (cross-device UI hidden — invariant #1). Otherwise it shows
 * the opt-in switch (default OFF), and only when opted in does it list other devices. Scene names
 * in the playback line are resolved locally by the ViewModel (the wire carries only IDs).
 */
@Composable
fun CrossDevicePanel(
    server: StashServer,
    modifier: Modifier = Modifier,
    viewModel: CrossDeviceViewModel = viewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(server) { viewModel.init(context, server) }

    val supported by viewModel.deviceBusSupported.collectAsState()
    val optedIn by viewModel.optedIn.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val rows by viewModel.rows.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Cross-Device",
            style = MaterialTheme.typography.titleLarge,
        )

        if (!supported) {
            Text(
                text = "This server does not support cross-device presence.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            text =
                "Make this device discoverable and controllable on your server. " +
                    "Off by default — while off, this device is invisible to others and " +
                    "cannot be controlled. Nothing is shared outside your server, and no titles " +
                    "are sent over the network.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SwitchWithLabel(
            label = if (optedIn) "Discoverable (presence on)" else "Make this device discoverable",
            checked = optedIn,
            onStateChange = { viewModel.setOptedIn(context, it) },
        )

        Text(
            text = "This device: $deviceName",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (optedIn) {
            OnlineDevicesList(rows)
        }
    }
}

@Composable
private fun OnlineDevicesList(rows: List<OnlineDeviceRow>) {
    Text(
        text = "Online devices",
        style = MaterialTheme.typography.titleMedium,
    )
    if (rows.isEmpty()) {
        Text(
            text = "No other devices online.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.id }) { row ->
            OnlineDeviceItem(row)
        }
    }
}

@Composable
private fun OnlineDeviceItem(row: OnlineDeviceRow) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = "${kindLabel(row.kind)}  ${row.name}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = row.playbackLine ?: if (row.online) "Online" else "Offline",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun kindLabel(kind: DeviceKind): String =
    when (kind) {
        DeviceKind.TV -> "[TV]"
        DeviceKind.DESKTOP -> "[Desktop]"
        DeviceKind.MOBILE -> "[Phone]"
        DeviceKind.OTHER -> "[Device]"
    }
