package com.example.islandlyrics.feature.customsettings.material

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.settings.material.SettingsSwitchItem
import com.example.islandlyrics.ui.common.FloatingLyricsRenderer
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingLyricsSettingsSubScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_KEY, false)) }
    var showAlbumArt by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, true)) }
    var followAlbumColor by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, true)) }
    var textStroke by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, true)) }
    var textBackground by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, false)) }
    var textSizeSp by remember { mutableStateOf(prefs.getFloat(FloatingLyricsRenderer.PREF_TEXT_SIZE, 15f)) }

    var customTextColor by remember { 
        mutableStateOf(androidx.compose.ui.graphics.Color(prefs.getInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, android.graphics.Color.WHITE)))
    }

    // Material 3 slider for Text Size
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSwitchItem(
            title = stringResource(R.string.settings_floating_lyrics_enabled),
            subtitle = stringResource(R.string.settings_floating_lyrics_enabled_desc),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                if (it && !Settings.canDrawOverlays(context)) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    })
                    enabled = false
                } else {
                    prefs.edit().putBoolean(FloatingLyricsRenderer.PREF_KEY, it).apply()
                }
            }
        )

        if (enabled) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_floating_show_album_art),
                subtitle = stringResource(R.string.settings_floating_show_album_art_desc),
                checked = showAlbumArt,
                onCheckedChange = {
                    showAlbumArt = it
                    prefs.edit().putBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, it).apply()
                }
            )
        
        SettingsSwitchItem(
            title = stringResource(R.string.settings_floating_text_stroke),
            subtitle = stringResource(R.string.settings_floating_text_stroke_desc),
            checked = textStroke,
            onCheckedChange = {
                textStroke = it
                prefs.edit().putBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, it).apply()
            }
        )
        
        SettingsSwitchItem(
            title = stringResource(R.string.settings_floating_text_background),
            subtitle = stringResource(R.string.settings_floating_text_background_desc),
            checked = textBackground,
            onCheckedChange = {
                textBackground = it
                prefs.edit().putBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, it).apply()
            }
        )

        SettingsSwitchItem(
            title = stringResource(R.string.settings_floating_follow_album_color),
            subtitle = stringResource(R.string.settings_floating_follow_album_color_desc),
            checked = followAlbumColor,
            onCheckedChange = {
                followAlbumColor = it
                prefs.edit().putBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, it).apply()
            }
        )

        if (!followAlbumColor) {
            // Material Color Palette
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_floating_text_color),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                val presetColors = listOf(
                    Color.White,
                    Color.Cyan,
                    Color.Green,
                    Color.Yellow,
                    Color(0xFFFFA500), // Orange
                    Color.Red,
                    Color.Magenta
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(presetColors.size) { index ->
                        val color = presetColors[index]
                        val isSelected = customTextColor == color
                        val borderModifier = if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.dp, Color.Gray, CircleShape)
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    customTextColor = color
                                    prefs.edit().putInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, color.toArgb()).apply()
                                }
                                .then(borderModifier)
                        )
                    }
                }
            }
        }

        // Custom size slider
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.settings_floating_text_size) + ": ${textSizeSp.toInt()}sp",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = textSizeSp,
                onValueChange = { 
                    textSizeSp = it
                    prefs.edit().putFloat(FloatingLyricsRenderer.PREF_TEXT_SIZE, it).apply()
                },
                valueRange = 10f..32f,
                steps = 11 // (32 - 10) / 2 - 1
            )
        }
    }
    }
}
