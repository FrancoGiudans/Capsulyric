package com.example.islandlyrics.feature.customsettings.miuix

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.islandlyrics.R
import com.example.islandlyrics.ui.common.FloatingLyricsRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch

@Composable
fun MiuixFloatingLyricsSettingsSubScreen(prefs: SharedPreferences, scope: CoroutineScope) {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_KEY, false)) }
    var showAlbumArt by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, true)) }
    var followAlbumColor by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, true)) }
    var textStroke by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, true)) }
    var textBackground by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, false)) }
    
    var textSizeSp by remember { mutableStateOf(prefs.getFloat(FloatingLyricsRenderer.PREF_TEXT_SIZE, 15f)) }
    var customTextColor by remember { 
        mutableStateOf(Color(prefs.getInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, android.graphics.Color.WHITE)))
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = stringResource(R.string.settings_floating_lyrics_enabled),
            summary = stringResource(R.string.settings_floating_lyrics_enabled_desc),
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
            SuperSwitch(
                title = stringResource(R.string.settings_floating_show_album_art),
                summary = stringResource(R.string.settings_floating_show_album_art_desc),
                checked = showAlbumArt,
                onCheckedChange = {
                    showAlbumArt = it
                    prefs.edit().putBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, it).apply()
                }
            )
        
        SuperSwitch(
            title = stringResource(R.string.settings_floating_text_stroke),
            summary = stringResource(R.string.settings_floating_text_stroke_desc),
            checked = textStroke,
            onCheckedChange = {
                textStroke = it
                prefs.edit().putBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, it).apply()
            }
        )
        
        SuperSwitch(
            title = stringResource(R.string.settings_floating_text_background),
            summary = stringResource(R.string.settings_floating_text_background_desc),
            checked = textBackground,
            onCheckedChange = {
                textBackground = it
                prefs.edit().putBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, it).apply()
            }
        )

        SuperSwitch(
            title = stringResource(R.string.settings_floating_follow_album_color),
            summary = stringResource(R.string.settings_floating_follow_album_color_desc),
            checked = followAlbumColor,
            onCheckedChange = {
                followAlbumColor = it
                prefs.edit().putBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, it).apply()
            }
        )
        
        if (!followAlbumColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_floating_text_color),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                ColorPalette(
                    color = customTextColor,
                    onColorChanged = { color ->
                        customTextColor = color
                        prefs.edit().putInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, color.toArgb()).apply()
                    }
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "${stringResource(R.string.settings_floating_text_size)}: ${textSizeSp.toInt()}sp",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = (textSizeSp - 10f) / (32f - 10f),
                onValueChange = { value -> 
                    val newSize = 10f + value * (32f - 10f)
                    val steppedSize = Math.round(newSize / 2f) * 2f
                    textSizeSp = steppedSize
                    prefs.edit().putFloat(FloatingLyricsRenderer.PREF_TEXT_SIZE, steppedSize).apply()
                }
            )
        }
        }
    }
}
