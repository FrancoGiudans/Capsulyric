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

package com.example.islandlyrics.feature.onlinelyricdebug.material

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import com.example.islandlyrics.ui.material.blur.MaterialBlurAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugViewModel
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLyricDebugScreen(
    onBack: () -> Unit,
    viewModel: OnlineLyricDebugViewModel = viewModel()
) {
    val context = LocalContext.current
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)
    val mediaInfo by viewModel.liveMetadata.observeAsState()
    val albumArt by viewModel.liveAlbumArt.observeAsState()
    val liveProgress by viewModel.liveProgress.observeAsState()
    val liveLyric by viewModel.liveLyric.observeAsState()
    val parsedLyrics by viewModel.liveParsedLyrics.observeAsState()
    val isFetching by viewModel.isFetching.observeAsState(false)
    val selectedResult by viewModel.selectedResult.observeAsState()
    val selectedMainResult by viewModel.selectedMainResult.observeAsState()
    val selectedTranslationResult by viewModel.selectedTranslationResult.observeAsState()
    val selectedRomanResult by viewModel.selectedRomanResult.observeAsState()
    val attempts by viewModel.attempts.observeAsState(emptyList())
    val dialogAttempt by viewModel.dialogAttempt.observeAsState()
    val error by viewModel.error.observeAsState()
    val customMatchTitle by viewModel.customMatchTitle.observeAsState("")
    val customMatchArtist by viewModel.customMatchArtist.observeAsState("")
    val isInstrumental by viewModel.isInstrumental.observeAsState(false)
    val isAlbumInstrumental by viewModel.isAlbumInstrumental.observeAsState(false)

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogText by remember { mutableStateOf("") }
    var dialogResult by remember { mutableStateOf<OnlineLyricFetcher.LyricResult?>(null) }
    var dialogRole by remember { mutableStateOf<OnlineLyricDebugViewModel.ResultRole?>(null) }

    LaunchedEffect(mediaInfo?.packageName, mediaInfo?.title, mediaInfo?.artist, mediaInfo?.album) {
        if (mediaInfo != null) {
            viewModel.syncProviderOrderFromCurrentRule()
            viewModel.syncCurrentSongQuery()
        }
    }

    val currentFullLyrics = remember(parsedLyrics) {
        viewModel.parsedLyricsText(parsedLyrics?.lines)
    }
    val rematchedLyrics = remember(selectedResult) {
        viewModel.resultLyricsText(selectedResult)
    }
    val duration = liveProgress?.duration?.takeIf { it > 0 } ?: mediaInfo?.duration ?: 0L
    val currentFullLyricsTitle = stringResource(R.string.online_lyric_rematch_current_full_lyrics)
    val resultFullLyricsTitle = stringResource(R.string.online_lyric_rematch_result_full_lyrics)
    val mainCandidates = viewModel.groupAttemptsByProvider(
        attempts = attempts,
        role = OnlineLyricDebugViewModel.ResultRole.MAIN,
        selectedResult = selectedMainResult
    )
    val translationCandidates = viewModel.groupAttemptsByProvider(
        attempts = attempts,
        role = OnlineLyricDebugViewModel.ResultRole.TRANSLATION,
        selectedResult = selectedTranslationResult
    )
    val romanizationCandidates = viewModel.groupAttemptsByProvider(
        attempts = attempts,
        role = OnlineLyricDebugViewModel.ResultRole.ROMANIZATION,
        selectedResult = selectedRomanResult
    )
    val hasOtherResults = mainCandidates.isNotEmpty() ||
        translationCandidates.isNotEmpty() || selectedTranslationResult != null ||
        romanizationCandidates.isNotEmpty() || selectedRomanResult != null

    MaterialBlurScaffold(
        topBar = {
            MaterialBlurTopAppBar(
                title = { Text(stringResource(R.string.online_lyric_rematch_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.online_lyric_debug_back)
                        )
                    }
                },
            )
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                bottom = padding.calculateBottomPadding() + 24.dp,
            )
        ) {
            item {
                SettingsSectionHeader(
                    text = stringResource(R.string.online_lyric_rematch_current_playback),
                    marginTop = 0.dp
                )
            }
            item {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = currentFullLyrics.isNotBlank() || !liveLyric?.lyric.isNullOrBlank()) {
                                dialogTitle = currentFullLyricsTitle
                                dialogText = currentFullLyrics.ifBlank { liveLyric?.lyric.orEmpty() }
                                dialogResult = null
                                dialogRole = null
                            }
                            .padding(16.dp)
                    ) {
                        CurrentPlaybackContent(
                            albumArt = albumArt,
                            title = mediaInfo?.title.orEmpty(),
                            artist = mediaInfo?.artist.orEmpty(),
                            album = mediaInfo?.album.orEmpty(),
                            duration = duration,
                            currentLyric = liveLyric?.lyric.orEmpty()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.rematchWithCurrentPlayback() },
                            enabled = !isFetching && !offlineModeEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.online_lyric_rematch_current_playback_action))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    if (isInstrumental) {
                                        viewModel.clearCurrentSongInstrumentalMarker()
                                    } else {
                                        viewModel.markCurrentSongInstrumental()
                                    }
                                },
                                enabled = !isFetching,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(
                                        if (isInstrumental) {
                                            R.string.online_lyric_rematch_clear_instrumental
                                        } else {
                                            R.string.online_lyric_rematch_mark_instrumental
                                        }
                                    )
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (isAlbumInstrumental) {
                                        viewModel.clearCurrentAlbumInstrumentalMarker()
                                    } else {
                                        viewModel.markCurrentAlbumInstrumental()
                                    }
                                },
                                enabled = !isFetching && !mediaInfo?.album.isNullOrBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(
                                        if (isAlbumInstrumental) {
                                            R.string.online_lyric_rematch_clear_album_instrumental
                                        } else {
                                            R.string.online_lyric_rematch_mark_album_instrumental
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.online_lyric_rematch_match_input)) }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = customMatchTitle,
                            onValueChange = viewModel::updateCustomMatchTitle,
                            label = { Text(stringResource(R.string.online_lyric_rematch_song_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customMatchArtist,
                            onValueChange = viewModel::updateCustomMatchArtist,
                            label = { Text(stringResource(R.string.online_lyric_rematch_artist)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.rematchLyrics() },
                            enabled = !isFetching && !offlineModeEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.online_lyric_rematch_action))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.rematchSidecarLyrics() },
                            enabled = !isFetching && !offlineModeEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.online_lyric_rematch_sidecar_action))
                        }
                        error?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            if (selectedResult != null && rematchedLyrics.isNotBlank()) {
                item { SettingsSectionHeader(text = stringResource(R.string.online_lyric_rematch_result_title)) }
                item {
                    SettingsCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    dialogTitle = resultFullLyricsTitle
                                    dialogText = rematchedLyrics
                                    dialogResult = selectedResult
                                    dialogRole = null
                                }
                                .padding(16.dp)
                        ) {
                            Text(
                                text = selectedResult?.provider?.displayName(context).orEmpty(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = rematchedLyrics,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (hasOtherResults) {
                item { SettingsSectionHeader(text = stringResource(R.string.online_lyric_rematch_other_results)) }
                item {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            CandidateSection(
                                title = stringResource(R.string.online_lyric_debug_main_candidates),
                                attempts = mainCandidates,
                                selectedResult = selectedMainResult,
                                preview = { viewModel.resultLyricsText(it) },
                                onOpen = {
                                    dialogRole = OnlineLyricDebugViewModel.ResultRole.MAIN
                                    viewModel.openAttempt(it)
                                }
                            )
                            CandidateSection(
                                title = stringResource(R.string.online_lyric_debug_translation_candidates),
                                attempts = translationCandidates,
                                selectedResult = selectedTranslationResult,
                                preview = { viewModel.resultTranslationText(it) },
                                clearTitle = stringResource(R.string.online_lyric_debug_no_translation_match),
                                onClear = {
                                    viewModel.clearSidecarForRole(OnlineLyricDebugViewModel.ResultRole.TRANSLATION)
                                },
                                onOpen = {
                                    dialogRole = OnlineLyricDebugViewModel.ResultRole.TRANSLATION
                                    viewModel.openAttempt(it)
                                }
                            )
                            CandidateSection(
                                title = stringResource(R.string.online_lyric_debug_roman_candidates),
                                attempts = romanizationCandidates,
                                selectedResult = selectedRomanResult,
                                preview = { viewModel.resultRomanText(it) },
                                clearTitle = stringResource(R.string.online_lyric_debug_no_romanization_match),
                                onClear = {
                                    viewModel.clearSidecarForRole(OnlineLyricDebugViewModel.ResultRole.ROMANIZATION)
                                },
                                onOpen = {
                                    dialogRole = OnlineLyricDebugViewModel.ResultRole.ROMANIZATION
                                    viewModel.openAttempt(it)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (dialogTitle != null) {
        FullLyricsDialog(
            title = dialogTitle.orEmpty(),
            text = dialogText.ifBlank { stringResource(R.string.online_lyric_rematch_no_lyrics) },
            translationText = viewModel.resultTranslationText(dialogResult),
            romanText = viewModel.resultRomanText(dialogResult),
            onDismiss = {
                dialogTitle = null
                dialogResult = null
                dialogRole = null
            }
        )
    }

    dialogAttempt?.let { attempt ->
        val role = dialogRole ?: OnlineLyricDebugViewModel.ResultRole.MAIN
        AttemptResultDialog(
            attempt = attempt,
            text = viewModel.resultLyricsText(attempt.result),
            translationText = viewModel.resultTranslationText(attempt.result),
            romanText = viewModel.resultRomanText(attempt.result),
            canSelect = viewModel.canUseAttemptForRole(attempt, role),
            selectLabel = when (role) {
                OnlineLyricDebugViewModel.ResultRole.MAIN -> stringResource(R.string.online_lyric_debug_use_as_main)
                OnlineLyricDebugViewModel.ResultRole.TRANSLATION -> stringResource(R.string.online_lyric_debug_use_as_translation)
                OnlineLyricDebugViewModel.ResultRole.ROMANIZATION -> stringResource(R.string.online_lyric_debug_use_as_romanization)
            },
            isFetching = isFetching,
            onSelect = {
                viewModel.selectAttemptForRole(
                    role,
                    attempt
                )
            },
            onDismiss = {
                dialogRole = null
                viewModel.closeDialog()
            }
        )
    }

}

@Composable
private fun CurrentPlaybackContent(
    albumArt: Bitmap?,
    title: String,
    artist: String,
    album: String,
    duration: Long,
    currentLyric: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (albumArt != null) {
                Image(
                    bitmap = albumArt.asImageBitmap(),
                    contentDescription = stringResource(R.string.main_album_art_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { stringResource(R.string.media_control_unknown_title) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist.ifBlank { stringResource(R.string.media_control_unknown_artist) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.ifBlank { stringResource(R.string.online_lyric_rematch_unknown_album) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.online_lyric_rematch_duration_fmt, formatTime(duration)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.online_lyric_rematch_current_line),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = currentLyric.ifBlank { stringResource(R.string.online_lyric_rematch_no_lyrics) },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun CandidateSection(
    title: String,
    attempts: List<OnlineLyricFetcher.ProviderAttempt>,
    selectedResult: OnlineLyricFetcher.LyricResult?,
    preview: (OnlineLyricFetcher.LyricResult?) -> String,
    clearTitle: String? = null,
    onClear: (() -> Unit)? = null,
    onOpen: (OnlineLyricFetcher.ProviderAttempt) -> Unit
) {
    val context = LocalContext.current
    val hasClearAction = selectedResult != null && clearTitle != null && onClear != null
    if (attempts.isEmpty() && !hasClearAction) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
    if (hasClearAction) {
        SourceResultRow(
            title = clearTitle.orEmpty(),
            preview = "",
            enabled = true,
            selected = false,
            onClick = onClear!!
        )
    }
    attempts.forEach { attempt ->
        val result = attempt.result
        val selected = result != null && result == selectedResult
        SourceResultRow(
            title = attempt.provider.displayName(context),
            preview = preview(result),
            enabled = true,
            selected = selected,
            onClick = { onOpen(attempt) }
        )
    }
}

@Composable
private fun SourceResultRow(
    title: String,
    preview: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FullLyricsDialog(
    title: String,
    text: String,
    translationText: String,
    romanText: String,
    onDismiss: () -> Unit
) {
    MaterialBlurAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ResultTextSections(
                mainText = text,
                translationText = translationText,
                romanText = romanText,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 420.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.online_lyric_debug_close))
            }
        }
    )
}

@Composable
private fun AttemptResultDialog(
    attempt: OnlineLyricFetcher.ProviderAttempt,
    text: String,
    translationText: String,
    romanText: String,
    canSelect: Boolean,
    selectLabel: String,
    isFetching: Boolean,
    onSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    MaterialBlurAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(attempt.provider.displayName(LocalContext.current)) },
        text = {
            val bodyText = attempt.result?.error?.let {
                stringResource(R.string.online_lyric_debug_error_fmt, it)
            } ?: text.ifBlank { stringResource(R.string.online_lyric_rematch_no_lyrics) }
            ResultTextSections(
                mainText = bodyText,
                translationText = if (attempt.result?.error == null) translationText else "",
                romanText = if (attempt.result?.error == null) romanText else "",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 420.dp)
            )
        },
        confirmButton = {
            if (canSelect) {
                TextButton(onClick = onSelect, enabled = !isFetching) {
                    Text(selectLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.online_lyric_debug_close))
            }
        }
    )
}

@Composable
private fun ResultTextSections(
    mainText: String,
    translationText: String,
    romanText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        ResultTextSection(
            title = stringResource(R.string.online_lyric_debug_result_main_lyrics),
            text = mainText
        )
        if (translationText.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            ResultTextSection(
                title = stringResource(R.string.online_lyric_debug_result_translation),
                text = translationText
            )
        }
        if (romanText.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            ResultTextSection(
                title = stringResource(R.string.online_lyric_debug_result_romanization),
                text = romanText
            )
        }
    }
}

@Composable
private fun ResultTextSection(title: String, text: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = text)
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
