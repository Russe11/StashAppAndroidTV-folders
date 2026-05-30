package com.github.damontecres.stashapp.ui.components.devicebus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.ui.compat.Button
import com.github.damontecres.stashapp.util.realtime.ControllerConfirmationRequest
import com.github.damontecres.stashapp.util.realtime.RemoteControlHost

/**
 * Top-level TOFU confirmation host (R-C TARGET): listens for [RemoteControlHost.confirmations] and,
 * on the first command from an unknown controller, prompts "Allow <controller> to control this
 * device?" (design §9). The user's answer is remembered per controller; until they answer, the held
 * command does nothing. Place once near the app root so a remote command is confirmable from any
 * screen.
 *
 * Only ever fires while the user is opted into presence (the command subscription only runs then),
 * so this is inert for a device that hasn't enabled cross-device control.
 */
@Composable
fun RemoteControlConfirmationHost(modifier: Modifier = Modifier) {
    var pending by remember { mutableStateOf<ControllerConfirmationRequest?>(null) }

    LaunchedEffect(Unit) {
        RemoteControlHost.confirmations.collect { request ->
            // Show the latest request; a burst from the same controller collapses to one prompt.
            pending = request
        }
    }

    val request = pending ?: return
    Dialog(
        onDismissRequest = {
            RemoteControlHost.dismissConfirmation(request.controllerKey)
            pending = null
        },
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Allow remote control?",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        "${request.controllerName} wants to control playback on this device. " +
                            "Allow it to play, pause, seek, and queue scenes here?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            RemoteControlHost.resolveConfirmation(request.controllerKey, false)
                            pending = null
                        },
                    ) { Text("Block") }
                    Button(
                        onClick = {
                            RemoteControlHost.resolveConfirmation(request.controllerKey, true)
                            pending = null
                        },
                    ) { Text("Allow") }
                }
            }
        }
    }
}
