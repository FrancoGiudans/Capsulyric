package com.example.islandlyrics.feature.onlinelyricdebug.miuix

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixOnlineLyricDebugScreen(
    onBack: () -> Unit,
    viewModel: OnlineLyricDebugViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val mediaInfo by viewModel.liveMetadata.observeAsState()
    val albumArt by viewModel.liveAlbumArt.observeAsState()
    val liveProgress by viewModel.liveProgress.observeAsState()
    val liveLyric by viewModel.liveLyric.observeAsState()
    val parsedLyrics by viewModel.liveParsedLyrics.observeAsState()
    val isFetching by viewModel.isFetching.observeAsState(false)
    val selectedResult by viewModel.selectedResult.observeAsState()
    val attempts by viewModel.attempts.observeAsState(emptyList())
    val dialogAttempt by viewModel.dialogAttempt.observeAsState()
    val error by viewModel.error.observeAsState()
    val customMatchTitle by viewModel.customMatchTitle.observeAsState("")
    val customMatchArtist by viewModel.customMatchArtist.observeAsState("")
    val effectiveQuery by viewModel.effectiveQuery.observeAsState("" to "")
    val querySourceLabel by viewModel.querySourceLabel.observeAsState("")
    val cacheStatus by viewModel.cacheStatus.observeAsState()

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogText by remember { mutableStateOf("") }

    LaunchedEffect(mediaInfo?.packageName, mediaInfo?.title, mediaInfo?.artist) {
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
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        CurrentPlaybackContent(
                            albumArt = albumArt,
                            title = mediaInfo?.title.orEmpty(),
                            artist = mediaInfo?.artist.orEmpty(),
                            duration = duration,
                            currentLyric = liveLyric?.lyric.orEmpty()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.rematchWithCurrentPlayback() },
                            enabled = !isFetching,
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
                            enabled = !isFetching,
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
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (selectedResult != null) {
                            Text(
                                stringResource(R.string.online_lyric_rematch_result_source_fmt, selectedResult?.api.orEmpty()),
                                color = MiuixTheme.colorScheme.primary
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
                            attempts.forEach { attempt ->
                                val result = attempt.result
                                val preview = viewModel.resultLyricsText(result)
                                SourceResultRow(
                                    title = attempt.provider.displayName(context),
                                    subtitle = when {
                                        result == null -> stringResource(R.string.online_lyric_debug_no_result)
                                        result.error != null -> stringResource(R.string.online_lyric_debug_error_fmt, result.error)
                                        result == selectedResult -> stringResource(R.string.online_lyric_rematch_selected_result)
                                        else -> stringResource(R.string.online_lyric_rematch_available_result)
                                    },
                                    preview = preview.ifBlank { result?.error.orEmpty() },
                                    enabled = result != null,
                                    selected = result == selectedResult,
                                    onClick = { viewModel.openAttempt(attempt) }
                                )
                            }
                        }
                    }
                }
            }
        }

        dialogTitle?.let { title ->
            MiuixBlurDialog(
                title = title,
                show = true,
                onDismissRequest = { dialogTitle = null }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = dialogText.ifBlank { stringResource(R.string.online_lyric_rematch_no_lyrics) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { dialogTitle = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.online_lyric_debug_close))
                    }
                }
            }
        }

        dialogAttempt?.let { attempt ->
            AttemptResultDialog(
                attempt = attempt,
                text = viewModel.resultLyricsText(attempt.result),
                canSelect = attempt.result?.let { it.error == null && !it.parsedLines.isNullOrEmpty() } == true,
                isFetching = isFetching,
                onSelect = { viewModel.selectAttempt(attempt) },
                onDismiss = { viewModel.closeDialog() }
            )
        }
    }
}

@Composable
private fun CurrentPlaybackContent(
    albumArt: Bitmap?,
    title: String,
    artist: String,
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
private fun SourceResultRow(
    title: String,
    subtitle: String,
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
private fun AttemptResultDialog(
    attempt: OnlineLyricFetcher.ProviderAttempt,
    text: String,
    canSelect: Boolean,
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
            Text(
                text = attempt.result?.error?.let {
                    stringResource(R.string.online_lyric_debug_error_fmt, it)
                } ?: text.ifBlank { stringResource(R.string.online_lyric_debug_no_result) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                color = MiuixTheme.colorScheme.onSurface
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
                        Text(stringResource(R.string.online_lyric_debug_select_result))
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.online_lyric_debug_close))
                }
            }
        }
    }
}


