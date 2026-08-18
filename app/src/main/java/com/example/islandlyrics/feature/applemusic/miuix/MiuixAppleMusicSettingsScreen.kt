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

package com.example.islandlyrics.feature.applemusic.miuix

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.effects.miuixPageScroll
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import com.example.islandlyrics.ui.miuix.preference.BlurOverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

private data class AppleOption(val value: String, val labelRes: Int)

private val storefrontPreset = listOf(
    AppleOption("us", R.string.apple_music_storefront_us),
    AppleOption("cn", R.string.apple_music_storefront_cn),
    AppleOption("jp", R.string.apple_music_storefront_jp),
    AppleOption("hk", R.string.apple_music_storefront_hk),
    AppleOption("gb", R.string.apple_music_storefront_gb)
)

private val languagePreset = listOf(
    AppleOption("en-US", R.string.apple_music_language_en_us),
    AppleOption("zh-Hans", R.string.apple_music_language_zh_hans),
    AppleOption("zh-Hant", R.string.apple_music_language_zh_hant),
    AppleOption("ja", R.string.apple_music_language_ja)
)

private val AppleMusicStatusSuccess = Color(0xFF2E7D32)



@Composable
fun MiuixAppleMusicSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.of(context) }
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var storefront by remember { mutableStateOf(AppPreferences.appleMusicStorefront(prefs)) }
    var language by remember { mutableStateOf(AppPreferences.appleMusicLanguage(prefs)) }

    val secureStore = remember { AppleMusicSecureStore(context) }
    var mediaUserToken by remember { mutableStateOf(secureStore.getMediaUserToken().orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }

    val savedText = stringResource(R.string.apple_music_mut_saved)
    val clearedText = stringResource(R.string.apple_music_mut_cleared)

    fun saveMut() {
        if (mediaUserToken.isBlank()) return
        secureStore.saveMediaUserToken(mediaUserToken)
        AppleMusicStateCache.setMediaUserToken(mediaUserToken)
        message = savedText
    }

    fun clearMut() {
        mediaUserToken = ""
        secureStore.clearMediaUserToken()
        AppleMusicStateCache.setMediaUserToken("")
        message = clearedText
    }

    val webLoginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mediaUserToken = secureStore.getMediaUserToken().orEmpty()
        message = null
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

    val storefrontCustom = storefront !in storefrontPreset.map { it.value }
    val languageCustom = language !in languagePreset.map { it.value }

    fun save() {
        prefs.edit {
            putString(AppPreferences.Keys.APPLE_MUSIC_STOREFRONT, storefront)
            putString(AppPreferences.Keys.APPLE_MUSIC_LANGUAGE, language)
        }
        AppleMusicStateCache.setStorefrontCache(storefront, language)
    }

    val storefrontPresetTexts = storefrontPreset.associateWith { stringResource(it.labelRes) }
    val languagePresetTexts = languagePreset.associateWith { stringResource(it.labelRes) }
    val customOverrideText = stringResource(R.string.apple_music_custom_override)

    val storefrontItems = storefrontPreset.map { storefrontPresetTexts.getValue(it) } + customOverrideText
    val storefrontSelectedIndex = if (!storefrontCustom) {
        storefrontPreset.indexOfFirst { it.value == storefront }.coerceAtLeast(0)
    } else {
        storefrontItems.lastIndex
    }

    val languageItems = languagePreset.map { languagePresetTexts.getValue(it) } + customOverrideText
    val languageSelectedIndex = if (!languageCustom) {
        languagePreset.indexOfFirst { it.value == language }.coerceAtLeast(0)
    } else {
        languageItems.lastIndex
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.apple_music_settings_title),
                largeTitle = stringResource(R.string.apple_music_settings_title),
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
            item { SmallTitle(text = stringResource(R.string.apple_music_settings_desc)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.apple_music_login_section),
                            color = MiuixTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.apple_music_login_explain),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = stringResource(R.string.apple_music_login_expiry),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = stringResource(R.string.apple_music_mut_howto_title),
                            color = MiuixTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.apple_music_mut_howto_body),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        TextField(
                            value = mediaUserToken,
                            onValueChange = { mediaUserToken = it },
                            label = stringResource(R.string.apple_music_mut_label),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        tokenStatusRes?.let { res ->
                            Text(
                                text = stringResource(res),
                                color = when (res) {
                                    R.string.apple_music_mut_status_valid -> AppleMusicStatusSuccess
                                    R.string.apple_music_mut_status_expired -> MiuixTheme.colorScheme.error
                                    else -> MiuixTheme.colorScheme.onSurfaceSecondary
                                }
                            )
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { webLoginLauncher.launch(Intent(context, AppleMusicLoginActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apple_music_web_login_button))
                    }
                    Button(
                        enabled = mediaUserToken.isNotBlank(),
                        onClick = { saveMut() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apple_music_mut_save))
                    }
                    TextButton(
                        text = stringResource(R.string.apple_music_mut_clear),
                        onClick = { clearMut() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperDropdown(
                        title = stringResource(R.string.apple_music_storefront),
                        items = storefrontItems,
                        selectedIndex = storefrontSelectedIndex,
                        enabled = mediaUserToken.isBlank(),
                        onSelectedIndexChange = { index ->
                            if (index < storefrontPreset.size) {
                                storefront = storefrontPreset[index].value
                            } else {
                                if (!storefrontCustom) {
                                    storefront = ""
                                }
                            }
                            save()
                        }
                    )
                    if (storefrontCustom) {
                        TextField(
                            value = storefront,
                            onValueChange = {
                                storefront = it
                                save()
                            },
                            label = stringResource(R.string.apple_music_storefront),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    SuperDropdown(
                        title = stringResource(R.string.apple_music_language),
                        items = languageItems,
                        selectedIndex = languageSelectedIndex,
                        onSelectedIndexChange = { index ->
                            if (index < languagePreset.size) {
                                language = languagePreset[index].value
                            } else {
                                if (!languageCustom) {
                                    language = ""
                                }
                            }
                            save()
                        }
                    )
                    if (languageCustom) {
                        TextField(
                            value = language,
                            onValueChange = {
                                language = it
                                save()
                            },
                            label = stringResource(R.string.apple_music_language),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
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