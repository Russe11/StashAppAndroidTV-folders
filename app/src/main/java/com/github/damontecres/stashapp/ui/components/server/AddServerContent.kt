package com.github.damontecres.stashapp.ui.components.server

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.ui.PreviewTheme
import com.github.damontecres.stashapp.ui.compat.Button
import com.github.damontecres.stashapp.ui.compat.isNotTvDevice
import com.github.damontecres.stashapp.ui.components.CircularProgress
import com.github.damontecres.stashapp.ui.components.EditTextBox
import com.github.damontecres.stashapp.ui.components.SwitchWithLabel
import com.github.damontecres.stashapp.ui.theme.SemanticColors
import com.github.damontecres.stashapp.ui.tryRequestFocus
import com.github.damontecres.stashapp.util.LocalDebugSetup
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.StashPreferencesSerializer
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.TestResult
import com.github.damontecres.stashapp.util.isNotNullOrBlank
import com.github.damontecres.stashapp.util.preferences
import com.github.damontecres.stashapp.util.updateAdvancedPreferences
import kotlinx.coroutines.launch

private const val TAG = "AddServer"
internal const val USE_USERNAME_BY_DEFAULT = true

@Composable
fun AddServer(
    onSubmit: (StashServer) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageServersViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences by context.preferences.data.collectAsState(StashPreferencesSerializer.defaultValue)

    val testButtonFocusRequester = remember { FocusRequester() }
    val localDebugCredentials = remember { LocalDebugSetup.credentials }

    var serverUrl by remember { mutableStateOf(localDebugCredentials?.serverUrl.orEmpty()) }
    var username by remember { mutableStateOf(localDebugCredentials?.username.orEmpty()) }
    var apiKey by remember { mutableStateOf(localDebugCredentials?.password) }
    var showApiKey by remember { mutableStateOf(false) }
    var usePassword by remember {
        mutableStateOf(localDebugCredentials != null || USE_USERNAME_BY_DEFAULT)
    }

    var trustCerts by remember {
        mutableStateOf(preferences.advancedPreferences.trustSelfSignedCertificates)
    }

    val connectionState by viewModel.connectionState.observeAsState(ConnectionState.Inactive)

    var showTrustDialog by remember { mutableStateOf(false) }
    var localDebugAutoSetupStarted by remember { mutableStateOf(false) }

    LaunchedEffect(serverUrl, apiKey, trustCerts, username) {
        viewModel.clearConnectionStatus()
    }
    LaunchedEffect(localDebugCredentials, trustCerts) {
        val credentials = localDebugCredentials
        if (credentials != null && !localDebugAutoSetupStarted) {
            localDebugAutoSetupStarted = true
            viewModel.testServer(
                credentials.serverUrl,
                credentials.password,
                trustCerts,
                credentials.username,
                useUsername = true,
            )
        }
    }
    LaunchedEffect(connectionState) {
        connectionState.let {
            if (it is ConnectionState.Result && it.testResult is TestResult.Success) {
                Log.i(TAG, "Connection to $serverUrl successful!")
                onSubmit.invoke(StashServer(serverUrl, apiKey))
            } else if (it is ConnectionState.NewApiKey) {
                Log.i(TAG, "Connection to $serverUrl successful with new API key!")
                onSubmit.invoke(StashServer(serverUrl, it.apiKey))
            } else if (it is ConnectionState.Result && it.testResult is TestResult.SelfSignedCertRequired) {
                showTrustDialog = true
            }
        }
    }

    val labelWidth = if (usePassword) 88.dp else 72.dp

    val touchDevice = isNotTvDevice

    if (touchDevice) {
        AddServerTouchForm(
            serverUrl = serverUrl,
            onServerUrlChange = { serverUrl = it },
            username = username,
            onUsernameChange = { username = it },
            apiKey = apiKey,
            onApiKeyChange = { apiKey = it },
            usePassword = usePassword,
            onUsePasswordChange = {
                apiKey = null
                usePassword = it
            },
            showApiKey = showApiKey,
            onShowApiKeyChange = { showApiKey = it },
            connectionState = connectionState,
            onTestConnection = {
                viewModel.testServer(serverUrl, apiKey, trustCerts, username, usePassword)
            },
            modifier = modifier,
        )
    } else {
        AddServerTvForm(
            serverUrl = serverUrl,
            onServerUrlChange = { serverUrl = it },
            username = username,
            onUsernameChange = { username = it },
            apiKey = apiKey,
            onApiKeyChange = { apiKey = it },
            usePassword = usePassword,
            onUsePasswordChange = {
                apiKey = null
                usePassword = it
            },
            showApiKey = showApiKey,
            onShowApiKeyChange = { showApiKey = it },
            connectionState = connectionState,
            labelWidth = labelWidth,
            testButtonFocusRequester = testButtonFocusRequester,
            onTestConnection = {
                viewModel.testServer(serverUrl, apiKey, trustCerts, username, usePassword)
            },
            modifier = modifier,
        )
    }
    AnimatedVisibility(showTrustDialog) {
        AllowSelfSignedCertsDialog(
            onDismissRequest = { showTrustDialog = false },
            onEnableTrust = {
                scope.launch(StashCoroutineExceptionHandler()) {
                    context.preferences.updateData {
                        it.updateAdvancedPreferences {
                            trustSelfSignedCertificates = true
                        }
                    }
                }
                trustCerts = true
                if (localDebugCredentials != null) {
                    localDebugAutoSetupStarted = false
                }
            },
        )
    }
}

/**
 * TV layout: label-in-a-Row + [EditTextBox], unchanged from the original behavior.
 */
@Composable
private fun AddServerTvForm(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    apiKey: String?,
    onApiKeyChange: (String) -> Unit,
    usePassword: Boolean,
    onUsePasswordChange: (Boolean) -> Unit,
    showApiKey: Boolean,
    onShowApiKeyChange: (Boolean) -> Unit,
    connectionState: ConnectionState,
    labelWidth: androidx.compose.ui.unit.Dp,
    testButtonFocusRequester: FocusRequester,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.focusGroup(),
    ) {
        stickyHeader {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillParentMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.add_server),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillParentMaxWidth(),
                )

                Icon(
                    painter = painterResource(R.drawable.stash_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(64.dp),
                )
            }
        }

        // Server url
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stashapp_url),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(labelWidth),
                )
                EditTextBox(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    keyboardOptions =
                        KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            showKeyboardOnFocus = true,
                        ),
                    keyboardActions = KeyboardActions(),
                    leadingIcon = null,
                    isInputValid = {
                        connectionState.let {
                            when (it) {
                                ConnectionState.DuplicateServer -> false
                                ConnectionState.Inactive -> true
                                is ConnectionState.Result -> it.canConnect
                                ConnectionState.Testing -> true
                                is ConnectionState.NewApiKey -> true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Username
        if (usePassword) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.stashapp_config_general_auth_username),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(labelWidth),
                    )
                    EditTextBox(
                        value = username,
                        onValueChange = onUsernameChange,
                        keyboardOptions =
                            KeyboardOptions(
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next,
                                showKeyboardOnFocus = false,
                            ),
                        keyboardActions = KeyboardActions(),
                        leadingIcon = null,
                        isInputValid = {
                            connectionState.let {
                                when (it) {
                                    ConnectionState.DuplicateServer -> false
                                    ConnectionState.Inactive -> true
                                    is ConnectionState.Result -> it.canConnect
                                    ConnectionState.Testing -> true
                                    is ConnectionState.NewApiKey -> true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Password/API Key
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.animateItem(),
            ) {
                Text(
                    text =
                        stringResource(
                            if (usePassword) {
                                R.string.stashapp_config_general_auth_password
                            } else {
                                R.string.stashapp_config_general_auth_api_key
                            },
                        ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(labelWidth),
                )
                EditTextBox(
                    value = apiKey ?: "",
                    onValueChange = onApiKeyChange,
                    keyboardOptions =
                        KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = if (showApiKey) KeyboardType.Ascii else KeyboardType.Password,
                            imeAction = ImeAction.Next,
                            showKeyboardOnFocus = false,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onNext = {
                                testButtonFocusRequester.tryRequestFocus()
                            },
                        ),
                    leadingIcon = null,
                    enabled = true,
                    isInputValid = {
                        apiKey.isNullOrEmpty() ||
                            connectionState.let {
                                when (it) {
                                    ConnectionState.DuplicateServer -> true
                                    ConnectionState.Inactive -> true
                                    is ConnectionState.Result -> it.testResult is TestResult.Success
                                    ConnectionState.Testing -> true
                                    is ConnectionState.NewApiKey -> true
                                }
                            }
                    },
                    placeholder =
                        if (usePassword) {
                            null
                        } else {
                            {
                                Text(
                                    text = stringResource(R.string.setup_api_key_optional_hint),
                                    color =
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                            alpha = .25f,
                                        ),
                                )
                            }
                        },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (usePassword) {
            item {
                Text(
                    text = stringResource(R.string.setup_api_key_note),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        item {
            StatusText(connectionState)
        }

        item {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .animateItem(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SwitchWithLabel(
                        label = stringResource(R.string.setup_use_username),
                        checked = usePassword,
                        onStateChange = onUsePasswordChange,
                        modifier = Modifier,
                    )
                    SwitchWithLabel(
                        label =
                            if (usePassword) {
                                stringResource(R.string.setup_show_password)
                            } else {
                                stringResource(R.string.setup_show_api_key)
                            },
                        checked = showApiKey,
                        onStateChange = onShowApiKeyChange,
                        modifier = Modifier,
                    )
                }
            }
        }

        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillParentMaxWidth(),
            ) {
                Button(
                    onClick = onTestConnection,
                    enabled = serverUrl.isNotNullOrBlank(),
                    modifier = Modifier.focusRequester(testButtonFocusRequester),
                ) {
                    if (connectionState is ConnectionState.Testing) {
                        CircularProgress(Modifier.size(32.dp), false)
                    } else {
                        Text(
                            text = stringResource(R.string.test_connection),
//                color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Touch layout: native [androidx.compose.material3.OutlinedTextField]s stacked vertically,
 * a full-width primary action button, and the existing reveal/use-username switches.
 *
 * Connection/SSL/PIN logic is identical to the TV path — only the presentation differs.
 */
@Composable
private fun AddServerTouchForm(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    apiKey: String?,
    onApiKeyChange: (String) -> Unit,
    usePassword: Boolean,
    onUsePasswordChange: (Boolean) -> Unit,
    showApiKey: Boolean,
    onShowApiKeyChange: (Boolean) -> Unit,
    connectionState: ConnectionState,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val urlError =
        connectionState is ConnectionState.DuplicateServer ||
            (connectionState is ConnectionState.Result && !connectionState.canConnect)
    val errorMessage =
        when (connectionState) {
            is ConnectionState.DuplicateServer -> stringResource(R.string.setup_duplicate_server)
            is ConnectionState.Result -> connectionState.testResult.message.ifBlank { null }
            else -> null
        }

    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.focusGroup(),
    ) {
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .widthIn(max = 560.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.stash_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        item {
            androidx.compose.material3.Text(
                text = stringResource(R.string.add_server),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .widthIn(max = 560.dp),
            )
        }

        // Server url
        item {
            androidx.compose.material3.OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { androidx.compose.material3.Text(stringResource(R.string.stashapp_url)) },
                singleLine = true,
                isError = urlError,
                supportingText =
                    if (urlError && errorMessage != null) {
                        { androidx.compose.material3.Text(errorMessage) }
                    } else {
                        null
                    },
                keyboardOptions =
                    KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .widthIn(max = 560.dp),
            )
        }

        // Username
        if (usePassword) {
            item {
                androidx.compose.material3.OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = {
                        androidx.compose.material3.Text(
                            stringResource(R.string.stashapp_config_general_auth_username),
                        )
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next,
                        ),
                    modifier =
                        Modifier
                            .fillParentMaxWidth()
                            .widthIn(max = 560.dp),
                )
            }
        }

        // Password / API key
        item {
            androidx.compose.material3.OutlinedTextField(
                value = apiKey ?: "",
                onValueChange = onApiKeyChange,
                label = {
                    androidx.compose.material3.Text(
                        stringResource(
                            if (usePassword) {
                                R.string.stashapp_config_general_auth_password
                            } else {
                                R.string.stashapp_config_general_auth_api_key
                            },
                        ),
                    )
                },
                singleLine = true,
                placeholder =
                    if (usePassword) {
                        null
                    } else {
                        {
                            androidx.compose.material3.Text(
                                stringResource(R.string.setup_api_key_optional_hint),
                            )
                        }
                    },
                visualTransformation =
                    if (showApiKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                keyboardOptions =
                    KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = if (showApiKey) KeyboardType.Ascii else KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = { onTestConnection() },
                    ),
                modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .widthIn(max = 560.dp),
            )
        }

        if (usePassword) {
            item {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.setup_api_key_note),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .fillParentMaxWidth()
                            .widthIn(max = 560.dp),
                )
            }
        }

        // Reveal control: core material-icons set has no eye/visibility icon, so the
        // existing "Show password/API key" switch is kept as the visibility toggle.
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .widthIn(max = 560.dp),
            ) {
                SwitchWithLabel(
                    label = stringResource(R.string.setup_use_username),
                    checked = usePassword,
                    onStateChange = onUsePasswordChange,
                    modifier = Modifier,
                )
                SwitchWithLabel(
                    label =
                        if (usePassword) {
                            stringResource(R.string.setup_show_password)
                        } else {
                            stringResource(R.string.setup_show_api_key)
                        },
                    checked = showApiKey,
                    onStateChange = onShowApiKeyChange,
                    modifier = Modifier,
                )
            }
        }

        // Status (success message); connection errors are surfaced on the URL field above.
        item {
            Box(
                modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .widthIn(max = 560.dp),
            ) {
                StatusText(connectionState)
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .widthIn(max = 560.dp),
            ) {
                Button(
                    onClick = onTestConnection,
                    enabled = serverUrl.isNotNullOrBlank() && connectionState !is ConnectionState.Testing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (connectionState is ConnectionState.Testing) {
                        CircularProgress(Modifier.size(24.dp), false)
                    } else {
                        Text(text = stringResource(R.string.test_connection))
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
fun StatusText(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val duplicateServerMessage = stringResource(R.string.setup_duplicate_server)
    val message =
        state.let {
            when (it) {
                is ConnectionState.Result -> it.testResult.message.ifBlank { null }
                is ConnectionState.DuplicateServer -> duplicateServerMessage
                else -> null
            }
        }
    if (message.isNotNullOrBlank()) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    } else if (state is ConnectionState.Result && state.testResult is TestResult.Success) {
        Text(
            text = stringResource(R.string.success),
            color = SemanticColors.Organized,
            modifier = modifier,
        )
    }
}

@Composable
fun AllowSelfSignedCertsDialog(
    onDismissRequest: () -> Unit,
    onEnableTrust: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
            modifier =
                Modifier
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ),
        ) {
            item {
                Text(
                    text = stringResource(R.string.setup_self_signed_prompt),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier =
                        Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(),
                ) {
                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.focusRequester(focusRequester),
                    ) {
                        Text(
                            text = stringResource(R.string.setup_self_signed_no),
                        )
                    }
                    Button(
                        onClick = {
                            onDismissRequest.invoke()
                            onEnableTrust.invoke()
                        },
                        modifier = Modifier,
                    ) {
                        Text(
                            text = stringResource(R.string.setup_self_signed_yes),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AddServerPreview() {
    PreviewTheme {
        AllowSelfSignedCertsDialog(
            {},
            {},
        )
    }
}
