package com.example.islandlyrics.feature.customsettings.material

import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsCardDivider
import com.example.islandlyrics.feature.settings.material.SettingsSwitchItem
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsDisplayMode
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsNeighborAlignment
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsRenderer
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingLyricsSettingsSubScreen(prefs: SharedPreferences) {
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

    SettingsCard {
        SettingsSwitchItem(
            title = stringResource(R.string.settings_floating_lyrics_enabled),
            subtitle = stringResource(R.string.settings_floating_lyrics_enabled_desc),
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
            SettingsCardDivider()
            SettingsSwitchItem(
                title = stringResource(R.string.settings_floating_show_album_art),
                subtitle = stringResource(R.string.settings_floating_show_album_art_desc),
                checked = showAlbumArt,
                onCheckedChange = {
                    showAlbumArt = it
                    prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, it) }
                }
            )

            SettingsCardDivider()
            SettingsSwitchItem(
                title = stringResource(R.string.settings_floating_text_stroke),
                subtitle = stringResource(R.string.settings_floating_text_stroke_desc),
                checked = textStroke,
                onCheckedChange = {
                    textStroke = it
                    prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, it) }
                }
            )

            SettingsCardDivider()
            SettingsSwitchItem(
                title = stringResource(R.string.settings_floating_text_background),
                subtitle = stringResource(R.string.settings_floating_text_background_desc),
                checked = textBackground,
                onCheckedChange = {
                    textBackground = it
                    prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, it) }
                }
            )

            SettingsCardDivider()
            FloatingDisplayModeSelector(
                selectedMode = displayMode,
                onModeSelected = {
                    displayMode = it
                    prefs.edit { putString(FloatingLyricsRenderer.PREF_DISPLAY_MODE, it.value) }
                }
            )

            if (displayMode == FloatingLyricsDisplayMode.NEIGHBOR_LINE) {
                SettingsCardDivider()
                FloatingNeighborAlignmentSelector(
                    selectedAlignment = neighborAlignment,
                    onAlignmentSelected = {
                        neighborAlignment = it
                        prefs.edit { putString(FloatingLyricsRenderer.PREF_NEIGHBOR_ALIGNMENT, it.value) }
                    }
                )
            }

            SettingsCardDivider()
            SettingsSwitchItem(
                title = stringResource(R.string.settings_floating_word_highlight),
                subtitle = stringResource(R.string.settings_floating_word_highlight_desc),
                checked = wordHighlight,
                onCheckedChange = {
                    wordHighlight = it
                    prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_WORD_HIGHLIGHT, it) }
                }
            )

            SettingsCardDivider()
            SettingsSwitchItem(
                title = stringResource(R.string.settings_floating_follow_album_color),
                subtitle = stringResource(R.string.settings_floating_follow_album_color_desc),
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
                SettingsCardDivider()
                MaterialEditableColorSection(
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

            SettingsCardDivider()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_floating_text_size) + ": ${textSizeSp.toInt()}sp",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = textSizeSp,
                    onValueChange = {
                        textSizeSp = it
                        prefs.edit { putFloat(FloatingLyricsRenderer.PREF_TEXT_SIZE, it) }
                    },
                    valueRange = 10f..32f,
                    steps = 10
                )
            }

            SettingsCardDivider()
            TextButton(
                onClick = {
                    FloatingLyricsRenderer.resetPosition(context)
                    Toast.makeText(
                        context,
                        R.string.settings_floating_position_reset_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.settings_floating_position_reset))
            }
        }
    }
}

@Composable
private fun FloatingDisplayModeSelector(
    selectedMode: FloatingLyricsDisplayMode,
    onModeSelected: (FloatingLyricsDisplayMode) -> Unit
) {
    val options = listOf(
        FloatingLyricsDisplayMode.SINGLE_LINE to (
            stringResource(R.string.settings_floating_mode_single) to stringResource(R.string.settings_floating_mode_single_desc)
        ),
        FloatingLyricsDisplayMode.ROMANIZATION to (
            stringResource(R.string.settings_floating_mode_romanization) to stringResource(R.string.settings_floating_mode_romanization_desc)
        ),
        FloatingLyricsDisplayMode.TRANSLATION to (
            stringResource(R.string.settings_floating_mode_translation) to stringResource(R.string.settings_floating_mode_translation_desc)
        ),
        FloatingLyricsDisplayMode.NEIGHBOR_LINE to (
            stringResource(R.string.settings_floating_mode_neighbor) to stringResource(R.string.settings_floating_mode_neighbor_desc)
        )
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_floating_display_mode),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        options.forEach { (mode, texts) ->
            FloatingRadioRow(
                title = texts.first,
                subtitle = texts.second,
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

@Composable
private fun FloatingNeighborAlignmentSelector(
    selectedAlignment: FloatingLyricsNeighborAlignment,
    onAlignmentSelected: (FloatingLyricsNeighborAlignment) -> Unit
) {
    val options = listOf(
        FloatingLyricsNeighborAlignment.CENTER to (
            stringResource(R.string.settings_floating_alignment_center) to stringResource(R.string.settings_floating_alignment_center_desc)
        ),
        FloatingLyricsNeighborAlignment.SPLIT_START_END to (
            stringResource(R.string.settings_floating_alignment_split) to stringResource(R.string.settings_floating_alignment_split_desc)
        )
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_floating_neighbor_alignment),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        options.forEach { (alignment, texts) ->
            FloatingRadioRow(
                title = texts.first,
                subtitle = texts.second,
                selected = selectedAlignment == alignment,
                onClick = { onAlignmentSelected(alignment) }
            )
        }
    }
}

@Composable
private fun FloatingRadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
