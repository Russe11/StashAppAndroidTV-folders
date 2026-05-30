package com.github.damontecres.stashapp.ui.components.states

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

class SnackbarLauncher(
    private val scope: CoroutineScope,
    private val host: SnackbarHostState?,
    private val context: Context,
) {
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        if (host != null) {
            scope.launch {
                val result = host.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    withDismissAction = actionLabel == null,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onAction?.invoke()
                }
            }
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun rememberSnackbarLauncher(): SnackbarLauncher {
    val scope = rememberCoroutineScope()
    val host = LocalSnackbarHostState.current
    val context = LocalContext.current
    return remember(scope, host, context) {
        SnackbarLauncher(scope = scope, host = host, context = context)
    }
}
