package com.example.islandlyrics.feature.lastfm.material

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.integration.lastfm.LastFmApiClient
import com.example.islandlyrics.integration.lastfm.LastFmSecureStore
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import com.example.islandlyrics.feature.settings.material.SettingsSwitchItem
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastFmSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.of(context) }
    val store = remember { LastFmSecureStore(context) }
    val api = remember { LastFmApiClient() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var credentials by remember { mutableStateOf(store.getCredentials()) }
    var enabled by remember { mutableStateOf(AppPreferences.isLastFmEnabled(prefs)) }
    var apiKey by remember { mutableStateOf(credentials.apiKey) }
    var apiSecret by remember { mutableStateOf(credentials.apiSecret) }
    var busy by remember { mutableStateOf(false) }
    var authInProgress by remember { mutableStateOf(false) }

    val savedText = stringResource(R.string.lastfm_saved)
    val missingCredentialsText = stringResource(R.string.lastfm_missing_credentials)
    val authOpenedText = stringResource(R.string.lastfm_auth_opened)
    val connectSuccessText = stringResource(R.string.lastfm_connect_success)
    val connectFailedText = stringResource(R.string.lastfm_connect_failed)
    val disconnectedText = stringResource(R.string.lastfm_disconnected)
    val offlineBlockedText = stringResource(R.string.offline_mode_network_blocked)

    fun refresh() {
        credentials = store.getCredentials()
        apiKey = credentials.apiKey
        apiSecret = credentials.apiSecret
    }

    fun saveCredentials(): Boolean {
        if (apiKey.isBlank() || apiSecret.isBlank()) return false
        store.saveApiCredentials(apiKey, apiSecret)
        refresh()
        return true
    }

    fun openAuthorization(saveFirst: Boolean) {
        if (OfflineModeManager.isEnabled(context)) {
            scope.launch { snackbarHostState.showSnackbar(offlineBlockedText) }
            return
        }
        if (saveFirst && !saveCredentials()) {
            scope.launch { snackbarHostState.showSnackbar(missingCredentialsText) }
            return
        }
        val fresh = store.getCredentials()
        if (!fresh.hasApiCredentials) {
            scope.launch { snackbarHostState.showSnackbar(missingCredentialsText) }
            return
        }
        busy = true
        scope.launch {
            val result = api.getToken(fresh)
            result.onSuccess { token ->
                store.savePendingToken(token)
                authInProgress = true
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.authUrl(fresh.apiKey, token))))
                snackbarHostState.showSnackbar(authOpenedText)
            }.onFailure {
                snackbarHostState.showSnackbar(connectFailedText)
            }
            busy = false
        }
    }

    fun finishConnection() {
        if (OfflineModeManager.isEnabled(context)) {
            scope.launch { snackbarHostState.showSnackbar(offlineBlockedText) }
            return
        }
        busy = true
        scope.launch {
            val token = store.getPendingToken()
            val fresh = store.getCredentials()
            if (token.isNullOrBlank() || !fresh.hasApiCredentials) {
                snackbarHostState.showSnackbar(missingCredentialsText)
            } else {
                api.getSession(fresh, token).onSuccess { session ->
                    store.saveSession(session.key, session.username)
                    prefs.edit { putBoolean(AppPreferences.Keys.LASTFM_ENABLED, true) }
                    enabled = true
                    authInProgress = false
                    refresh()
                    snackbarHostState.showSnackbar(connectSuccessText)
                }.onFailure {
                    snackbarHostState.showSnackbar(connectFailedText)
                }
            }
            busy = false
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.lastfm_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = neutralMaterialTopBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SettingsSectionHeader(text = stringResource(R.string.lastfm_setup_title), marginTop = 0.dp) }
            item {
                SettingsCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.lastfm_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!credentials.isConnected) {
                            Text(
                                text = stringResource(R.string.lastfm_setup_steps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LastFmApiClient.API_KEY_CREATE_URL)))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.lastfm_create_api_key))
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.lastfm_connected_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (credentials.isConnected) {
                                stringResource(R.string.lastfm_connected_as, credentials.username ?: "")
                            } else {
                                stringResource(R.string.lastfm_not_connected)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.lastfm_enable)) }
            item {
                SettingsCard {
                    SettingsSwitchItem(
                        title = stringResource(R.string.lastfm_enable),
                        subtitle = stringResource(R.string.lastfm_enable_desc),
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            prefs.edit { putBoolean(AppPreferences.Keys.LASTFM_ENABLED, it) }
                        }
                    )
                }
            }

            if (!credentials.isConnected) {
                item { SettingsSectionHeader(text = stringResource(R.string.lastfm_api_key)) }
                item {
                    SettingsCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text(stringResource(R.string.lastfm_api_key)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = apiSecret,
                                onValueChange = { apiSecret = it },
                                label = { Text(stringResource(R.string.lastfm_api_secret)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item { SettingsSectionHeader(text = stringResource(if (credentials.isConnected) R.string.lastfm_title else R.string.lastfm_open_auth)) }
            item {
                SettingsCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!credentials.isConnected) {
                            Button(
                                enabled = !busy,
                                onClick = {
                                    if (!saveCredentials()) {
                                        scope.launch { snackbarHostState.showSnackbar(missingCredentialsText) }
                                        return@Button
                                    }
                                    scope.launch { snackbarHostState.showSnackbar(savedText) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.lastfm_save_credentials))
                            }

                            Button(
                                enabled = !busy,
                                onClick = { openAuthorization(saveFirst = true) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.lastfm_open_auth))
                            }
                        } else {
                            Button(
                                enabled = !busy,
                                onClick = { openAuthorization(saveFirst = false) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.lastfm_reauthorize))
                            }
                        }

                        if (!credentials.isConnected || authInProgress) {
                            Button(
                                enabled = !busy,
                                onClick = { finishConnection() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.lastfm_finish_connect))
                            }
                        }

                        TextButton(
                            onClick = {
                                store.clearAll()
                                prefs.edit { putBoolean(AppPreferences.Keys.LASTFM_ENABLED, false) }
                                enabled = false
                                authInProgress = false
                                refresh()
                                scope.launch { snackbarHostState.showSnackbar(disconnectedText) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lastfm_disconnect))
                        }
                    }
                }
            }
        }
    }
}
