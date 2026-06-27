package com.example.islandlyrics.feature.onlinelyricdebug.material

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugViewModel
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLyricDebugScreen(
    onBack: () -> Unit,
    viewModel: OnlineLyricDebugViewModel = viewModel()
) {
    val context = LocalContext.current
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.online_lyric_rematch_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.online_lyric_debug_back)
                        )
                    }
                },
                colors = neutralMaterialTopBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
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
                            }
                            .padding(16.dp)
                    ) {
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
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.online_lyric_rematch_current_playback_action))
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
                            enabled = !isFetching,
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
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(
                                R.string.online_lyric_rematch_effective_query_fmt,
                                effectiveQuery.first.ifBlank { stringResource(R.string.online_lyric_debug_none) },
                                effectiveQuery.second.ifBlank { stringResource(R.string.online_lyric_debug_none) }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (querySourceLabel.isNotBlank()) {
                            Text(
                                text = querySourceLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        cacheStatus?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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

            item { SettingsSectionHeader(text = stringResource(R.string.online_lyric_rematch_result_title)) }
            item {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = rematchedLyrics.isNotBlank()) {
                                dialogTitle = resultFullLyricsTitle
                                dialogText = rematchedLyrics
                            }
                            .padding(16.dp)
                    ) {
                        if (selectedResult != null) {
                            Text(
                                text = stringResource(R.string.online_lyric_rematch_result_source_fmt, selectedResult?.api.orEmpty()),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = rematchedLyrics.ifBlank { stringResource(R.string.online_lyric_rematch_no_result) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rematchedLyrics.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.online_lyric_rematch_other_results)) }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (attempts.isEmpty()) {
                            Text(
                                text = stringResource(R.string.online_lyric_rematch_no_other_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }

    if (dialogTitle != null) {
        FullLyricsDialog(
            title = dialogTitle.orEmpty(),
            text = dialogText.ifBlank { stringResource(R.string.online_lyric_rematch_no_lyrics) },
            onDismiss = { dialogTitle = null }
        )
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
private fun SourceResultRow(
    title: String,
    subtitle: String,
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
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState())
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
    canSelect: Boolean,
    isFetching: Boolean,
    onSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(attempt.provider.displayName(LocalContext.current)) },
        text = {
            Text(
                text = attempt.result?.error?.let {
                    stringResource(R.string.online_lyric_debug_error_fmt, it)
                } ?: text.ifBlank { stringResource(R.string.online_lyric_debug_no_result) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            if (canSelect) {
                TextButton(onClick = onSelect, enabled = !isFetching) {
                    Text(stringResource(R.string.online_lyric_debug_select_result))
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

private fun formatTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
