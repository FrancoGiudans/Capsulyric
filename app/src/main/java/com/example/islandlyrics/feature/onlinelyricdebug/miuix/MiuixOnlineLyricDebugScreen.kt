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

package com.example.islandlyrics.feature.onlinelyricdebug.miuix

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugViewModel
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.effects.miuixPageScroll
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixOnlineLyricDebugScreen(
    onBack: () -> Unit,
    viewModel: OnlineLyricDebugViewModel = viewModel()
) {
    val context = LocalContext.current
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
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
    val effectiveQuery by viewModel.effectiveQuery.observeAsState("" to "")
    val querySourceLabel by viewModel.querySourceLabel.observeAsState("")
    val cacheStatus by viewModel.cacheStatus.observeAsState()
    val isCurrentSelectionFromCache by viewModel.isCurrentSelectionFromCache.observeAsState(false)
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

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.online_lyric_rematch_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        MiuixBackIcon(contentDescription = stringResource(R.string.online_lyric_debug_back))
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
            item { SmallTitle(text = stringResource(R.string.online_lyric_rematch_current_playback)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clickable(enabled = currentFullLyrics.isNotBlank() || !liveLyric?.lyric.isNullOrBlank()) {
                            dialogTitle = currentFullLyricsTitle
                            dialogText = currentFullLyrics.ifBlank { liveLyric?.lyric.orEmpty() }
                            dialogResult = null
                            dialogRole = null
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.online_lyric_rematch_current_playback_action))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
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
                            Button(
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

            item { SmallTitle(text = stringResource(R.string.online_lyric_rematch_match_input)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TextField(
                            value = customMatchTitle,
                            onValueChange = viewModel::updateCustomMatchTitle,
                            label = stringResource(R.string.online_lyric_rematch_song_title),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = customMatchArtist,
                            onValueChange = viewModel::updateCustomMatchArtist,
                            label = stringResource(R.string.online_lyric_rematch_artist),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.rematchLyrics() },
                            enabled = !isFetching && !offlineModeEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                if (isFetching) {
                                    stringResource(R.string.online_lyric_debug_fetching)
                                } else {
                                    stringResource(R.string.online_lyric_rematch_action)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.rematchSidecarLyrics() },
                            enabled = !isFetching && !offlineModeEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.online_lyric_rematch_sidecar_action))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(
                                R.string.online_lyric_rematch_effective_query_fmt,
                                effectiveQuery.first.ifBlank { stringResource(R.string.online_lyric_debug_none) },
                                effectiveQuery.second.ifBlank { stringResource(R.string.online_lyric_debug_none) }
                            ),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        if (querySourceLabel.isNotBlank()) {
                            Text(querySourceLabel, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                        cacheStatus?.let {
                            Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                        }
                        error?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, color = MiuixTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { SmallTitle(text = stringResource(R.string.online_lyric_rematch_result_title)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clickable(enabled = rematchedLyrics.isNotBlank()) {
                            dialogTitle = resultFullLyricsTitle
                            dialogText = rematchedLyrics
                            dialogResult = selectedResult
                            dialogRole = null
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (selectedResult != null) {
                            Text(
                                stringResource(R.string.online_lyric_rematch_result_source_fmt, selectedResult?.api.orEmpty()),
                                color = MiuixTheme.colorScheme.primary
                            )
                            if (isCurrentSelectionFromCache) {
                                Text(
                                    text = stringResource(R.string.online_lyric_debug_cache_hit),
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                            ResultBadges(
                                labels = resultBadges(
                                    result = selectedResult,
                                    attempt = null,
                                    selected = true
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = rematchedLyrics.ifBlank { stringResource(R.string.online_lyric_rematch_no_result) },
                            color = if (rematchedLyrics.isBlank()) {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            },
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item { SmallTitle(text = stringResource(R.string.online_lyric_rematch_other_results)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (attempts.isEmpty()) {
                            Text(
                                text = stringResource(R.string.online_lyric_rematch_no_other_results),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        } else {
                            CandidateSection(
                                title = stringResource(R.string.online_lyric_debug_main_candidates),
                                attempts = attempts,
                                selectedResult = selectedMainResult,
                                canUse = { viewModel.canUseAttemptForRole(it, OnlineLyricDebugViewModel.ResultRole.MAIN) },
                                preview = { viewModel.resultLyricsText(it) },
                                emptyText = stringResource(R.string.online_lyric_debug_no_main_candidates),
                                onOpen = {
                                    dialogRole = OnlineLyricDebugViewModel.ResultRole.MAIN
                                    viewModel.openAttempt(it)
                                }
                            )
                            CandidateSection(
                                title = stringResource(R.string.online_lyric_debug_translation_candidates),
                                attempts = attempts,
                                selectedResult = selectedTranslationResult,
                                canUse = { viewModel.canUseAttemptForRole(it, OnlineLyricDebugViewModel.ResultRole.TRANSLATION) },
                                preview = { viewModel.resultTranslationText(it) },
                                emptyText = stringResource(R.string.online_lyric_debug_no_translation_candidates),
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
                                attempts = attempts,
                                selectedResult = selectedRomanResult,
                                canUse = { viewModel.canUseAttemptForRole(it, OnlineLyricDebugViewModel.ResultRole.ROMANIZATION) },
                                preview = { viewModel.resultRomanText(it) },
                                emptyText = stringResource(R.string.online_lyric_debug_no_roman_candidates),
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

        dialogTitle?.let { title ->
            MiuixBlurDialog(
                title = title,
                show = true,
                onDismissRequest = {
                    dialogTitle = null
                    dialogResult = null
                    dialogRole = null
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    ResultTextSections(
                        mainText = dialogText.ifBlank { stringResource(R.string.online_lyric_rematch_no_lyrics) },
                        translationText = viewModel.resultTranslationText(dialogResult),
                        romanText = viewModel.resultRomanText(dialogResult),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 420.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            dialogTitle = null
                            dialogResult = null
                            dialogRole = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.online_lyric_debug_close))
                    }
                }
            }
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
                .background(MiuixTheme.colorScheme.secondary),
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
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { stringResource(R.string.media_control_unknown_title) },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist.ifBlank { stringResource(R.string.media_control_unknown_artist) },
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.ifBlank { stringResource(R.string.online_lyric_rematch_unknown_album) },
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.online_lyric_rematch_duration_fmt, formatTime(duration)),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.online_lyric_rematch_current_line),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceSecondary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = currentLyric.ifBlank { stringResource(R.string.online_lyric_rematch_no_lyrics) },
        fontSize = 17.sp,
        color = MiuixTheme.colorScheme.primary
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun CandidateSection(
    title: String,
    attempts: List<OnlineLyricFetcher.ProviderAttempt>,
    selectedResult: OnlineLyricFetcher.LyricResult?,
    canUse: (OnlineLyricFetcher.ProviderAttempt) -> Boolean,
    preview: (OnlineLyricFetcher.LyricResult?) -> String,
    emptyText: String,
    clearTitle: String? = null,
    onClear: (() -> Unit)? = null,
    onOpen: (OnlineLyricFetcher.ProviderAttempt) -> Unit
) {
    val context = LocalContext.current
    val usableAttempts = attempts.filter(canUse)
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
    if (clearTitle != null && onClear != null) {
        SourceResultRow(
            title = clearTitle,
            subtitle = if (selectedResult == null) {
                stringResource(R.string.online_lyric_rematch_selected_result)
            } else {
                stringResource(R.string.online_lyric_debug_no_sidecar_summary)
            },
            badges = emptyList(),
            preview = "",
            enabled = true,
            selected = selectedResult == null,
            onClick = onClear
        )
    }
    if (usableAttempts.isEmpty()) {
        Text(
            text = emptyText,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        return
    }
    usableAttempts.forEach { attempt ->
        val result = attempt.result
        val selected = result == selectedResult
        SourceResultRow(
            title = attempt.provider.displayName(context),
            subtitle = if (selected) {
                stringResource(R.string.online_lyric_rematch_selected_result)
            } else {
                stringResource(R.string.online_lyric_rematch_available_result)
            },
            badges = resultBadges(
                result = result,
                attempt = attempt,
                selected = selected
            ),
            preview = preview(result),
            enabled = result != null,
            selected = selected,
            onClick = { onOpen(attempt) }
        )
    }
}

@Composable
private fun SourceResultRow(
    title: String,
    subtitle: String,
    badges: List<String>,
    preview: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp)
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.primary
        )
        ResultBadges(labels = badges)
        if (preview.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = preview,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun resultBadges(
    result: OnlineLyricFetcher.LyricResult?,
    attempt: OnlineLyricFetcher.ProviderAttempt?,
    selected: Boolean
): List<String> {
    val labels = mutableListOf<String>()
    if (selected) {
        labels += stringResource(R.string.online_lyric_rematch_selected_result)
    }
    if (result != null && result.error == null && !result.parsedLines.isNullOrEmpty()) {
        labels += if (result.hasSyllable || result.parsedLines.orEmpty().any { !it.syllables.isNullOrEmpty() }) {
            stringResource(R.string.online_lyric_debug_result_syllable)
        } else {
            stringResource(R.string.online_lyric_debug_result_lrc_or_text)
        }
        if (!result.translationLyrics.isNullOrBlank()) {
            labels += stringResource(R.string.online_lyric_debug_result_translation)
        }
        if (!result.romanLyrics.isNullOrBlank()) {
            labels += stringResource(R.string.online_lyric_debug_result_romanization)
        }
    }
    if (attempt?.usedCleanTitleFallback == true) {
        labels += stringResource(R.string.online_lyric_debug_clean_title_badge)
    }
    return labels
}

@Composable
private fun ResultBadges(labels: List<String>) {
    if (labels.isEmpty()) return
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .background(
                        color = MiuixTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
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
    MiuixBlurDialog(
        title = attempt.provider.displayName(LocalContext.current),
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val bodyText = attempt.result?.error?.let {
                    stringResource(R.string.online_lyric_debug_error_fmt, it)
                } ?: text.ifBlank { stringResource(R.string.online_lyric_debug_no_result) }
            ResultTextSections(
                mainText = bodyText,
                translationText = if (attempt.result?.error == null) translationText else "",
                romanText = if (attempt.result?.error == null) romanText else "",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 420.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (canSelect) {
                    Button(
                    onClick = onSelect,
                    enabled = !isFetching,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(selectLabel)
                }
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.online_lyric_debug_close))
                }
            }
        }
    }
}

@Composable
private fun ResultTextSections(
    mainText: String,
    translationText: String,
    romanText: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        if (mainText.isNotBlank()) {
            ResultTextSection(
                title = stringResource(R.string.online_lyric_debug_result_main_lyrics),
                text = mainText
            )
        }
        if (translationText.isNotBlank()) {
            if (mainText.isNotBlank()) Spacer(modifier = Modifier.height(14.dp))
            ResultTextSection(
                title = stringResource(R.string.online_lyric_debug_result_translation),
                text = translationText
            )
        }
        if (romanText.isNotBlank()) {
            if (mainText.isNotBlank() || translationText.isNotBlank()) Spacer(modifier = Modifier.height(14.dp))
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
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurface
    )
}


