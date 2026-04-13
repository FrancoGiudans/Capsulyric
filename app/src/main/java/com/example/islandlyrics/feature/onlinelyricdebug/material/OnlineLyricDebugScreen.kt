package com.example.islandlyrics.feature.onlinelyricdebug.material

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugViewModel
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLyricDebugScreen(
    onBack: () -> Unit,
    viewModel: OnlineLyricDebugViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val mediaInfo by viewModel.liveMetadata.observeAsState()
    val liveProgress by viewModel.liveProgress.observeAsState()
    val liveLyric by viewModel.liveLyric.observeAsState()
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val isFetching by viewModel.isFetching.observeAsState(false)
    val attempts by viewModel.attempts.observeAsState(emptyList())
    val selectedResult by viewModel.selectedResult.observeAsState()
    val error by viewModel.error.observeAsState()
    val providerOrder by viewModel.providerOrder.observeAsState(OnlineLyricProvider.defaultOrder())
    val useSmartSelection by viewModel.useSmartSelection.observeAsState(true)
    val usedCleanTitleFallback by viewModel.usedCleanTitleFallback.observeAsState(false)
    val dialogAttempt by viewModel.dialogAttempt.observeAsState()
    val customMatchTitle by viewModel.customMatchTitle.observeAsState("")
    val customMatchArtist by viewModel.customMatchArtist.observeAsState("")
    val effectiveQuery by viewModel.effectiveQuery.observeAsState("" to "")
    val querySourceLabel by viewModel.querySourceLabel.observeAsState("")
    val cacheStatus by viewModel.cacheStatus.observeAsState()
    val canSelectDialogResult = dialogAttempt?.result?.let { result ->
        result.error == null && !result.parsedLines.isNullOrEmpty()
    } == true
    val showPrioritySection = remember(mediaInfo?.packageName, useSmartSelection) {
        val pkg = mediaInfo?.packageName
        pkg != null &&
            (ParserRuleHelper.getRuleForPackage(context, pkg)?.useOnlineLyrics == true) &&
            !useSmartSelection
    }

    LaunchedEffect(mediaInfo?.packageName) {
        if (mediaInfo?.packageName != null) {
            viewModel.syncProviderOrderFromCurrentRule()
            viewModel.syncCurrentSongQuery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.online_lyric_debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.online_lyric_debug_back))
                    }
                },
                colors = neutralMaterialTopBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(1.dp))
            DebugInfoCard(title = stringResource(R.string.online_lyric_debug_now_playing)) {
                Text(stringResource(R.string.online_lyric_debug_song_fmt, mediaInfo?.title ?: stringResource(R.string.online_lyric_debug_none)))
                Text(stringResource(R.string.online_lyric_debug_artist_fmt, mediaInfo?.artist ?: stringResource(R.string.online_lyric_debug_none)))
                Text(stringResource(R.string.online_lyric_debug_match_title_fmt, effectiveQuery.first.ifBlank { stringResource(R.string.online_lyric_debug_none) }))
                Text(stringResource(R.string.online_lyric_debug_match_artist_fmt, effectiveQuery.second.ifBlank { stringResource(R.string.online_lyric_debug_none) }))
                Text(querySourceLabel, color = MaterialTheme.colorScheme.secondary)
                cacheStatus?.let {
                    Text(it, color = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    stringResource(
                        R.string.online_lyric_debug_playback_time_fmt,
                        formatTime(liveProgress?.position ?: 0),
                        formatTime(liveProgress?.duration ?: 0)
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }

            DebugInfoCard(title = stringResource(R.string.online_lyric_debug_current_match)) {
                OutlinedTextField(
                    value = customMatchTitle,
                    onValueChange = viewModel::updateCustomMatchTitle,
                    label = { Text(stringResource(R.string.online_lyric_debug_custom_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customMatchArtist,
                    onValueChange = viewModel::updateCustomMatchArtist,
                    label = { Text(stringResource(R.string.online_lyric_debug_custom_artist)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveCurrentSongMatchOverride() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.online_lyric_debug_save))
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearCurrentSongMatchOverride() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.online_lyric_debug_clear_custom))
                    }
                }
            }

            DebugInfoCard(title = stringResource(R.string.online_lyric_debug_live_status)) {
                Text(
                    stringResource(
                        R.string.online_lyric_debug_lyric_source_fmt,
                        liveLyric?.apiPath ?: stringResource(R.string.online_lyric_debug_dash),
                        liveLyric?.sourceApp?.ifBlank { stringResource(R.string.online_lyric_debug_dash) } ?: stringResource(R.string.online_lyric_debug_dash)
                    )
                )
                Text(
                    stringResource(
                        R.string.online_lyric_debug_current_lyric_fmt,
                        liveLyric?.lyric?.ifBlank { stringResource(R.string.online_lyric_debug_empty) } ?: stringResource(R.string.online_lyric_debug_dash)
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    stringResource(R.string.online_lyric_debug_playing_app_fmt, mediaInfo?.packageName ?: stringResource(R.string.online_lyric_debug_dash)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    stringResource(
                        R.string.online_lyric_debug_play_state_fmt,
                        if (isPlaying) stringResource(R.string.online_lyric_debug_state_playing) else stringResource(R.string.online_lyric_debug_state_paused)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            if (showPrioritySection) {
                DebugInfoCard(title = stringResource(R.string.online_lyric_debug_priority)) {
                    providerOrder.forEachIndexed { index, provider ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${index + 1}. ${provider.displayName(context)}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.moveProvider(provider, -1) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.online_lyric_debug_move_up))
                            }
                            IconButton(onClick = { viewModel.moveProvider(provider, 1) }, enabled = index < providerOrder.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.online_lyric_debug_move_down))
                            }
                        }
                    }
                    TextButton(onClick = { viewModel.resetProviderOrder() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.online_lyric_debug_reset_order))
                    }
                }
            }

            DebugInfoCard(title = stringResource(R.string.online_lyric_debug_fetch)) {
                Button(onClick = { viewModel.fetchLyrics() }, enabled = !isFetching, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors()) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.online_lyric_debug_fetch_cached))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.fetchLyrics(forceRefresh = true) },
                    enabled = !isFetching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.online_lyric_debug_force_refresh))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (useSmartSelection) {
                        stringResource(R.string.online_lyric_debug_fetch_mode_smart)
                    } else {
                        stringResource(
                            R.string.online_lyric_debug_provider_order_fmt,
                            providerOrder.joinToString(" > ") { it.displayName(context) }
                        )
                    }
                )
                Text(
                    stringResource(
                        R.string.online_lyric_debug_title_fallback_fmt,
                        if (usedCleanTitleFallback) stringResource(R.string.online_lyric_debug_triggered) else stringResource(R.string.online_lyric_debug_not_triggered)
                    )
                )
                Text(
                    stringResource(
                        R.string.online_lyric_debug_effective_query_fmt,
                        effectiveQuery.first.ifBlank { stringResource(R.string.online_lyric_debug_none) },
                        effectiveQuery.second.ifBlank { stringResource(R.string.online_lyric_debug_none) }
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                attempts.forEach { attempt ->
                    val result = attempt.result
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = result != null) { viewModel.openAttempt(attempt) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (result == selectedResult) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                stringResource(
                                    R.string.online_lyric_debug_attempt_header_fmt,
                                    if (result == selectedResult) stringResource(R.string.online_lyric_debug_selected_prefix) else "",
                                    attempt.provider.displayName(context),
                                    attempt.durationMs
                                ),
                                fontWeight = FontWeight.SemiBold
                            )
                            if (attempt.usedCleanTitleFallback) {
                                Text(stringResource(R.string.online_lyric_debug_retry_clean_title), color = MaterialTheme.colorScheme.tertiary)
                            }
                            Text(
                                when {
                                    result == null -> stringResource(R.string.online_lyric_debug_no_result)
                                    result.error != null -> stringResource(R.string.online_lyric_debug_error_fmt, result.error)
                                    else -> stringResource(
                                        R.string.online_lyric_debug_result_summary_fmt,
                                        result.api,
                                        result.score,
                                        if (result.hasSyllable) stringResource(R.string.online_lyric_debug_result_syllable) else stringResource(R.string.online_lyric_debug_result_lrc_or_text)
                                    )
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (result != null && result.error == null) {
                                Text(stringResource(R.string.online_lyric_debug_tap_for_full_result), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.online_lyric_debug_back))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    dialogAttempt?.let { attempt ->
        val result = attempt.result
        AlertDialog(
            onDismissRequest = { viewModel.closeDialog() },
            title = { Text(stringResource(R.string.online_lyric_debug_attempt_title_fmt, attempt.provider.displayName(context))) },
            text = {
                Column {
                    Text(stringResource(R.string.online_lyric_debug_duration_fmt, attempt.durationMs))
                    if (attempt.usedCleanTitleFallback) {
                        Text(stringResource(R.string.online_lyric_debug_clean_title_used), color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result?.error?.let { stringResource(R.string.online_lyric_debug_error_fmt, it) }
                            ?: result?.lyrics
                            ?: stringResource(R.string.online_lyric_debug_no_result),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 360.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                if (canSelectDialogResult) {
                    TextButton(onClick = { viewModel.selectAttempt(attempt) }, enabled = !isFetching) {
                        Text(stringResource(R.string.online_lyric_debug_select_result))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDialog() }) {
                    Text(stringResource(R.string.online_lyric_debug_close))
                }
            }
        )
    }
}

@Composable
private fun DebugInfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
