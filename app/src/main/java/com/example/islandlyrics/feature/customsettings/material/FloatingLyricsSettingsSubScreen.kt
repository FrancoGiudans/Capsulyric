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
import com.example.islandlyrics.feature.settings.material.SettingsTextItem
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsDisplayConfig
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
    var displayModes by remember {
        mutableStateOf(
            FloatingLyricsDisplayConfig.readDisplayModes(prefs)
        )
    }
    var showNeighborLine by remember {
        mutableStateOf(
            FloatingLyricsDisplayConfig.readShowNeighborLine(prefs)
        )
    }
    var neighborAlignment by remember {
        mutableStateOf(
            FloatingLyricsNeighborAlignment.from(
                prefs.getString(FloatingLyricsRenderer.PREF_NEIGHBOR_ALIGNMENT, FloatingLyricsNeighborAlignment.CENTER.value)
            )
        )
    }
    val showDisplayModeDialog = remember { mutableStateOf(false) }
    val showNeighborAlignmentDialog = remember { mutableStateOf(false) }
    var wordHighlight by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_WORD_HIGHLIGHT, true)) }
    var textSizeSp by remember { mutableFloatStateOf(prefs.getFloat(FloatingLyricsRenderer.PREF_TEXT_SIZE, 15f)) }

    var customTextColor by remember { 
        mutableStateOf(Color(prefs.getInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, android.graphics.Color.WHITE)))
    }
    var floatingTextColorEditing by remember { mutableStateOf(false) }
    var floatingTextColorSnapshot by remember { mutableStateOf(customTextColor) }
    val keepOneText = stringResource(R.string.settings_home_lyric_preview_keep_one)

    fun setDisplayMode(mode: FloatingLyricsDisplayMode, checked: Boolean) {
        val nextModes = FloatingLyricsDisplayMode.toggledModes(displayModes, mode, checked)
        if (nextModes == null) {
            Toast.makeText(context, keepOneText, Toast.LENGTH_SHORT).show()
            return
        }
        displayModes = nextModes
        prefs.edit {
            putString(FloatingLyricsRenderer.PREF_DISPLAY_MODE, FloatingLyricsDisplayMode.preferenceValue(nextModes))
            putBoolean(FloatingLyricsRenderer.PREF_SHOW_NEIGHBOR_LINE, showNeighborLine)
        }
    }

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
                selectedModes = displayModes,
                onClick = { showDisplayModeDialog.value = true }
            )

            SettingsCardDivider()
            SettingsSwitchItem(
                title = stringResource(R.string.settings_floating_show_neighbor_line),
                subtitle = stringResource(R.string.settings_floating_show_neighbor_line_desc),
                checked = showNeighborLine,
                onCheckedChange = {
                    showNeighborLine = it
                    prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_SHOW_NEIGHBOR_LINE, it) }
                }
            )

            SettingsCardDivider()
            FloatingNeighborAlignmentSelector(
                selectedAlignment = neighborAlignment,
                onClick = { showNeighborAlignmentDialog.value = true }
            )

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

    if (showDisplayModeDialog.value) {
        AlertDialog(
            onDismissRequest = { showDisplayModeDialog.value = false },
            title = { Text(stringResource(R.string.settings_floating_display_mode)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FloatingDisplayModeOption(
                        title = stringResource(R.string.settings_floating_mode_single),
                        checked = FloatingLyricsDisplayMode.LYRIC in displayModes,
                        onCheckedChange = { setDisplayMode(FloatingLyricsDisplayMode.LYRIC, it) }
                    )
                    FloatingDisplayModeOption(
                        title = stringResource(R.string.settings_floating_mode_translation),
                        checked = FloatingLyricsDisplayMode.TRANSLATION in displayModes,
                        onCheckedChange = { setDisplayMode(FloatingLyricsDisplayMode.TRANSLATION, it) }
                    )
                    FloatingDisplayModeOption(
                        title = stringResource(R.string.settings_floating_mode_romanization),
                        checked = FloatingLyricsDisplayMode.ROMANIZATION in displayModes,
                        onCheckedChange = { setDisplayMode(FloatingLyricsDisplayMode.ROMANIZATION, it) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisplayModeDialog.value = false }) {
                    Text(stringResource(R.string.backup_dialog_confirm))
                }
            }
        )
    }

    if (showNeighborAlignmentDialog.value) {
        AlertDialog(
            onDismissRequest = { showNeighborAlignmentDialog.value = false },
            title = { Text(stringResource(R.string.settings_floating_neighbor_alignment)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FloatingRadioRow(
                        title = stringResource(R.string.settings_floating_alignment_center),
                        subtitle = stringResource(R.string.settings_floating_alignment_center_desc),
                        selected = neighborAlignment == FloatingLyricsNeighborAlignment.CENTER,
                        onClick = {
                            neighborAlignment = FloatingLyricsNeighborAlignment.CENTER
                            prefs.edit {
                                putString(FloatingLyricsRenderer.PREF_NEIGHBOR_ALIGNMENT, FloatingLyricsNeighborAlignment.CENTER.value)
                            }
                        }
                    )
                    FloatingRadioRow(
                        title = stringResource(R.string.settings_floating_alignment_split),
                        subtitle = stringResource(R.string.settings_floating_alignment_split_desc),
                        selected = neighborAlignment == FloatingLyricsNeighborAlignment.SPLIT_START_END,
                        onClick = {
                            neighborAlignment = FloatingLyricsNeighborAlignment.SPLIT_START_END
                            prefs.edit {
                                putString(FloatingLyricsRenderer.PREF_NEIGHBOR_ALIGNMENT, FloatingLyricsNeighborAlignment.SPLIT_START_END.value)
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNeighborAlignmentDialog.value = false }) {
                    Text(stringResource(R.string.backup_dialog_confirm))
                }
            }
        )
    }
}

@Composable
private fun FloatingDisplayModeSelector(
    selectedModes: Set<FloatingLyricsDisplayMode>,
    onClick: () -> Unit
) {
    SettingsTextItem(
        title = stringResource(R.string.settings_floating_display_mode),
        value = selectedModes.labelForFloatingDisplayMode(),
        onClick = onClick
    )
}

@Composable
private fun FloatingNeighborAlignmentSelector(
    selectedAlignment: FloatingLyricsNeighborAlignment,
    onClick: () -> Unit
) {
    val label = when (selectedAlignment) {
        FloatingLyricsNeighborAlignment.CENTER -> stringResource(R.string.settings_floating_alignment_center)
        FloatingLyricsNeighborAlignment.SPLIT_START_END -> stringResource(R.string.settings_floating_alignment_split)
    }
    SettingsTextItem(
        title = stringResource(R.string.settings_floating_neighbor_alignment),
        value = label,
        onClick = onClick
    )
}

@Composable
private fun FloatingDisplayModeOption(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
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

@Composable
private fun Set<FloatingLyricsDisplayMode>.labelForFloatingDisplayMode(): String {
    val labels = buildList {
        if (FloatingLyricsDisplayMode.LYRIC in this@labelForFloatingDisplayMode) {
            add(stringResource(R.string.settings_floating_mode_single))
        }
        if (FloatingLyricsDisplayMode.TRANSLATION in this@labelForFloatingDisplayMode) {
            add(stringResource(R.string.settings_floating_mode_translation))
        }
        if (FloatingLyricsDisplayMode.ROMANIZATION in this@labelForFloatingDisplayMode) {
            add(stringResource(R.string.settings_floating_mode_romanization))
        }
    }
    return labels.ifEmpty {
        listOf(stringResource(R.string.settings_floating_mode_single))
    }.joinToString(" / ")
}
