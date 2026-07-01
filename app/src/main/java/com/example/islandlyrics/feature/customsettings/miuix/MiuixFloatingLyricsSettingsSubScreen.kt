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
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsDisplayConfig
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsDisplayMode
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsNeighborAlignment
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsRenderer
import kotlinx.coroutines.CoroutineScope
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
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
            selectedModes = displayModes,
            onClick = { showDisplayModeDialog.value = true }
        )

        SuperSwitch(
            title = stringResource(R.string.settings_floating_show_neighbor_line),
            summary = stringResource(R.string.settings_floating_show_neighbor_line_desc),
            checked = showNeighborLine,
            onCheckedChange = {
                showNeighborLine = it
                prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_SHOW_NEIGHBOR_LINE, it) }
            }
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

    if (showDisplayModeDialog.value) {
        MiuixBlurDialog(
            show = true,
            title = stringResource(R.string.settings_floating_display_mode),
            onDismissRequest = { showDisplayModeDialog.value = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                CheckboxPreference(
                    title = stringResource(R.string.settings_floating_mode_single),
                    checked = FloatingLyricsDisplayMode.LYRIC in displayModes,
                    onCheckedChange = { setDisplayMode(FloatingLyricsDisplayMode.LYRIC, it) }
                )
                CheckboxPreference(
                    title = stringResource(R.string.settings_floating_mode_translation),
                    checked = FloatingLyricsDisplayMode.TRANSLATION in displayModes,
                    onCheckedChange = { setDisplayMode(FloatingLyricsDisplayMode.TRANSLATION, it) }
                )
                CheckboxPreference(
                    title = stringResource(R.string.settings_floating_mode_romanization),
                    checked = FloatingLyricsDisplayMode.ROMANIZATION in displayModes,
                    onCheckedChange = { setDisplayMode(FloatingLyricsDisplayMode.ROMANIZATION, it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    text = stringResource(R.string.backup_dialog_confirm),
                    onClick = { showDisplayModeDialog.value = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun FloatingDisplayModeSelector(
    selectedModes: Set<FloatingLyricsDisplayMode>,
    onClick: () -> Unit
) {
    SuperArrow(
        title = stringResource(R.string.settings_floating_display_mode),
        summary = stringResource(
            R.string.settings_home_lyric_preview_summary_fmt,
            selectedModes.labelForFloatingDisplayMode()
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
