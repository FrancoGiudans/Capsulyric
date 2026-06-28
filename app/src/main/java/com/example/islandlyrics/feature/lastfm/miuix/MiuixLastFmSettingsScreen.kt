package com.example.islandlyrics.feature.lastfm.miuix

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.effects.miuixPageScroll
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixLastFmSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.of(context) }
    val store = remember { LastFmSecureStore(context) }
    val api = remember { LastFmApiClient() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var credentials by remember { mutableStateOf(store.getCredentials()) }
    var enabled by remember { mutableStateOf(AppPreferences.isLastFmEnabled(prefs)) }
    var apiKey by remember { mutableStateOf(credentials.apiKey) }
    var apiSecret by remember { mutableStateOf(credentials.apiSecret) }
    var busy by remember { mutableStateOf(false) }
    var authInProgress by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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
            message = offlineBlockedText
            return
        }
        if (saveFirst && !saveCredentials()) {
            message = missingCredentialsText
            return
        }
        val fresh = store.getCredentials()
        if (!fresh.hasApiCredentials) {
            message = missingCredentialsText
            return
        }
        busy = true
        scope.launch {
            api.getToken(fresh).onSuccess { token ->
                store.savePendingToken(token)
                authInProgress = true
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.authUrl(fresh.apiKey, token))))
                message = authOpenedText
            }.onFailure {
                message = connectFailedText
            }
            busy = false
        }
    }

    fun finishConnection() {
        if (OfflineModeManager.isEnabled(context)) {
            message = offlineBlockedText
            return
        }
        busy = true
        scope.launch {
            val token = store.getPendingToken()
            val fresh = store.getCredentials()
            if (token.isNullOrBlank() || !fresh.hasApiCredentials) {
                message = missingCredentialsText
            } else {
                api.getSession(fresh, token).onSuccess { session ->
                    store.saveSession(session.key, session.username)
                    prefs.edit { putBoolean(AppPreferences.Keys.LASTFM_ENABLED, true) }
                    enabled = true
                    authInProgress = false
                    refresh()
                    message = connectSuccessText
                }.onFailure {
                    message = connectFailedText
                }
            }
            busy = false
        }
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.lastfm_title),
                largeTitle = stringResource(R.string.lastfm_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        MiuixBackIcon(contentDescription = "Back")
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .miuixPageScroll(scrollBehavior),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.lastfm_description),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        if (!credentials.isConnected) {
                            Text(
                                text = stringResource(R.string.lastfm_setup_title),
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.lastfm_setup_steps),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LastFmApiClient.API_KEY_CREATE_URL)))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.lastfm_create_api_key))
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.lastfm_connected_note),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                        Text(
                            text = if (credentials.isConnected) {
                                stringResource(R.string.lastfm_connected_as, credentials.username ?: "")
                            } else {
                                stringResource(R.string.lastfm_not_connected)
                            },
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }

            item { SmallTitle(text = stringResource(R.string.lastfm_enable)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SwitchPreference(
                        title = stringResource(R.string.lastfm_enable),
                        summary = stringResource(R.string.lastfm_enable_desc),
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            prefs.edit { putBoolean(AppPreferences.Keys.LASTFM_ENABLED, it) }
                        }
                    )
                }
            }

            if (!credentials.isConnected) {
                item { SmallTitle(text = stringResource(R.string.lastfm_api_key)) }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = stringResource(R.string.lastfm_api_key),
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextField(
                                value = apiSecret,
                                onValueChange = { apiSecret = it },
                                label = stringResource(R.string.lastfm_api_secret),
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            if (credentials.isConnected) {
                                openAuthorization(saveFirst = false)
                            } else {
                                message = if (saveCredentials()) savedText else missingCredentialsText
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (credentials.isConnected) {
                                stringResource(R.string.lastfm_reauthorize)
                            } else {
                                stringResource(R.string.lastfm_save_credentials)
                            }
                        )
                    }
                    if (!credentials.isConnected) {
                        Button(
                            enabled = !busy,
                            onClick = { openAuthorization(saveFirst = true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lastfm_open_auth))
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
                        text = stringResource(R.string.lastfm_disconnect),
                        onClick = {
                            store.clearAll()
                            prefs.edit { putBoolean(AppPreferences.Keys.LASTFM_ENABLED, false) }
                            enabled = false
                            authInProgress = false
                            refresh()
                            message = disconnectedText
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            message?.let { current ->
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(current, color = MiuixTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
