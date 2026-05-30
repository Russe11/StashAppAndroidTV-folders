package com.github.damontecres.stashapp.ui.components

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.stashapp.ui.FontAwesome
import com.github.damontecres.stashapp.ui.compat.ListItem
import com.github.damontecres.stashapp.ui.compat.isNotTvDevice
import com.github.damontecres.stashapp.ui.theme.Spacing
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface DialogItemEntry

data object DialogItemDivider : DialogItemEntry

data class DialogItem(
    val headlineContent: @Composable () -> Unit,
    val onClick: () -> Unit,
    val overlineContent: @Composable (() -> Unit)? = null,
    val supportingContent: @Composable (() -> Unit)? = null,
    val leadingContent: @Composable (BoxScope.() -> Unit)? = null,
    val trailingContent: @Composable (() -> Unit)? = null,
    val enabled: Boolean = true,
) : DialogItemEntry {
    constructor(
        text: String,
        @StringRes iconStringRes: Int,
        onClick: () -> Unit,
    ) : this(
        headlineContent = {
            Text(
                text = text,
//                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        leadingContent = {
            Text(
                text = stringResource(id = iconStringRes),
//                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontFamily = FontAwesome,
            )
        },
        onClick = onClick,
    )

    constructor(
        text: String,
        icon: ImageVector,
        iconTintColor: Color? = null,
        onClick: () -> Unit,
    ) : this(
        headlineContent = {
            Text(
                text = text,
//                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = iconTintColor ?: LocalContentColor.current,
            )
        },
        onClick = onClick,
    )

    constructor(
        text: String,
        onClick: () -> Unit,
    ) : this(
        headlineContent = {
            Text(
                text = text,
//                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        onClick = onClick,
    )

    companion object {
        fun divider(): DialogItemEntry = DialogItemDivider
    }
}

/**
 * On touch, make a [Dialog] dismissible by tapping the scrim (the action menu / option list
 * is not a TV focus trap there). On TV we keep the caller-supplied [DialogProperties] verbatim
 * so the existing D-pad/Back handling is untouched. We preserve [DialogProperties.usePlatformDefaultWidth]
 * so full-width dialogs stay full width.
 */
@Composable
private fun DialogProperties.touchDismissible(): DialogProperties =
    if (isNotTvDevice) {
        DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = usePlatformDefaultWidth,
        )
    } else {
        this
    }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialogPopup(
    showDialog: Boolean,
    title: String,
    dialogItems: List<DialogItemEntry>,
    onDismissRequest: () -> Unit,
    dismissOnClick: Boolean = true,
    waitToLoad: Boolean = true,
    properties: DialogProperties = DialogProperties(),
) {
    var waiting by remember { mutableStateOf(waitToLoad) }
    if (showDialog) {
        if (waitToLoad) {
            LaunchedEffect(Unit) {
                // This is a hack because a long click will propagate here and click the first list item
                // So this disables the list items assuming the user will stop pressing when the dialog appears
                // This is also bypassed in the code below if the user releases the enter/d-pad center button
                waiting = true
                delay(1000)
                waiting = false
            }
        } else {
            waiting = false
        }

        val onItemClick: (DialogItem) -> Unit = { item ->
            if (dismissOnClick) {
                onDismissRequest.invoke()
            }
            item.onClick.invoke()
        }

        if (isNotTvDevice) {
            // Touch: present the same action items as a native Material bottom sheet
            // (drag handle, tap-outside / swipe-down to dismiss) instead of a centered dialog.
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier.padding(
                                start = Spacing.lg,
                                end = Spacing.lg,
                                bottom = Spacing.sm,
                            ),
                    )
                    dialogItems.forEach { entry ->
                        when (entry) {
                            is DialogItemDivider -> HorizontalDivider(Modifier.height(16.dp))
                            is DialogItem ->
                                ListItem(
                                    selected = false,
                                    enabled = !waiting && entry.enabled,
                                    onClick = { onItemClick(entry) },
                                    headlineContent = entry.headlineContent,
                                    overlineContent = entry.overlineContent,
                                    supportingContent = entry.supportingContent,
                                    leadingContent = entry.leadingContent,
                                    trailingContent = entry.trailingContent,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                        }
                    }
                }
            }
            return
        }

        Dialog(
            onDismissRequest = onDismissRequest,
            // Only reached on TV (touch uses the bottom sheet above); touchDismissible() is a no-op there.
            properties = properties.touchDismissible(),
        ) {
            val elevatedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            LazyColumn(
                modifier =
                    Modifier
//                        .widthIn(min = 520.dp, max = 300.dp)
//                        .dialogFocusable()
                        .graphicsLayer {
                            this.clip = true
                            this.shape = RoundedCornerShape(28.0.dp)
                        }.drawBehind { drawRect(color = elevatedContainerColor) }
                        .padding(PaddingValues(24.dp))
                        .onKeyEvent { event ->
                            val code = event.nativeKeyEvent.keyCode
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                                code in
                                setOf(
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                                )
                            ) {
                                waiting = false
                            }
                            false
                        },
            ) {
                stickyHeader {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                items(dialogItems) {
                    when (it) {
                        is DialogItemDivider -> {
                            HorizontalDivider(Modifier.height(16.dp))
                        }

                        is DialogItem -> {
                            ListItem(
                                selected = false,
                                enabled = !waiting && it.enabled,
                                onClick = { onItemClick(it) },
                                headlineContent = it.headlineContent,
                                overlineContent = it.overlineContent,
                                supportingContent = it.supportingContent,
                                leadingContent = it.leadingContent,
                                trailingContent = it.trailingContent,
                                modifier = Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollableDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val scrollAmount = 100f
    val columnState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun scroll(reverse: Boolean = false) {
        scope.launch(StashCoroutineExceptionHandler()) {
            columnState.scrollBy(if (reverse) -scrollAmount else scrollAmount)
        }
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        LazyColumn(
            state = columnState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
            modifier =
                modifier
                    .width(600.dp)
                    .height(380.dp)
                    .focusable()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                    ).onKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            return@onKeyEvent false
                        }
                        if (it.key == Key.DirectionDown) {
                            scroll(false)
                            return@onKeyEvent true
                        }
                        if (it.key == Key.DirectionUp) {
                            scroll(true)
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    },
        )
    }
}

@Composable
fun MarkerDurationDialog(
    onDismissRequest: () -> Unit,
    onClick: (Long) -> Unit,
) {
    val dialogItems =
        remember {
            listOf(
                15.seconds,
                20.seconds,
                30.seconds,
                60.seconds,
                5.minutes,
                10.minutes,
                20.minutes,
            ).map {
                DialogItem(it.toString()) {
                    onClick.invoke(it.inWholeMilliseconds)
                }
            }
        }
    DialogPopup(
        showDialog = true,
        title = "How long?",
        dialogItems = dialogItems,
        onDismissRequest = onDismissRequest,
        dismissOnClick = false,
        waitToLoad = false,
        properties = DialogProperties(),
    )
}

@Composable
fun BasicDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    elevation: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        // Touch: tap-outside / Back dismisses. TV keeps the caller-supplied properties.
        properties = properties.touchDismissible(),
    ) {
        Box(
            modifier =
                Modifier
                    .shadow(elevation = elevation, shape = RoundedCornerShape(8.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(elevation),
                        shape = RoundedCornerShape(8.dp),
                    ),
        ) {
            content()
        }
    }
}
