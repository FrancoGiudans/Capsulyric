/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.example.islandlyrics.R
import com.example.islandlyrics.ui.adaptive.LocalAppWindowLayout
import com.example.islandlyrics.ui.adaptive.WindowSizeTier
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurBottomSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LyricSelectionSheet(
    show: Boolean,
    selection: LyricSelection,
    title: String,
    artist: String,
    cover: Bitmap?,
    appearance: PlaybackCoverAppearance?,
    loopRange: LyricLoopRange?,
    onSelectionChange: (LyricSelection) -> Unit,
    onLoop: (LyricLoopRange) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val surface = MiuixTheme.colorScheme.surface.toArgb()
    val appName = stringResource(R.string.app_name)
    val exportError = stringResource(R.string.lyrics_share_failed)
    val savedMessage = stringResource(R.string.lyrics_share_saved)
    val characterLimitMessage = stringResource(R.string.lyrics_share_limit, LyricShareCharacterLimit)
    val lineLimitMessage = stringResource(R.string.lyrics_share_line_limit, LyricShareLineLimit)
    var exporting by remember { mutableStateOf(false) }
    fun export(saveToGallery: Boolean) {
        if (exporting || !selection.canExport) return
        exporting = true
        val card = LyricShareCard(title, artist, cover, appearance, selection.selectedLines(), appName, surface)
        scope.launch {
            try {
                if (saveToGallery) {
                    LyricShareExporter.save(context, card)
                    Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                } else {
                    LyricShareExporter.share(context, card)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("LyricSelectionSheet", "Unable to export lyrics", error)
                Toast.makeText(context, exportError, Toast.LENGTH_SHORT).show()
            } catch (error: OutOfMemoryError) {
                Log.w("LyricSelectionSheet", "Unable to allocate lyric card", error)
                Toast.makeText(context, exportError, Toast.LENGTH_SHORT).show()
            } finally {
                exporting = false
            }
        }
    }

    val layoutInfo = LocalAppWindowLayout.current
    val isCompactLandscape = layoutInfo.tier == WindowSizeTier.CompactLandscape
    val viewport = if (layoutInfo.heightDp > 0.dp) {
        layoutInfo.heightDp
    } else {
        LocalConfiguration.current.screenHeightDp.dp
    }
    MiuixBlurBottomSheet(
        show = show,
        sheetMaxWidth = if (isCompactLandscape) 640.dp else 520.dp,
        insideMargin = DpSize(16.dp, 8.dp),
        allowDismiss = !exporting,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        renderInRootScaffold = false,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = viewport * if (isCompactLandscape) 0.88f else 0.78f).navigationBarsPadding().padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LyricSelectionHeader(title, artist, cover, selection)
            LyricSelectionCards(
                selection = selection,
                appearance = appearance,
                enabled = !exporting,
                onToggle = { index ->
                    val limit = if (index in selection.indices) null else selection.limitWhenAdding(index)
                    if (limit == null) {
                        onSelectionChange(selection.toggle(index))
                    } else {
                        val message = when (limit) {
                            LyricSelectionLimit.Lines -> lineLimitMessage
                            LyricSelectionLimit.Characters -> characterLimitMessage
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            LyricSelectionActions(
                exporting = exporting,
                canExport = selection.canExport,
                canCopy = selection.indices.isNotEmpty(),
                loopRange = loopRange,
                compactLandscape = isCompactLandscape,
                onShare = { export(saveToGallery = false) },
                onSave = { export(saveToGallery = true) },
                onCopy = {
                    val text = selection.selectedLines().joinToString("\n\n", transform = LyricShareLine::asParagraph)
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(title, text))
                    onDismiss()
                },
                onLoop = onLoop,
            )
        }
    }
}

@Composable
private fun LyricSelectionHeader(title: String, artist: String, cover: Bitmap?, selection: LyricSelection) {
    val colors = MiuixTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(colors.secondaryVariant)) {
            if (cover != null && !cover.isRecycled) {
                Image(cover.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.isNotBlank()) {
                Text(artist, style = MiuixTheme.textStyles.body2, color = colors.onSurfaceVariantSummary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.lyrics_share_selected, selection.indices.size, LyricShareLineLimit),
                style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            Text(stringResource(R.string.lyrics_share_count, selection.characterCount, LyricShareCharacterLimit),
                style = MiuixTheme.textStyles.body2,
                color = if (selection.characterCount > LyricShareCharacterLimit) colors.primary else colors.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun LyricSelectionCards(
    selection: LyricSelection,
    appearance: PlaybackCoverAppearance?,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialIndex = selection.initialIndex
    val list = rememberLazyListState(initialIndex)
    LaunchedEffect(list, initialIndex) {
        val layout = snapshotFlow { list.layoutInfo }.first { info ->
            info.visibleItemsInfo.any { it.index == initialIndex }
        }
        val card = layout.visibleItemsInfo.first { it.index == initialIndex }
        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        list.scrollBy((card.offset + card.size / 2 - center).toFloat())
    }
    val colors = MiuixTheme.colorScheme
    val selectedColor = remember(appearance, colors.primary) {
        val swatch = mostSaturatedPlaybackColor(appearance?.colors)
        val hsl = FloatArray(3)
        if (swatch != null) ColorUtils.colorToHSL(swatch, hsl)
        if (hsl[1] < 0.1f) {
            colors.primary
        } else {
            hsl[1] = hsl[1].coerceAtLeast(0.55f)
            hsl[2] = hsl[2].coerceIn(0.30f, 0.68f)
            Color(ColorUtils.HSLToColor(hsl))
        }
    }
    val selectedText = if (selectedColor.luminance() > 0.179f) Color.Black else Color.White
    BoxWithConstraints(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(vertical = (maxHeight / 2 - 20.dp).coerceAtLeast(4.dp)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(selection.cards.size) { index ->
                val card = selection.cards[index]
                val selected = index in selection.indices
                val blocked = !selected && selection.limitWhenAdding(index) != null
                val text = when {
                    selected -> selectedText
                    blocked -> colors.onSurfaceVariantSummary
                    else -> colors.onSurface
                }
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) selectedColor else colors.onSurface.copy(alpha = 0.08f))
                        .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(index) })
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(card.original, style = MiuixTheme.textStyles.body1, color = text, fontWeight = FontWeight.SemiBold)
                    card.translation?.let {
                        Text(it, style = MiuixTheme.textStyles.body2, color = text.copy(alpha = if (selected) 0.90f else 0.74f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricSelectionActions(
    exporting: Boolean,
    canExport: Boolean,
    canCopy: Boolean,
    loopRange: LyricLoopRange?,
    compactLandscape: Boolean = false,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onLoop: (LyricLoopRange) -> Unit,
) {
    if (compactLandscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = stringResource(if (exporting) R.string.lyrics_share_rendering else R.string.lyrics_share_image),
                onClick = onShare,
                enabled = !exporting && canExport,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
            TextButton(stringResource(R.string.lyrics_share_save), onSave, Modifier.weight(1f), enabled = !exporting && canExport)
            TextButton(stringResource(R.string.lyrics_share_copy), onCopy, Modifier.weight(1f), enabled = !exporting && canCopy)
            TextButton(
                text = stringResource(R.string.lyrics_share_loop),
                onClick = { loopRange?.let(onLoop) },
                enabled = !exporting && loopRange != null,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = stringResource(if (exporting) R.string.lyrics_share_rendering else R.string.lyrics_share_image),
                onClick = onShare,
                enabled = !exporting && canExport,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
            TextButton(stringResource(R.string.lyrics_share_save), onSave, Modifier.weight(1f), enabled = !exporting && canExport)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(stringResource(R.string.lyrics_share_copy), onCopy, Modifier.weight(1f), enabled = !exporting && canCopy)
            TextButton(
                text = stringResource(R.string.lyrics_share_loop),
                onClick = { loopRange?.let(onLoop) },
                enabled = !exporting && loopRange != null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
