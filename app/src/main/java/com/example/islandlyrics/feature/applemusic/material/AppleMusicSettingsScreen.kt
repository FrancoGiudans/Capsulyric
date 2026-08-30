/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.feature.applemusic.material

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.islandlyrics.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.feature.applemusic.AppleMusicLoginActivity
import com.example.islandlyrics.feature.applemusic.AppleMusicWebLoginHelper
import com.example.islandlyrics.integration.applemusic.AppleMusicSecureStore
import com.example.islandlyrics.lyrics.online.provider.AppleMusicStateCache
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor

private data class AppleOption(val value: String, val labelRes: Int)

private val storefrontOptions = listOf(
    AppleOption("us", R.string.apple_music_storefront_us),
    AppleOption("cn", R.string.apple_music_storefront_cn),
    AppleOption("jp", R.string.apple_music_storefront_jp),
    AppleOption("hk", R.string.apple_music_storefront_hk),
    AppleOption("gb", R.string.apple_music_storefront_gb)
)

private val languageOptions = listOf(
    AppleOption("en-US", R.string.apple_music_language_en_us),
    AppleOption("zh-Hans", R.string.apple_music_language_zh_hans),
    AppleOption("zh-Hant", R.string.apple_music_language_zh_hant),
    AppleOption("ja", R.string.apple_music_language_ja)
)

private val AppleMusicStatusSuccess = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleMusicSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.of(context) }
    var storefront by remember { mutableStateOf(AppPreferences.appleMusicStorefront(prefs)) }
    var language by remember { mutableStateOf(AppPreferences.appleMusicLanguage(prefs)) }

    val secureStore = remember { AppleMusicSecureStore(context) }
    var mediaUserToken by remember { mutableStateOf(secureStore.getMediaUserToken().orEmpty()) }
    var mutSaved by remember { mutableStateOf(false) }

    fun saveMut() {
        secureStore.saveMediaUserToken(mediaUserToken)
        AppleMusicStateCache.setMediaUserToken(mediaUserToken)
        mutSaved = true
    }

    fun clearMut() {
        mediaUserToken = ""
        secureStore.clearMediaUserToken()
        AppleMusicStateCache.setMediaUserToken("")
        mutSaved = true
    }

    val webLoginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mediaUserToken = secureStore.getMediaUserToken().orEmpty()
    }

    var tokenStatusRes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(mediaUserToken) {
        if (mediaUserToken.isBlank()) {
            tokenStatusRes = null
        } else {
            tokenStatusRes = R.string.apple_music_mut_status_checking
            val valid = withContext(Dispatchers.IO) {
                AppleMusicWebLoginHelper.validateMediaUserToken(context)
            }
            tokenStatusRes = when (valid) {
                true -> R.string.apple_music_mut_status_valid
                false -> R.string.apple_music_mut_status_expired
                null -> R.string.apple_music_mut_status_unknown
            }
        }
    }

    fun save() {
        prefs.edit {
            putString(AppPreferences.Keys.APPLE_MUSIC_STOREFRONT, storefront)
            putString(AppPreferences.Keys.APPLE_MUSIC_LANGUAGE, language)
        }
        AppleMusicStateCache.setStorefrontCache(storefront, language)
    }

    @Composable
    fun optionLabel(options: List<AppleOption>, value: String): String {
        val option = options.firstOrNull { it.value == value }
        return if (option != null) stringResource(option.labelRes) else value
    }

    val storefrontCustom = storefront !in storefrontOptions.map { it.value }
    val languageCustom = language !in languageOptions.map { it.value }

    MaterialBlurScaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MaterialBlurTopAppBar(
                title = { Text(stringResource(R.string.apple_music_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSectionHeader(
                text = stringResource(R.string.apple_music_settings_desc),
                marginTop = 8.dp
            )

            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.apple_music_login_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.apple_music_login_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.apple_music_login_expiry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.apple_music_mut_howto_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.apple_music_mut_howto_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = mediaUserToken,
                        onValueChange = {
                            mediaUserToken = it
                            mutSaved = false
                        },
                        label = { Text(stringResource(R.string.apple_music_mut_label)) },
                        placeholder = { Text(stringResource(R.string.apple_music_mut_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    tokenStatusRes?.let { res ->
                        Text(
                            text = stringResource(res),
                            style = MaterialTheme.typography.bodySmall,
                            color = when (res) {
                                R.string.apple_music_mut_status_valid -> AppleMusicStatusSuccess
                                R.string.apple_music_mut_status_expired -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (mutSaved) {
                        Text(
                            text = stringResource(R.string.apple_music_mut_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = { webLoginLauncher.launch(Intent(context, AppleMusicLoginActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apple_music_web_login_button))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { saveMut() },
                            enabled = mediaUserToken.isNotBlank()
                        ) {
                            Text(stringResource(R.string.apple_music_mut_save))
                        }
                        OutlinedButton(onClick = { clearMut() }) {
                            Text(stringResource(R.string.apple_music_mut_clear))
                        }
                    }
                }
            }

            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppleDropdownRow(
                        title = stringResource(R.string.apple_music_storefront),
                        value = if (storefrontCustom) {
                            stringResource(R.string.apple_music_custom_override)
                        } else {
                            optionLabel(storefrontOptions, storefront)
                        },
                        options = storefrontOptions,
                        currentValue = storefront,
                        enabled = mediaUserToken.isBlank(),
                        onSelect = {
                            storefront = it.value
                            save()
                        },
                        onSelectCustom = {
                            if (!storefrontCustom) {
                                storefront = ""
                                save()
                            }
                        }
                    )
                    if (storefrontCustom) {
                        OutlinedTextField(
                            value = storefront,
                            onValueChange = {
                                storefront = it
                                save()
                            },
                            label = { Text(stringResource(R.string.apple_music_storefront)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AppleDropdownRow(
                        title = stringResource(R.string.apple_music_language),
                        value = if (languageCustom) {
                            stringResource(R.string.apple_music_custom_override)
                        } else {
                            optionLabel(languageOptions, language)
                        },
                        options = languageOptions,
                        currentValue = language,
                        onSelect = {
                            language = it.value
                            save()
                        },
                        onSelectCustom = {
                            if (!languageCustom) {
                                language = ""
                                save()
                            }
                        }
                    )
                    if (languageCustom) {
                        OutlinedTextField(
                            value = language,
                            onValueChange = {
                                language = it
                                save()
                            },
                            label = { Text(stringResource(R.string.apple_music_language)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleDropdownRow(
    title: String,
    value: String,
    options: List<AppleOption>,
    currentValue: String,
    enabled: Boolean = true,
    onSelect: (AppleOption) -> Unit,
    onSelectCustom: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { expanded = true }, enabled = enabled) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        onClick = {
                            expanded = false
                            if (option.value != currentValue) onSelect(option)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.apple_music_custom_override)) },
                    onClick = {
                        expanded = false
                        onSelectCustom()
                    }
                )
            }
        }
    }
}
