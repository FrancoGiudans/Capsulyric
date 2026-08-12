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
 *  *
 *  *
 */

package com.example.islandlyrics.feature.customsettings.miuix

import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.R
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurBottomSheet
import com.example.islandlyrics.ui.miuix.reorderable.MiuixBlurReorderablePanel
import com.example.islandlyrics.ui.miuix.reorderable.MiuixReorderableListItem
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsDisplayConfig
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsNeighborAlignment
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsRenderer
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import kotlinx.coroutines.CoroutineScope
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import com.example.islandlyrics.ui.miuix.preference.BlurOverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
@Suppress("UNUSED_PARAMETER")
fun MiuixFloatingLyricsSettingsSubScreen(prefs: SharedPreferences, scope: CoroutineScope) {
    val context = LocalContext.current

    var featureEnabled by remember { mutableStateOf(LabFeatureManager.isFloatingLyricsEnabled(context)) }
    var showLyrics by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_KEY, false)) }
    var showAlbumArt by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, true)) }
    var followAlbumColor by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, true)) }
    var textStroke by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, true)) }
    var textBackground by remember { mutableStateOf(prefs.getBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, false)) }
    var secondaryTextModes by remember {
        mutableStateOf(
            FloatingLyricsDisplayConfig.readSecondaryTextModes(prefs).map { it.preferenceValue }
        )
    }
    var showSecondLine by remember {
        mutableStateOf(
            FloatingLyricsDisplayConfig.readShowSecondLine(prefs)
        )
    }
    var neighborAlignment by remember {
        mutableStateOf(
            FloatingLyricsNeighborAlignment.from(
                prefs.getString(FloatingLyricsRenderer.PREF_NEIGHBOR_ALIGNMENT, FloatingLyricsNeighborAlignment.CENTER.value)
            )
        )
    }
    val showSecondaryTextModeDialog = remember { mutableStateOf(false) }
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
            checked = featureEnabled,
            onCheckedChange = { enabled ->
                featureEnabled = enabled
                LabFeatureManager.setFloatingLyricsEnabled(context, enabled)
                if (!enabled) {
                    showLyrics = false
                }
            }
        )
        SuperSwitch(
            title = stringResource(R.string.settings_floating_lyrics_show),
            summary = stringResource(R.string.settings_floating_lyrics_show_desc),
            checked = showLyrics,
            onCheckedChange = {
                showLyrics = it
                if (it && !Settings.canDrawOverlays(context)) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = "package:${context.packageName}".toUri()
                    })
                    showLyrics = false
                } else {
                    prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_KEY, it) }
                }
            }
        )

        if (showLyrics) {
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

        SuperSwitch(
            title = stringResource(R.string.settings_floating_show_second_line),
            summary = stringResource(R.string.settings_floating_show_second_line_desc),
            checked = showSecondLine,
            onCheckedChange = {
                showSecondLine = it
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_SHOW_NEIGHBOR_LINE, it) }
            }
        )

        FloatingSecondaryTextSelector(
            secondaryTextModes = secondaryTextModes,
            onClick = { showSecondaryTextModeDialog.value = true }
        )

        FloatingNeighborAlignmentSelector(
            selectedAlignment = neighborAlignment,
            onAlignmentSelected = {
                neighborAlignment = it
                prefs.edit { putString(FloatingLyricsRenderer.PREF_NEIGHBOR_ALIGNMENT, it.value) }
            }
        )

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
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
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

    if (showSecondaryTextModeDialog.value) {
        MiuixFloatingSecondaryTextModeBottomSheet(
            selectedModes = secondaryTextModes,
            onDismiss = { showSecondaryTextModeDialog.value = false },
            onCommit = { modes ->
                secondaryTextModes = modes
                SecondaryTextMode.write(
                    prefs,
                    FloatingLyricsDisplayConfig.KEY_SECONDARY_TEXT_MODES,
                    modes.mapNotNull { SecondaryTextMode.from(it) }
                )
            }
        )
    }
}

@Composable
private fun FloatingSecondaryTextSelector(
    secondaryTextModes: List<String>,
    onClick: () -> Unit
) {
    SuperArrow(
        title = stringResource(R.string.settings_floating_secondary_text_mode),
        summary = stringResource(
            R.string.settings_floating_secondary_text_mode_summary,
            secondaryTextModes.labelForFloatingSecondaryText()
        ),
        onClick = onClick
    )
}

@Composable
private fun FloatingNeighborAlignmentSelector(
    selectedAlignment: FloatingLyricsNeighborAlignment,
    onAlignmentSelected: (FloatingLyricsNeighborAlignment) -> Unit
) {
    val alignments = FloatingLyricsNeighborAlignment.entries
    val labels = alignments.map { alignment ->
        when (alignment) {
            FloatingLyricsNeighborAlignment.CENTER -> stringResource(R.string.settings_floating_alignment_center)
            FloatingLyricsNeighborAlignment.SPLIT_START_END -> stringResource(R.string.settings_floating_alignment_split)
        }
    }
    val currentIndex = alignments.indexOf(selectedAlignment).takeIf { it >= 0 } ?: 0

    SuperDropdown(
        title = stringResource(R.string.settings_floating_neighbor_alignment),
        items = labels,
        selectedIndex = currentIndex,
        onSelectedIndexChange = { index -> onAlignmentSelected(alignments[index]) }
    )
}
@Composable
private fun MiuixFloatingSecondaryTextModeBottomSheet(
    selectedModes: List<String>,
    onDismiss: () -> Unit,
    onCommit: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val keepOneText = stringResource(R.string.settings_home_lyric_preview_keep_one)
    var modes by remember(selectedModes) { mutableStateOf(selectedModes) }
    var modeOrder by remember(selectedModes) {
        mutableStateOf(floatingSecondaryTextOrderOf(selectedModes))
    }

    MiuixBlurBottomSheet(
        show = true,
        title = stringResource(R.string.settings_floating_secondary_text_mode),
        onDismissRequest = onDismiss,
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.backup_dialog_cancel),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    val finalModes = modes.mapNotNull { SecondaryTextMode.from(it) }
                        .ifEmpty { listOf(SecondaryTextMode.NEXT_LYRIC) }
                        .map { it.preferenceValue }
                    onCommit(finalModes)
                    onDismiss()
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.backup_dialog_confirm),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MiuixBlurReorderablePanel(
                items = modeOrder.map { id ->
                    MiuixReorderableListItem(
                        id = id,
                        title = stringResource(SecondaryTextMode.from(id)!!.labelRes()),
                        checked = id in modes
                    )
                },
                onMove = { from, to ->
                    modeOrder = modeOrder.toMutableList().apply { add(to, removeAt(from)) }
                    modes = modeOrder.filter { it in modes }
                },
                onCheckedChange = { id, checked ->
                    if (checked) {
                        modes = modeOrder.filter { it == id || it in modes }
                    } else {
                        if (modes.size <= 1) {
                            Toast.makeText(context, keepOneText, Toast.LENGTH_SHORT).show()
                            return@MiuixBlurReorderablePanel
                        }
                        modes = modes - id
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun List<String>.labelForFloatingSecondaryText(): String {
    val labels = mapNotNull { mode ->
        SecondaryTextMode.from(mode)?.let { stringResource(it.labelRes()) }
    }
    return labels.ifEmpty {
        listOf(stringResource(SecondaryTextMode.NEXT_LYRIC.labelRes()))
    }.joinToString(" / ")
}

@Composable
private fun SecondaryTextMode.labelRes(): Int = when (this) {
    SecondaryTextMode.NEXT_LYRIC -> R.string.super_island_secondary_text_next_lyric
    SecondaryTextMode.ROMANIZATION -> R.string.super_island_secondary_text_romanization
    SecondaryTextMode.TRANSLATION -> R.string.super_island_secondary_text_translation
}

private fun floatingSecondaryTextOrderOf(modes: List<String>): List<String> {
    val result = LinkedHashSet<String>()
    modes.forEach { mode -> SecondaryTextMode.from(mode)?.let { result.add(it.preferenceValue) } }
    SecondaryTextMode.entries.forEach { result.add(it.preferenceValue) }
    return result.toList()
}
