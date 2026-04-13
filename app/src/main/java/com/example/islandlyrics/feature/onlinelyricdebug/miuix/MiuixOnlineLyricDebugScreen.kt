package com.example.islandlyrics.feature.onlinelyricdebug.miuix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.islandlyrics.R
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import com.example.islandlyrics.feature.onlinelyricdebug.OnlineLyricDebugViewModel
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.MiuixBlurTopAppBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixOnlineLyricDebugScreen(
    onBack: () -> Unit,
    viewModel: OnlineLyricDebugViewModel = viewModel()
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
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

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.online_lyric_debug_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.online_lyric_debug_back), tint = MiuixTheme.colorScheme.onBackground)
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 24.dp)
        ) {
            item { SmallTitle(text = stringResource(R.string.online_lyric_debug_now_playing)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.online_lyric_debug_song_fmt, mediaInfo?.title ?: stringResource(R.string.online_lyric_debug_none)))
                        Text(stringResource(R.string.online_lyric_debug_artist_fmt, mediaInfo?.artist ?: stringResource(R.string.online_lyric_debug_none)), color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text(stringResource(R.string.online_lyric_debug_match_title_fmt, effectiveQuery.first.ifBlank { stringResource(R.string.online_lyric_debug_none) }))
                        Text(stringResource(R.string.online_lyric_debug_match_artist_fmt, effectiveQuery.second.ifBlank { stringResource(R.string.online_lyric_debug_none) }))
                        Text(querySourceLabel, color = MiuixTheme.colorScheme.primary)
                        cacheStatus?.let {
                            Text(it, color = MiuixTheme.colorScheme.primary)
                        }
                        Text(
                            stringResource(
                                R.string.online_lyric_debug_playback_time_fmt,
                                formatTime(liveProgress?.position ?: 0),
                                formatTime(liveProgress?.duration ?: 0)
                            ),
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
            item { SmallTitle(text = stringResource(R.string.online_lyric_debug_current_match)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TextField(
                            value = customMatchTitle,
                            onValueChange = viewModel::updateCustomMatchTitle,
                            label = stringResource(R.string.online_lyric_debug_custom_title),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = customMatchArtist,
                            onValueChange = viewModel::updateCustomMatchArtist,
                            label = stringResource(R.string.online_lyric_debug_custom_artist),
                            modifier = Modifier.fillMaxWidth()
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
                            Button(
                                onClick = { viewModel.clearCurrentSongMatchOverride() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.online_lyric_debug_clear_custom))
                            }
                        }
                    }
                }
            }
            item { SmallTitle(text = stringResource(R.string.online_lyric_debug_live_status)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                            color = MiuixTheme.colorScheme.primary
                        )
                        Text(stringResource(R.string.online_lyric_debug_playing_app_fmt, mediaInfo?.packageName ?: stringResource(R.string.online_lyric_debug_dash)), color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text(
                            stringResource(
                                R.string.online_lyric_debug_play_state_fmt,
                                if (isPlaying) stringResource(R.string.online_lyric_debug_state_playing) else stringResource(R.string.online_lyric_debug_state_paused)
                            )
                        )
                    }
                }
            }
            if (showPrioritySection) {
                item { SmallTitle(text = stringResource(R.string.online_lyric_debug_priority)) }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            providerOrder.forEachIndexed { index, provider ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}. ${provider.displayName(context)}", modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.moveProvider(provider, -1) }, enabled = index > 0) {
                                        androidx.compose.material3.Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.online_lyric_debug_move_up))
                                    }
                                    IconButton(onClick = { viewModel.moveProvider(provider, 1) }, enabled = index < providerOrder.lastIndex) {
                                        androidx.compose.material3.Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.online_lyric_debug_move_down))
                                    }
                                }
                            }
                            Button(onClick = { viewModel.resetProviderOrder() }) {
                                androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.online_lyric_debug_reset_order))
                            }
                        }
                    }
                }
            }
            item { SmallTitle(text = stringResource(R.string.online_lyric_debug_fetch)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.fetchLyrics() }, enabled = !isFetching, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isFetching) stringResource(R.string.online_lyric_debug_fetching) else stringResource(R.string.online_lyric_debug_fetch_cached))
                        }
                        Button(
                            onClick = { viewModel.fetchLyrics(forceRefresh = true) },
                            enabled = !isFetching,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.online_lyric_debug_force_refresh))
                        }
                        error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
                        Text(
                            if (useSmartSelection) {
                                stringResource(R.string.online_lyric_debug_fetch_mode_smart)
                            } else {
                                stringResource(R.string.online_lyric_debug_provider_order_fmt, providerOrder.joinToString(" > ") { it.displayName(context) })
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
                        attempts.forEach { attempt ->
                            val result = attempt.result
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = result != null) { viewModel.openAttempt(attempt) }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.online_lyric_debug_attempt_header_fmt,
                                        if (result == selectedResult) stringResource(R.string.online_lyric_debug_selected_prefix) else "",
                                        attempt.provider.displayName(context),
                                        attempt.durationMs
                                    )
                                )
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
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    fontSize = 13.sp
                                )
                                if (result != null && result.error == null) {
                                    Text(stringResource(R.string.online_lyric_debug_tap_for_full_result), color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.online_lyric_debug_back))
                }
            }
        }

        dialogAttempt?.let { attempt ->
            val result = attempt.result
            MiuixBlurDialog(
                title = stringResource(R.string.online_lyric_debug_attempt_title_fmt, attempt.provider.displayName(context)),
                show = true,
                onDismissRequest = { viewModel.closeDialog() }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.online_lyric_debug_duration_fmt, attempt.durationMs))
                    if (attempt.usedCleanTitleFallback) {
                        Text(stringResource(R.string.online_lyric_debug_clean_title_used), color = MiuixTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result?.error?.let { stringResource(R.string.online_lyric_debug_error_fmt, it) }
                            ?: result?.lyrics
                            ?: stringResource(R.string.online_lyric_debug_no_result),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (canSelectDialogResult) {
                            Button(
                                onClick = { viewModel.selectAttempt(attempt) },
                                enabled = !isFetching,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.online_lyric_debug_select_result))
                            }
                        }
                        Button(
                            onClick = { viewModel.closeDialog() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.online_lyric_debug_close))
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
