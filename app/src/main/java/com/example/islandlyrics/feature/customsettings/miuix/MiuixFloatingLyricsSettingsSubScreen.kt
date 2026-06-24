package com.example.islandlyrics.feature.customsettings.miuix

import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
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
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsDisplayMode
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsNeighborAlignment
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsRenderer
import kotlinx.coroutines.CoroutineScope
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.preference.RadioButtonPreference as SuperRadio
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import kotlin.math.roundToInt

@Composable
@Suppress("UNUSED_PARAMETER")
fun MiuixFloatingLyricsSettingsSubScreen(prefs: SharedPreferences, scope: CoroutineScope) {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_KEY, false)) }
    var showAlbumArt by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, true)) }
    var followAlbumColor by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, true)) }
    var textStroke by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, true)) }
    var textBackground by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, false)) }
    var displayMode by remember {
        mutableStateOf(
            FloatingLyricsDisplayMode.from(
                prefs.getString(FloatingLyricsRenderer.PREF_DISPLAY_MODE, FloatingLyricsDisplayMode.SINGLE_LINE.value)
            )
        )
    }
    var neighborAlignment by remember {
        mutableStateOf(
            FloatingLyricsNeighborAlignment.from(
                prefs.getString(FloatingLyricsRenderer.PREF_NEIGHBOR_ALIGNMENT, FloatingLyricsNeighborAlignment.CENTER.value)
            )
        )
    }
    var wordHighlight by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_WORD_HIGHLIGHT, true)) }
    
    var textSizeSp by remember { mutableFloatStateOf(prefs.getFloat(FloatingLyricsRenderer.PREF_TEXT_SIZE, 15f)) }
    var customTextColor by remember { 
        mutableStateOf(Color(prefs.getInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, android.graphics.Color.WHITE)))
    }
    var floatingTextColorEditing by remember { mutableStateOf(false) }
    var floatingTextColorSnapshot by remember { mutableStateOf(customTextColor) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = stringResource(R.string.settings_floating_lyrics_enabled),
            summary = stringResource(R.string.settings_floating_lyrics_enabled_desc),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                if (it && !Settings.canDrawOverlays(context)) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = "package:${context.packageName}".toUri()
                    })
                    enabled = false
                } else {
                    prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_KEY, it) }
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
                    prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, it) }
                }
            )
        
        SuperSwitch(
            title = stringResource(R.string.settings_floating_text_stroke),
            summary = stringResource(R.string.settings_floating_text_stroke_desc),
            checked = textStroke,
            onCheckedChange = {
                textStroke = it
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, it) }
            }
        )
        
        SuperSwitch(
            title = stringResource(R.string.settings_floating_text_background),
            summary = stringResource(R.string.settings_floating_text_background_desc),
            checked = textBackground,
            onCheckedChange = {
                textBackground = it
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, it) }
            }
        )

        FloatingDisplayModeSelector(
            selectedMode = displayMode,
            onModeSelected = {
                displayMode = it
                prefs.edit { putString(FloatingLyricsRenderer.PREF_DISPLAY_MODE, it.value) }
            }
        )

        if (displayMode == FloatingLyricsDisplayMode.NEIGHBOR_LINE) {
            FloatingNeighborAlignmentSelector(
                selectedAlignment = neighborAlignment,
                onAlignmentSelected = {
                    neighborAlignment = it
                    prefs.edit { putString(FloatingLyricsRenderer.PREF_NEIGHBOR_ALIGNMENT, it.value) }
                }
            )
        }

        SuperSwitch(
            title = stringResource(R.string.settings_floating_word_highlight),
            summary = stringResource(R.string.settings_floating_word_highlight_desc),
            checked = wordHighlight,
            onCheckedChange = {
                wordHighlight = it
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_WORD_HIGHLIGHT, it) }
            }
        )

        SuperSwitch(
            title = stringResource(R.string.settings_floating_follow_album_color),
            summary = stringResource(R.string.settings_floating_follow_album_color_desc),
            checked = followAlbumColor,
            onCheckedChange = {
                if (it && floatingTextColorEditing) {
                    customTextColor = floatingTextColorSnapshot
                    floatingTextColorEditing = false
                }
                followAlbumColor = it
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, it) }
            }
        )
        
        if (!followAlbumColor) {
            MiuixEditableColorSection(
                title = stringResource(R.string.settings_floating_text_color),
                color = customTextColor,
                isEditing = floatingTextColorEditing,
                defaultActionText = stringResource(R.string.settings_color_default),
                onStartEditing = {
                    floatingTextColorSnapshot = customTextColor
                    floatingTextColorEditing = true
                },
                onColorChanged = { color ->
                    customTextColor = color
                },
                onApply = {
                    prefs.edit { putInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, customTextColor.toArgb()) }
                    floatingTextColorEditing = false
                },
                onCancel = {
                    customTextColor = floatingTextColorSnapshot
                    floatingTextColorEditing = false
                },
                onUseDefault = {
                    customTextColor = Color.White
                    floatingTextColorSnapshot = Color.White
                    prefs.edit { putInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, Color.White.toArgb()) }
                    floatingTextColorEditing = false
                }
            )
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
                    val steppedSize = (newSize / 2f).roundToInt() * 2f
                    textSizeSp = steppedSize
                    prefs.edit { putFloat(FloatingLyricsRenderer.PREF_TEXT_SIZE, steppedSize) }
                }
            )
        }

        TextButton(
            text = stringResource(R.string.settings_floating_position_reset),
            onClick = {
                FloatingLyricsRenderer.resetPosition(context)
                Toast.makeText(
                    context,
                    R.string.settings_floating_position_reset_toast,
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        }
    }
}

@Composable
private fun FloatingDisplayModeSelector(
    selectedMode: FloatingLyricsDisplayMode,
    onModeSelected: (FloatingLyricsDisplayMode) -> Unit
) {
    Text(
        text = stringResource(R.string.settings_floating_display_mode),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
    )
    FloatingModeRadio(
        title = stringResource(R.string.settings_floating_mode_single),
        summary = stringResource(R.string.settings_floating_mode_single_desc),
        selected = selectedMode == FloatingLyricsDisplayMode.SINGLE_LINE,
        onClick = { onModeSelected(FloatingLyricsDisplayMode.SINGLE_LINE) }
    )
    FloatingModeRadio(
        title = stringResource(R.string.settings_floating_mode_romanization),
        summary = stringResource(R.string.settings_floating_mode_romanization_desc),
        selected = selectedMode == FloatingLyricsDisplayMode.ROMANIZATION,
        onClick = { onModeSelected(FloatingLyricsDisplayMode.ROMANIZATION) }
    )
    FloatingModeRadio(
        title = stringResource(R.string.settings_floating_mode_translation),
        summary = stringResource(R.string.settings_floating_mode_translation_desc),
        selected = selectedMode == FloatingLyricsDisplayMode.TRANSLATION,
        onClick = { onModeSelected(FloatingLyricsDisplayMode.TRANSLATION) }
    )
    FloatingModeRadio(
        title = stringResource(R.string.settings_floating_mode_neighbor),
        summary = stringResource(R.string.settings_floating_mode_neighbor_desc),
        selected = selectedMode == FloatingLyricsDisplayMode.NEIGHBOR_LINE,
        onClick = { onModeSelected(FloatingLyricsDisplayMode.NEIGHBOR_LINE) }
    )
}

@Composable
private fun FloatingNeighborAlignmentSelector(
    selectedAlignment: FloatingLyricsNeighborAlignment,
    onAlignmentSelected: (FloatingLyricsNeighborAlignment) -> Unit
) {
    Text(
        text = stringResource(R.string.settings_floating_neighbor_alignment),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
    )
    FloatingModeRadio(
        title = stringResource(R.string.settings_floating_alignment_center),
        summary = stringResource(R.string.settings_floating_alignment_center_desc),
        selected = selectedAlignment == FloatingLyricsNeighborAlignment.CENTER,
        onClick = { onAlignmentSelected(FloatingLyricsNeighborAlignment.CENTER) }
    )
    FloatingModeRadio(
        title = stringResource(R.string.settings_floating_alignment_split),
        summary = stringResource(R.string.settings_floating_alignment_split_desc),
        selected = selectedAlignment == FloatingLyricsNeighborAlignment.SPLIT_START_END,
        onClick = { onAlignmentSelected(FloatingLyricsNeighborAlignment.SPLIT_START_END) }
    )
}

@Composable
private fun FloatingModeRadio(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    SuperRadio(
        title = title,
        summary = summary,
        selected = selected,
        onClick = onClick
    )
}
