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

package com.example.islandlyrics.feature.onlinelyricdebug

import android.app.Application
import android.text.format.DateUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.lyrics.online.OnlineLyricFetchSnapshotStore
import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore
import com.example.islandlyrics.lyrics.importer.LyricifyDatabaseImporter
import com.example.islandlyrics.lyrics.online.parser.OnlineLyricSidecarMerger
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.islandlyrics.R

internal fun trackIdSongKey(mediaInfo: LyricRepository.MediaInfo?): String? =
    mediaInfo?.let { "${it.packageName}|${it.title}|${it.artist}" }

internal fun retainTrackIdAttempt(
    attempts: List<OnlineLyricFetcher.ProviderAttempt>,
    attempt: OnlineLyricFetcher.ProviderAttempt
): List<OnlineLyricFetcher.ProviderAttempt> = attempts.filterNot {
    it.queryVariant == "track_id" && it.provider == attempt.provider &&
        it.result?.providerTrackId == attempt.result?.providerTrackId
} + attempt

internal fun preferredProviderAttempt(
    attempts: List<OnlineLyricFetcher.ProviderAttempt>,
    selectedResult: OnlineLyricFetcher.LyricResult?
): OnlineLyricFetcher.ProviderAttempt? =
    attempts.firstOrNull { it.result == selectedResult }
        ?: attempts.lastOrNull { it.queryVariant == "track_id" }
        ?: attempts.maxByOrNull { it.result?.score ?: Int.MIN_VALUE }

internal fun restoredMainResult(
    snapshot: OnlineLyricFetchSnapshotStore.Snapshot,
    cachedResult: OnlineLyricFetcher.LyricResult?
): OnlineLyricFetcher.LyricResult? =
    if (snapshot.songKey != null) snapshot.bestResult else cachedResult ?: snapshot.bestResult

class OnlineLyricDebugViewModel(application: Application) : AndroidViewModel(application) {
    enum class ResultRole {
        MAIN,
        TRANSLATION,
        ROMANIZATION
    }

    private val repo = LyricRepository.getInstance()
    private val appContext = application.applicationContext
    private val fetcher = OnlineLyricFetcher(networkAllowed = { !OfflineModeManager.isEnabled(appContext) })
    private val cacheStore = OnlineLyricCacheStore(application)

    private val _isFetching = MutableLiveData(false)
    val isFetching: LiveData<Boolean> = _isFetching

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _providerOrder = MutableLiveData(OnlineLyricProvider.defaultOrder())
    val providerOrder: LiveData<List<OnlineLyricProvider>> = _providerOrder
    private val _useSmartSelection = MutableLiveData(true)
    val useSmartSelection: LiveData<Boolean> = _useSmartSelection

    private val _attempts = MutableLiveData<List<OnlineLyricFetcher.ProviderAttempt>>(emptyList())
    val attempts: LiveData<List<OnlineLyricFetcher.ProviderAttempt>> = _attempts

    private val _selectedResult = MutableLiveData<OnlineLyricFetcher.LyricResult?>(null)
    val selectedResult: LiveData<OnlineLyricFetcher.LyricResult?> = _selectedResult

    private val _isCurrentSelectionFromCache = MutableLiveData(false)
    val isCurrentSelectionFromCache: LiveData<Boolean> = _isCurrentSelectionFromCache

    private val _selectedMainResult = MutableLiveData<OnlineLyricFetcher.LyricResult?>(null)
    val selectedMainResult: LiveData<OnlineLyricFetcher.LyricResult?> = _selectedMainResult

    private val _selectedTranslationResult = MutableLiveData<OnlineLyricFetcher.LyricResult?>(null)
    val selectedTranslationResult: LiveData<OnlineLyricFetcher.LyricResult?> = _selectedTranslationResult

    private val _selectedRomanResult = MutableLiveData<OnlineLyricFetcher.LyricResult?>(null)
    val selectedRomanResult: LiveData<OnlineLyricFetcher.LyricResult?> = _selectedRomanResult

    private var translationDisabledByUser = false
    private var romanDisabledByUser = false

    private val _usedCleanTitleFallback = MutableLiveData(false)
    val usedCleanTitleFallback: LiveData<Boolean> = _usedCleanTitleFallback
    private var hydratedSnapshotKey: String? = null

    private val _dialogAttempt = MutableLiveData<OnlineLyricFetcher.ProviderAttempt?>(null)
    val dialogAttempt: LiveData<OnlineLyricFetcher.ProviderAttempt?> = _dialogAttempt

    data class TrackIdPreview(
        val mediaInfo: LyricRepository.MediaInfo,
        val attempt: OnlineLyricFetcher.ProviderAttempt
    )

    private val _trackIdPreview = MutableLiveData<TrackIdPreview?>(null)
    val trackIdPreview: LiveData<TrackIdPreview?> = _trackIdPreview
    private val _trackIdError = MutableLiveData<String?>(null)
    val trackIdError: LiveData<String?> = _trackIdError
    private var trackIdJob: Job? = null
    private var trackIdMediaKey: String? = null

    private val _customMatchTitle = MutableLiveData("")
    val customMatchTitle: LiveData<String> = _customMatchTitle

    private val _customMatchArtist = MutableLiveData("")
    val customMatchArtist: LiveData<String> = _customMatchArtist

    private val _effectiveQuery = MutableLiveData("" to "")
    val effectiveQuery: LiveData<Pair<String, String>> = _effectiveQuery

    private val _querySourceLabel = MutableLiveData("")
    val querySourceLabel: LiveData<String> = _querySourceLabel

    private val _cacheStatus = MutableLiveData<String?>(null)
    val cacheStatus: LiveData<String?> = _cacheStatus

    private val _isInstrumental = MutableLiveData(false)
    val isInstrumental: LiveData<Boolean> = _isInstrumental

    private val _isAlbumInstrumental = MutableLiveData(false)
    val isAlbumInstrumental: LiveData<Boolean> = _isAlbumInstrumental

    val liveMetadata = repo.liveMetadata
    val liveLyric = repo.liveLyric
    val liveProgress = repo.liveProgress
    val liveAlbumArt = repo.liveAlbumArt
    val liveParsedLyrics = repo.liveParsedLyrics
    val isPlaying = repo.isPlaying
    private fun s(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    fun parsedLyricsText(lines: List<OnlineLyricFetcher.LyricLine>?): String {
        val lyricLines = lines.orEmpty()
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
        return lyricLines.filterIndexed { index, text ->
            index == 0 || text != lyricLines[index - 1]
        }.joinToString("\n")
    }

    fun resultLyricsText(result: OnlineLyricFetcher.LyricResult?): String {
        val parsed = parsedLyricsText(result?.parsedLines)
        return parsed.ifBlank { visibleLyricText(result?.lyrics) }
    }

    fun resultTranslationText(result: OnlineLyricFetcher.LyricResult?): String {
        return sidecarLyricsText(result?.translationLyrics)
    }

    fun resultRomanText(result: OnlineLyricFetcher.LyricResult?): String {
        return sidecarLyricsText(result?.romanLyrics)
    }

    private fun sidecarLyricsText(content: String?): String {
        return visibleLyricText(content)
    }

    private fun visibleLyricText(content: String?): String {
        if (content.isNullOrBlank()) return ""
        val lineTimestampRegex = Regex("""\[\d{1,2}:\d{2}(?:\.\d{1,3})?]""")
        val qrcHeaderRegex = Regex("""\[\d+,\d+]""")
        val wordTokenRegex = Regex("""(?:<|\()\d+,\d+(?:,\d+)?(?:>|\))""")
        val lines = content
            .lineSequence()
            .map { rawLine ->
                rawLine
                    .replace(lineTimestampRegex, "")
                    .replace(qrcHeaderRegex, "")
                    .replace(wordTokenRegex, "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()
        return lines
            .filterIndexed { index, text ->
                index == 0 || text != lines[index - 1]
            }
            .joinToString("\n")
    }

    private fun findCurrentLine(
        lines: List<OnlineLyricFetcher.LyricLine>,
        position: Long
    ): OnlineLyricFetcher.LyricLine? {
        return lines.firstOrNull { position >= it.startTime && position < it.endTime }
    }

    private fun findFallbackLine(
        lines: List<OnlineLyricFetcher.LyricLine>,
        position: Long
    ): OnlineLyricFetcher.LyricLine? {
        return lines.lastOrNull { it.startTime <= position }
    }

    fun canUseAttemptForRole(
        attempt: OnlineLyricFetcher.ProviderAttempt,
        role: ResultRole
    ): Boolean {
        val result = attempt.result ?: return false
        return canUseResultForRole(result, role)
    }

    fun canUseResultForRole(
        result: OnlineLyricFetcher.LyricResult?,
        role: ResultRole
    ): Boolean {
        if (result == null || result.error != null) return false
        return when (role) {
            ResultRole.MAIN -> isUsableMainResult(result)
            ResultRole.TRANSLATION -> resultTranslationText(result).isNotBlank()
            ResultRole.ROMANIZATION -> resultRomanText(result).isNotBlank()
        }
    }

    private fun isUsableMainResult(result: OnlineLyricFetcher.LyricResult): Boolean {
        return !result.parsedLines.isNullOrEmpty() &&
            parsedLyricsText(result.parsedLines).isNotBlank()
    }

    /** Collapses diagnostic query variants into one best visible result per provider. */
    fun groupAttemptsByProvider(
        attempts: List<OnlineLyricFetcher.ProviderAttempt>,
        role: ResultRole,
        selectedResult: OnlineLyricFetcher.LyricResult?
    ): List<OnlineLyricFetcher.ProviderAttempt> {
        val (imported, online) = attempts.filter { canUseAttemptForRole(it, role) }
            .partition { it.queryVariant == "lyricify_import" }
        return (imported + online
            .groupBy { it.provider }
            .values
            .mapNotNull { providerAttempts ->
                preferredProviderAttempt(providerAttempts, selectedResult)
            }).distinctBy { it.result?.let(LyricifyDatabaseImporter::candidateKey) }
    }

    private suspend fun importedAttempts(
        mediaInfo: LyricRepository.MediaInfo, title: String, artist: String
    ): List<OnlineLyricFetcher.ProviderAttempt> = withContext(Dispatchers.IO) {
        cacheStore.getImportedCandidates(mediaInfo, title, artist).map {
            OnlineLyricFetcher.ProviderAttempt(it.provider, it, 0L, false, queryVariant = "lyricify_import")
        }
    }

    private fun selectBestSidecarResult(
        attempts: List<OnlineLyricFetcher.ProviderAttempt>,
        preferredMain: OnlineLyricFetcher.LyricResult?,
        role: ResultRole,
        targetTitle: String,
        targetArtist: String
    ): OnlineLyricFetcher.LyricResult? {
        val kind = when (role) {
            ResultRole.TRANSLATION -> OnlineLyricSidecarMerger.SidecarKind.TRANSLATION
            ResultRole.ROMANIZATION -> OnlineLyricSidecarMerger.SidecarKind.ROMANIZATION
            ResultRole.MAIN -> return preferredMain
        }
        // 与自检索路径共用同一套 sidecar 选择：只要求匹配当前播放信息
        return OnlineLyricSidecarMerger.selectBestSidecarSource(
            attempts = attempts,
            preferredMain = preferredMain,
            kind = kind,
            targetTitle = targetTitle,
            targetArtist = targetArtist
        )
    }

    private fun buildCombinedResult(
        mainResult: OnlineLyricFetcher.LyricResult,
        translationResult: OnlineLyricFetcher.LyricResult?,
        romanResult: OnlineLyricFetcher.LyricResult?
    ): OnlineLyricFetcher.LyricResult {
        val translationLyrics = translationResult?.translationLyrics
            ?.takeIf { resultTranslationText(translationResult).isNotBlank() }
        val romanLyrics = romanResult?.romanLyrics
            ?.takeIf { resultRomanText(romanResult).isNotBlank() }
        return mainResult.copy(
            api = buildCombinedApiLabel(mainResult, translationResult, romanResult),
            translationLyrics = translationLyrics,
            romanLyrics = romanLyrics
        )
    }

    private fun buildCombinedApiLabel(
        mainResult: OnlineLyricFetcher.LyricResult,
        translationResult: OnlineLyricFetcher.LyricResult?,
        romanResult: OnlineLyricFetcher.LyricResult?
    ): String {
        val sidecars = buildList {
            if (translationResult != null && translationResult.api != mainResult.api) {
                add("T:${translationResult.api}")
            }
            if (romanResult != null && romanResult.api != mainResult.api) {
                add("R:${romanResult.api}")
            }
        }
        return if (sidecars.isEmpty()) {
            mainResult.api
        } else {
            "${mainResult.api} + ${sidecars.joinToString(" + ")}"
        }
    }

    private fun setSelectionState(
        mainResult: OnlineLyricFetcher.LyricResult?,
        translationResult: OnlineLyricFetcher.LyricResult?,
        romanResult: OnlineLyricFetcher.LyricResult?,
        fromCache: Boolean = false
    ): OnlineLyricFetcher.LyricResult? {
        val visibleTranslationResult = translationResult
            ?.takeIf { canUseResultForRole(it, ResultRole.TRANSLATION) }
        val visibleRomanResult = romanResult
            ?.takeIf { canUseResultForRole(it, ResultRole.ROMANIZATION) }
        _selectedMainResult.value = mainResult
        _selectedTranslationResult.value = visibleTranslationResult
        _selectedRomanResult.value = visibleRomanResult
        _isCurrentSelectionFromCache.value = fromCache && mainResult != null
        val combined = mainResult?.let {
            buildCombinedResult(
                mainResult = it,
                translationResult = visibleTranslationResult,
                romanResult = visibleRomanResult
            )
        }
        _selectedResult.value = combined
        return combined
    }

    private fun clearSelectionState() {
        _selectedResult.value = null
        _isCurrentSelectionFromCache.value = false
        _selectedMainResult.value = null
        _selectedTranslationResult.value = null
        _selectedRomanResult.value = null
        translationDisabledByUser = false
        romanDisabledByUser = false
    }

    private fun applyResultToRepository(
        mediaInfo: LyricRepository.MediaInfo,
        result: OnlineLyricFetcher.LyricResult,
        apiPath: String = "Online API"
    ) {
        val rule = ParserRuleHelper.getRuleForPackage(getApplication(), mediaInfo.packageName)
            ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)
        val lines = OnlineLyricSidecarMerger.withSidecars(result, rule)
        repo.updateParsedLyrics(
            lines = lines,
            hasSyllable = result.hasSyllable,
            sourceLabel = result.api,
            apiPath = apiPath,
            timelineCapability = LyricRepository.TimelineCapability.MULTI_LINE
        )

        val appLabel = ParserRuleHelper.getAppNameForPackage(getApplication(), mediaInfo.packageName)
        val position = liveProgress.value?.position ?: 0L
        val currentLine = findCurrentLine(lines, position)
        repo.updateCurrentLine(currentLine)
        val displayLine = currentLine ?: findFallbackLine(lines, position)
        repo.updateLyric(
            lyric = displayLine?.text.orEmpty(),
            app = appLabel,
            apiPath = apiPath,
            translation = displayLine?.translation,
            roma = displayLine?.roma
        )
        AppLogger.getInstance().d(
            "OnlineLyricDebug",
            "Applied ${result.api}: lines=${lines.size}, translation=${displayLine?.translation != null}, roma=${displayLine?.roma != null}"
        )
    }

    private fun applyNoLyricsState(mediaInfo: LyricRepository.MediaInfo) {
        repo.updateLyric("", mediaInfo.packageName, LyricRepository.API_PATH_INSTRUMENTAL)
        repo.updateParsedLyrics(
            lines = emptyList(),
            hasSyllable = false,
            timelineCapability = LyricRepository.TimelineCapability.NONE
        )
        repo.updateCurrentLine(null)
        clearSelectionState()
        _attempts.value = emptyList()
    }

    private suspend fun persistAndApplyResult(
        mediaInfo: LyricRepository.MediaInfo,
        queryTitle: String,
        queryArtist: String,
        result: OnlineLyricFetcher.LyricResult,
        cacheMessage: String
    ) {
        withContext(Dispatchers.IO) {
            cacheStore.saveLyricResult(
                mediaInfo = mediaInfo,
                queryTitle = queryTitle,
                queryArtist = queryArtist,
                result = result
            )
        }
        applyResultToRepository(mediaInfo, result)
        translationDisabledByUser = false
        romanDisabledByUser = false
        setSelectionState(
            mainResult = result,
            translationResult = result.takeIf { !it.translationLyrics.isNullOrBlank() },
            romanResult = result.takeIf { !it.romanLyrics.isNullOrBlank() }
        )
        _cacheStatus.value = cacheMessage
    }

    private suspend fun persistAndApplySelection(
        mediaInfo: LyricRepository.MediaInfo,
        queryTitle: String,
        queryArtist: String,
        mainResult: OnlineLyricFetcher.LyricResult,
        translationResult: OnlineLyricFetcher.LyricResult?,
        romanResult: OnlineLyricFetcher.LyricResult?,
        cacheMessage: String? = null,
        trackIdAttempt: OnlineLyricFetcher.ProviderAttempt? = null
    ) {
        val songKey = trackIdSongKey(mediaInfo)
        if (trackIdSongKey(liveMetadata.value) != songKey) return
        val combinedResult = buildCombinedResult(mainResult, translationResult, romanResult)
        withContext(Dispatchers.IO) {
            cacheStore.saveLyricResult(
                mediaInfo = mediaInfo,
                queryTitle = queryTitle,
                queryArtist = queryArtist,
                result = combinedResult
            )
        }
        if (trackIdSongKey(liveMetadata.value) != songKey) return
        val retained = OnlineLyricFetchSnapshotStore.getTrackId(songKey)
        _attempts.value = retained?.attempts.orEmpty().fold(_attempts.value.orEmpty(), ::retainTrackIdAttempt)
        if (trackIdAttempt != null) {
            translationDisabledByUser = false
            romanDisabledByUser = false
            _usedCleanTitleFallback.value = false
            _attempts.value = retainTrackIdAttempt(_attempts.value.orEmpty(), trackIdAttempt)
        }
        applyResultToRepository(mediaInfo, combinedResult)
        setSelectionState(mainResult, translationResult, romanResult)
        val previousSnapshot = OnlineLyricFetchSnapshotStore.get(
            mediaInfo.packageName,
            queryTitle,
            queryArtist,
            songKey
        )
        OnlineLyricFetchSnapshotStore.save(
            OnlineLyricFetchSnapshotStore.Snapshot(
                packageName = mediaInfo.packageName,
                queryTitle = queryTitle,
                queryArtist = queryArtist,
                fetchedAt = previousSnapshot?.fetchedAt ?: System.currentTimeMillis(),
                bestResult = combinedResult,
                attempts = _attempts.value.orEmpty(),
                usedCleanTitleFallback = _usedCleanTitleFallback.value == true,
                songKey = songKey.takeIf { _attempts.value.orEmpty().any { it.queryVariant == "track_id" } }
            )
        )
        hydratedSnapshotKey = OnlineLyricFetchSnapshotStore.buildKey(
            mediaInfo.packageName,
            queryTitle,
            queryArtist
        )
        _cacheStatus.value = cacheMessage ?: s(R.string.online_lyric_debug_cache_written_fmt, combinedResult.api)
    }

    fun syncProviderOrderFromCurrentRule() {
        val pkg = liveMetadata.value?.packageName ?: return
        val rule = ParserRuleHelper.getRuleForPackage(getApplication(), pkg)
            ?: ParserRuleHelper.createDefaultRule(pkg)
        _useSmartSelection.value = rule.useSmartOnlineLyricSelection
        _providerOrder.value = OnlineLyricProvider.normalizeOrder(rule.onlineLyricProviderOrder)
        syncCurrentSongQuery()
    }

    fun resetProviderOrder() {
        _providerOrder.value = OnlineLyricProvider.defaultOrder()
    }

    fun moveProvider(provider: OnlineLyricProvider, direction: Int) {
        val current = _providerOrder.value.orEmpty().toMutableList()
        val index = current.indexOf(provider)
        if (index == -1) return
        val target = (index + direction).coerceIn(0, current.lastIndex)
        if (target == index) return
        current.removeAt(index)
        current.add(target, provider)
        _providerOrder.value = current
    }

    fun updateCustomMatchTitle(value: String) {
        _customMatchTitle.value = value
    }

    fun updateCustomMatchArtist(value: String) {
        _customMatchArtist.value = value
    }

    fun rematchWithCurrentPlayback() {
        val mediaInfo = liveMetadata.value
        if (mediaInfo == null || (mediaInfo.title.isBlank() && mediaInfo.artist.isBlank())) {
            _error.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        _customMatchTitle.value = mediaInfo.title.trim()
        _customMatchArtist.value = mediaInfo.artist.trim()
        _error.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheStore.saveMatchOverride(
                    mediaInfo = mediaInfo,
                    title = mediaInfo.title.trim(),
                    artist = mediaInfo.artist.trim()
                )
            }
            fetchLyrics(forceRefresh = true)
        }
    }

    fun syncCurrentSongQuery() {
        val mediaInfo = liveMetadata.value ?: return
        val songKey = trackIdSongKey(mediaInfo)
        val rule = ParserRuleHelper.getRuleForPackage(getApplication(), mediaInfo.packageName)
            ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                cacheStore.getCurrentSongState(
                    mediaInfo = mediaInfo,
                    fallbackTitle = mediaInfo.title,
                    fallbackArtist = mediaInfo.artist,
                    useRawMetadata = rule.useRawMetadataForOnlineMatching
                )
            }
            val cachedHit = if (!state.isInstrumental) {
                withContext(Dispatchers.IO) {
                    cacheStore.getCachedLyric(
                        mediaInfo,
                        state.effectiveTitle,
                        state.effectiveArtist
                    )
                }
            } else {
                null
            }
            val imported = if (state.isInstrumental) emptyList() else
                importedAttempts(mediaInfo, state.effectiveTitle, state.effectiveArtist)
            if (trackIdSongKey(liveMetadata.value) != songKey) return@launch
            val snapshot = if (!state.isInstrumental) {
                OnlineLyricFetchSnapshotStore.get(
                    mediaInfo.packageName,
                    state.effectiveTitle,
                    state.effectiveArtist,
                    songKey
                )
            } else {
                null
            }
            val snapshotKey = OnlineLyricFetchSnapshotStore.buildKey(
                mediaInfo.packageName,
                state.effectiveTitle,
                state.effectiveArtist
            )
            if (hydratedSnapshotKey != snapshotKey) {
                hydratedSnapshotKey = null
                _attempts.value = emptyList()
                _usedCleanTitleFallback.value = false
                clearSelectionState()
            }
            _customMatchTitle.value = state.matchOverride?.title.orEmpty()
            _customMatchArtist.value = state.matchOverride?.artist.orEmpty()
            _effectiveQuery.value = state.effectiveTitle to state.effectiveArtist
            _isInstrumental.value = state.isInstrumental
            _isAlbumInstrumental.value = state.isAlbumInstrumental
            _querySourceLabel.value = when (state.querySource) {
                OnlineLyricCacheStore.QuerySource.CUSTOM_OVERRIDE -> s(R.string.online_lyric_debug_query_source_custom)
                OnlineLyricCacheStore.QuerySource.RAW_METADATA -> s(R.string.online_lyric_debug_query_source_raw)
                OnlineLyricCacheStore.QuerySource.DEFAULT_METADATA -> s(R.string.online_lyric_debug_query_source_default)
            }
            if (snapshot != null) {
                hydratedSnapshotKey = snapshotKey
                _attempts.value = snapshot.attempts
                _usedCleanTitleFallback.value = snapshot.usedCleanTitleFallback
                val main = restoredMainResult(snapshot, cachedHit?.result)
                if (main != null) {
                    setSelectionState(
                        mainResult = main,
                        translationResult = main.takeIf { !it.translationLyrics.isNullOrBlank() },
                        romanResult = main.takeIf { !it.romanLyrics.isNullOrBlank() },
                        fromCache = snapshot.songKey == null && cachedHit != null && snapshot.bestResult != main
                    )
                }
                _cacheStatus.value = if (snapshot.fromCache) {
                    s(R.string.online_lyric_debug_cache_hit)
                } else {
                    s(
                        R.string.online_lyric_debug_snapshot_status,
                        DateUtils.getRelativeTimeSpanString(
                            snapshot.fetchedAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        )
                    )
                }
            } else if (cachedHit != null) {
                hydratedSnapshotKey = snapshotKey
                val main = cachedHit.result
                setSelectionState(
                    mainResult = main,
                    translationResult = main.takeIf { !it.translationLyrics.isNullOrBlank() },
                    romanResult = main.takeIf { !it.romanLyrics.isNullOrBlank() },
                    fromCache = true
                )
                _attempts.value = listOf(
                    OnlineLyricFetcher.ProviderAttempt(
                        provider = main.provider,
                        result = main,
                        durationMs = 0L,
                        usedCleanTitleFallback = false
                    )
                )
                _usedCleanTitleFallback.value = false
                _cacheStatus.value = s(
                    R.string.online_lyric_debug_cached_provider_fmt,
                    main.api
                )
            } else {
                _cacheStatus.value = when {
                    state.isInstrumental -> {
                        if (state.isAlbumInstrumental) {
                            s(R.string.online_lyric_debug_album_instrumental_status)
                        } else {
                            s(R.string.online_lyric_debug_instrumental_status)
                        }
                    }
                    state.cachedLyricUpdatedAt != null -> {
                        s(
                            R.string.online_lyric_debug_cached_provider_fmt,
                            state.cachedProviderLabel ?: s(R.string.online_lyric_debug_cached_default_provider)
                        )
                    }
                    else -> s(R.string.online_lyric_debug_no_cached_lyric)
                }
            }
            if (imported.isNotEmpty()) {
                val current = (_selectedMainResult.value ?: cachedHit?.result)?.let {
                    OnlineLyricFetcher.ProviderAttempt(it.provider, it, 0L, false, queryVariant = "lyricify_import")
                }
                _attempts.value = (listOfNotNull(current) + imported + _attempts.value.orEmpty())
                    .distinctBy { it.result?.let(LyricifyDatabaseImporter::candidateKey) }
            }
        }
    }

    fun saveCurrentSongMatchOverride() {
        val mediaInfo = liveMetadata.value ?: run {
            _error.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        val title = _customMatchTitle.value.orEmpty().trim()
        val artist = _customMatchArtist.value.orEmpty().trim()
        if (title.isBlank() && artist.isBlank()) {
            _error.value = s(R.string.online_lyric_debug_error_empty_override)
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheStore.saveMatchOverride(mediaInfo, title, artist)
            }
            _cacheStatus.value = s(R.string.online_lyric_debug_override_saved)
            syncCurrentSongQuery()
        }
    }

    fun clearCurrentSongMatchOverride() {
        val mediaInfo = liveMetadata.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheStore.clearMatchOverride(mediaInfo)
            }
            _customMatchTitle.value = ""
            _customMatchArtist.value = ""
            _cacheStatus.value = s(R.string.online_lyric_debug_override_cleared)
            syncCurrentSongQuery()
        }
    }

    fun markCurrentSongInstrumental() {
        val mediaInfo = liveMetadata.value ?: run {
            _error.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheStore.markInstrumental(mediaInfo)
            }
            _error.value = null
            _customMatchTitle.value = ""
            _customMatchArtist.value = ""
            _isInstrumental.value = true
            applyNoLyricsState(mediaInfo)
            _cacheStatus.value = s(R.string.online_lyric_debug_instrumental_marked)
            syncCurrentSongQuery()
        }
    }

    fun clearCurrentSongInstrumentalMarker() {
        val mediaInfo = liveMetadata.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheStore.clearInstrumentalMarker(mediaInfo)
            }
            _isInstrumental.value = false
            _cacheStatus.value = s(R.string.online_lyric_debug_instrumental_cleared)
            syncCurrentSongQuery()
        }
    }

    fun markCurrentAlbumInstrumental() {
        val mediaInfo = liveMetadata.value ?: run {
            _error.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        val album = mediaInfo.album.trim()
        if (album.isBlank()) {
            _error.value = s(R.string.online_lyric_debug_error_no_album)
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheStore.markAlbumInstrumental(mediaInfo)
            }
            _error.value = null
            _customMatchTitle.value = ""
            _customMatchArtist.value = ""
            _isInstrumental.value = true
            _isAlbumInstrumental.value = true
            applyNoLyricsState(mediaInfo)
            _cacheStatus.value = s(R.string.online_lyric_debug_album_instrumental_marked, album)
            syncCurrentSongQuery()
        }
    }

    fun clearCurrentAlbumInstrumentalMarker() {
        val mediaInfo = liveMetadata.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheStore.clearAlbumInstrumentalMarker(mediaInfo)
            }
            _isAlbumInstrumental.value = false
            _cacheStatus.value = s(R.string.online_lyric_debug_album_instrumental_cleared)
            syncCurrentSongQuery()
        }
    }

    fun rematchLyrics() {
        val mediaInfo = liveMetadata.value ?: run {
            _error.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        val title = _customMatchTitle.value.orEmpty().trim()
        val artist = _customMatchArtist.value.orEmpty().trim()
        if (title.isBlank() && artist.isBlank()) {
            _error.value = s(R.string.online_lyric_debug_error_empty_override)
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheStore.saveMatchOverride(mediaInfo, title, artist)
            }
            fetchLyrics(forceRefresh = true)
        }
    }

    /**
     * 单独匹配翻译/拼音歌词（不改变主歌词）。
     * 放在“重新匹配”下方，复用当前有效查询，重新抓取所有源后仅刷新 sidecar，
     * 主歌词保持当前选中不变，避免“经常什么结果都没有”时主歌词被意外替换。
     */
    fun rematchSidecarLyrics() {
        val mediaInfo = liveMetadata.value ?: run {
            _error.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        if (OfflineModeManager.isEnabled(appContext)) {
            _error.value = s(R.string.offline_mode_network_blocked)
            return
        }
        val rule = ParserRuleHelper.getRuleForPackage(getApplication(), mediaInfo.packageName)
            ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)
        viewModelScope.launch {
            _isFetching.value = true
            _error.value = null
            try {
                val currentSongState = withContext(Dispatchers.IO) {
                    cacheStore.getCurrentSongState(
                        mediaInfo = mediaInfo,
                        fallbackTitle = mediaInfo.title,
                        fallbackArtist = mediaInfo.artist,
                        useRawMetadata = rule.useRawMetadataForOnlineMatching
                    )
                }
                val queryTitle = currentSongState.effectiveTitle
                val queryArtist = currentSongState.effectiveArtist
                if (queryTitle.isBlank() && queryArtist.isBlank()) {
                    _error.value = s(R.string.online_lyric_debug_error_no_song)
                    return@launch
                }
                // 复用当前主结果，若无则用最佳结果兜底
                val preserveMain = _selectedMainResult.value ?: _selectedResult.value
                val outcome = fetcher.fetchLyrics(
                    title = queryTitle,
                    artist = queryArtist,
                    album = mediaInfo.album,
                    durationMs = mediaInfo.duration,
                    albumArtist = mediaInfo.albumArtist,
                    mediaId = mediaInfo.mediaId,
                    mediaUri = mediaInfo.mediaUri,
                    providerOrderIds = if (rule.useSmartOnlineLyricSelection) {
                        OnlineLyricProvider.defaultIdsForPackage(mediaInfo.packageName)
                    } else {
                        _providerOrder.value.orEmpty().map { it.id }
                    },
                    useSmartSelection = rule.useSmartOnlineLyricSelection,
                    collectAllResults = true
                )
                _attempts.value = outcome.attempts + importedAttempts(mediaInfo, queryTitle, queryArtist)
                _usedCleanTitleFallback.value = outcome.usedCleanTitleFallback
                OnlineLyricFetchSnapshotStore.save(
                    OnlineLyricFetchSnapshotStore.Snapshot(
                        packageName = mediaInfo.packageName,
                        queryTitle = queryTitle,
                        queryArtist = queryArtist,
                        fetchedAt = System.currentTimeMillis(),
                        bestResult = outcome.bestResult,
                        attempts = outcome.attempts,
                        usedCleanTitleFallback = outcome.usedCleanTitleFallback
                    )
                )
                val mainForSidecar = preserveMain ?: outcome.bestResult
                if (mainForSidecar == null) {
                    _error.value = s(R.string.online_lyric_debug_all_apis_failed)
                    return@launch
                }
                val translationResult = selectBestSidecarResult(
                    attempts = outcome.attempts,
                    preferredMain = mainForSidecar,
                    role = ResultRole.TRANSLATION,
                    targetTitle = queryTitle,
                    targetArtist = queryArtist
                )
                val romanResult = selectBestSidecarResult(
                    attempts = outcome.attempts,
                    preferredMain = mainForSidecar,
                    role = ResultRole.ROMANIZATION,
                    targetTitle = queryTitle,
                    targetArtist = queryArtist
                )
                if (translationResult == null && romanResult == null) {
                    _error.value = s(R.string.online_lyric_debug_no_translation_candidates)
                    // 仍刷新 attempts 供用户查看
                    return@launch
                }
                // 仅更新 sidecar，保留主歌词
                persistAndApplySelection(
                    mediaInfo = mediaInfo,
                    queryTitle = queryTitle,
                    queryArtist = queryArtist,
                    mainResult = mainForSidecar,
                    translationResult = translationResult ?: _selectedTranslationResult.value,
                    romanResult = romanResult ?: _selectedRomanResult.value,
                    cacheMessage = s(R.string.online_lyric_debug_cache_switched_fmt, mainForSidecar.api)
                )
                _cacheStatus.value = s(R.string.online_lyric_debug_cache_switched_fmt, mainForSidecar.api)
            } catch (e: Exception) {
                _error.value = s(R.string.online_lyric_debug_error_fetch_failed_fmt, e.message ?: "")
            } finally {
                _isFetching.value = false
                syncCurrentSongQuery()
            }
        }
    }

    fun fetchLyrics(forceRefresh: Boolean = false) {
        if (OfflineModeManager.isEnabled(appContext)) {
            _isFetching.value = false
            _error.value = s(R.string.offline_mode_network_blocked)
            _usedCleanTitleFallback.value = false
            syncCurrentSongQuery()
            return
        }
        val mediaInfo = liveMetadata.value
        if (mediaInfo == null) {
            _error.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        val rule = ParserRuleHelper.getRuleForPackage(getApplication(), mediaInfo.packageName)
            ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)

        _isFetching.value = true
        _error.value = null
        _attempts.value = emptyList()
        clearSelectionState()
        _usedCleanTitleFallback.value = false

        viewModelScope.launch {
            try {
                val currentSongState = withContext(Dispatchers.IO) {
                    cacheStore.getCurrentSongState(
                        mediaInfo = mediaInfo,
                        fallbackTitle = mediaInfo.title,
                        fallbackArtist = mediaInfo.artist,
                        useRawMetadata = rule.useRawMetadataForOnlineMatching
                    )
                }
                val queryTitle = currentSongState.effectiveTitle
                val queryArtist = currentSongState.effectiveArtist
                _effectiveQuery.value = queryTitle to queryArtist
                _isInstrumental.value = currentSongState.isInstrumental
                _isAlbumInstrumental.value = currentSongState.isAlbumInstrumental
                _querySourceLabel.value = when (currentSongState.querySource) {
                    OnlineLyricCacheStore.QuerySource.CUSTOM_OVERRIDE -> s(R.string.online_lyric_debug_query_source_custom)
                    OnlineLyricCacheStore.QuerySource.RAW_METADATA -> s(R.string.online_lyric_debug_query_source_raw)
                    OnlineLyricCacheStore.QuerySource.DEFAULT_METADATA -> s(R.string.online_lyric_debug_query_source_default)
                }
                if (currentSongState.isInstrumental) {
                    applyNoLyricsState(mediaInfo)
                    _cacheStatus.value = if (currentSongState.isAlbumInstrumental) {
                        s(R.string.online_lyric_debug_album_instrumental_status)
                    } else {
                        s(R.string.online_lyric_debug_instrumental_status)
                    }
                    return@launch
                }
                if (queryTitle.isBlank() && queryArtist.isBlank()) {
                    _error.value = s(R.string.online_lyric_debug_error_no_song)
                    return@launch
                }

                if (!forceRefresh) {
                    val cacheHit = withContext(Dispatchers.IO) {
                        cacheStore.getCachedLyric(mediaInfo, queryTitle, queryArtist)
                    }
                    if (cacheHit != null) {
                        setSelectionState(
                            mainResult = cacheHit.result,
                            translationResult = cacheHit.result.takeIf { !it.translationLyrics.isNullOrBlank() },
                            romanResult = cacheHit.result.takeIf { !it.romanLyrics.isNullOrBlank() },
                            fromCache = true
                        )
                        _attempts.value = listOf(
                            OnlineLyricFetcher.ProviderAttempt(
                                provider = cacheHit.result.provider,
                                result = cacheHit.result,
                                durationMs = 0L,
                                usedCleanTitleFallback = false,
                                queryVariant = "lyricify_import"
                            )
                        ) + importedAttempts(mediaInfo, queryTitle, queryArtist)
                        hydratedSnapshotKey = OnlineLyricFetchSnapshotStore.buildKey(
                            mediaInfo.packageName,
                            queryTitle,
                            queryArtist
                        )
                        _cacheStatus.value = s(R.string.online_lyric_debug_cache_hit)
                        applyResultToRepository(mediaInfo, cacheHit.result, apiPath = "Online Cache")
                        return@launch
                    }
                }

                val outcome = fetcher.fetchLyrics(
                    title = queryTitle,
                    artist = queryArtist,
                    album = mediaInfo.album,
                    durationMs = mediaInfo.duration,
                    albumArtist = mediaInfo.albumArtist,
                    mediaId = mediaInfo.mediaId,
                    mediaUri = mediaInfo.mediaUri,
                    providerOrderIds = if (rule.useSmartOnlineLyricSelection) {
                        OnlineLyricProvider.defaultIdsForPackage(mediaInfo.packageName)
                    } else {
                        _providerOrder.value.orEmpty().map { it.id }
                    },
                    useSmartSelection = rule.useSmartOnlineLyricSelection,
                    collectAllResults = true
                )
                _attempts.value = outcome.attempts + importedAttempts(mediaInfo, queryTitle, queryArtist)
                _usedCleanTitleFallback.value = outcome.usedCleanTitleFallback
                OnlineLyricFetchSnapshotStore.save(
                    OnlineLyricFetchSnapshotStore.Snapshot(
                        packageName = mediaInfo.packageName,
                        queryTitle = queryTitle,
                        queryArtist = queryArtist,
                        fetchedAt = System.currentTimeMillis(),
                        bestResult = outcome.bestResult,
                        attempts = outcome.attempts,
                        usedCleanTitleFallback = outcome.usedCleanTitleFallback
                    )
                )
                hydratedSnapshotKey = OnlineLyricFetchSnapshotStore.buildKey(
                    mediaInfo.packageName,
                    queryTitle,
                    queryArtist
                )
                if (outcome.bestResult == null) {
                    clearSelectionState()
                    _error.value = s(R.string.online_lyric_debug_all_apis_failed)
                } else {
                    val translationResult = selectBestSidecarResult(
                        attempts = outcome.attempts,
                        preferredMain = outcome.bestResult,
                        role = ResultRole.TRANSLATION,
                        targetTitle = queryTitle,
                        targetArtist = queryArtist
                    )
                    val romanResult = selectBestSidecarResult(
                        attempts = outcome.attempts,
                        preferredMain = outcome.bestResult,
                        role = ResultRole.ROMANIZATION,
                        targetTitle = queryTitle,
                        targetArtist = queryArtist
                    )
                    persistAndApplySelection(
                        mediaInfo = mediaInfo,
                        queryTitle = queryTitle,
                        queryArtist = queryArtist,
                        mainResult = outcome.bestResult,
                        translationResult = translationResult,
                        romanResult = romanResult,
                        cacheMessage = s(R.string.online_lyric_debug_cache_switched_fmt, outcome.bestResult.api)
                    )
                    AppLogger.getInstance().log("OnlineLyricDebug", "自动选择: ${outcome.bestResult.api} (${outcome.bestResult.score})")
                }
            } catch (e: Exception) {
                _error.value = s(R.string.online_lyric_debug_error_fetch_failed_fmt, e.message ?: "")
            } finally {
                _isFetching.value = false
                syncCurrentSongQuery()
            }
        }
    }

    fun syncTrackIdSong() {
        val songKey = trackIdSongKey(liveMetadata.value)
        if (trackIdMediaKey == songKey) return
        trackIdJob?.cancel()
        trackIdMediaKey = songKey
        _trackIdPreview.value = null
        _trackIdError.value = null
    }

    fun clearTrackIdError() {
        _trackIdError.value = null
    }

    fun closeTrackIdPreview() {
        _trackIdPreview.value = null
    }

    fun trackIdPreviewDetails(preview: TrackIdPreview): String = buildString {
        val result = preview.attempt.result
        appendLine(s(R.string.online_lyric_track_id_preview_id, result?.providerTrackId.orEmpty()))
        listOfNotNull(result?.matchedTitle, result?.matchedArtist)
            .filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }
            ?.let { appendLine(it) }
        append(s(R.string.online_lyric_track_id_preview_target, preview.mediaInfo.title, preview.mediaInfo.artist))
        if (!canUseResultForRole(result, ResultRole.MAIN)) {
            append("\n\n")
            append(s(R.string.online_lyric_track_id_no_timing))
        }
    }

    fun fetchLyricsById(provider: OnlineLyricProvider, input: String) {
        if (_isFetching.value == true) return
        val mediaInfo = liveMetadata.value
        if (mediaInfo == null || (mediaInfo.title.isBlank() && mediaInfo.artist.isBlank())) {
            _trackIdError.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        if (OfflineModeManager.isEnabled(appContext)) {
            _trackIdError.value = s(R.string.offline_mode_network_blocked)
            return
        }
        val id = provider.normalizeTrackId(input)
        if (id == null) {
            _trackIdError.value = s(R.string.online_lyric_track_id_invalid)
            return
        }
        syncTrackIdSong()
        val songKey = trackIdSongKey(mediaInfo)
        _isFetching.value = true
        _trackIdError.value = null
        _trackIdPreview.value = null
        trackIdJob = viewModelScope.launch {
            try {
                val state = currentTrackIdSongState(mediaInfo)
                if (trackIdSongKey(liveMetadata.value) != songKey) return@launch
                if (state.isInstrumental) {
                    _trackIdError.value = s(R.string.online_lyric_track_id_instrumental)
                    return@launch
                }
                val start = android.os.SystemClock.elapsedRealtime()
                val result = fetcher.fetchLyricsById(provider, id)
                if (trackIdSongKey(liveMetadata.value) != songKey) return@launch
                if (result == null || result.error != null || resultLyricsText(result).isBlank()) {
                    _trackIdError.value = result?.error ?: s(R.string.online_lyric_track_id_not_found)
                    return@launch
                }
                _trackIdPreview.value = TrackIdPreview(
                    mediaInfo,
                    OnlineLyricFetcher.ProviderAttempt(
                        provider = provider,
                        result = result,
                        durationMs = android.os.SystemClock.elapsedRealtime() - start,
                        usedCleanTitleFallback = false,
                        queryVariant = "track_id"
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (trackIdSongKey(liveMetadata.value) == songKey) {
                    _trackIdError.value = s(R.string.online_lyric_debug_error_fetch_failed_fmt, e.message.orEmpty())
                }
            } finally {
                _isFetching.value = false
            }
        }
    }

    private suspend fun currentTrackIdSongState(mediaInfo: LyricRepository.MediaInfo) =
        withContext(Dispatchers.IO) {
            val rule = ParserRuleHelper.getRuleForPackage(getApplication(), mediaInfo.packageName)
                ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)
            cacheStore.getCurrentSongState(
                mediaInfo = mediaInfo,
                fallbackTitle = mediaInfo.title,
                fallbackArtist = mediaInfo.artist,
                useRawMetadata = rule.useRawMetadataForOnlineMatching
            )
        }

    fun applyTrackIdPreview() {
        if (_isFetching.value == true) return
        val preview = _trackIdPreview.value ?: return
        val result = preview.attempt.result ?: return
        val songKey = trackIdSongKey(preview.mediaInfo)
        if (trackIdSongKey(liveMetadata.value) != songKey) {
            closeTrackIdPreview()
            _trackIdError.value = s(R.string.online_lyric_track_id_song_changed)
            return
        }
        if (!canUseResultForRole(result, ResultRole.MAIN)) return
        _isFetching.value = true
        trackIdJob = viewModelScope.launch {
            try {
                val state = currentTrackIdSongState(preview.mediaInfo)
                if (trackIdSongKey(liveMetadata.value) != songKey) return@launch
                if (state.isInstrumental) {
                    closeTrackIdPreview()
                    _trackIdError.value = s(R.string.online_lyric_track_id_instrumental)
                    return@launch
                }
                persistAndApplySelection(
                    mediaInfo = preview.mediaInfo,
                    queryTitle = state.effectiveTitle,
                    queryArtist = state.effectiveArtist,
                    mainResult = result,
                    translationResult = result.takeIf { !it.translationLyrics.isNullOrBlank() },
                    romanResult = result.takeIf { !it.romanLyrics.isNullOrBlank() },
                    trackIdAttempt = preview.attempt
                )
                closeTrackIdPreview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (trackIdSongKey(liveMetadata.value) == songKey) {
                    closeTrackIdPreview()
                    _trackIdError.value = s(R.string.online_lyric_debug_error_fetch_failed_fmt, e.message.orEmpty())
                }
            } finally {
                _isFetching.value = false
            }
        }
    }

    fun openAttempt(attempt: OnlineLyricFetcher.ProviderAttempt) {
        _dialogAttempt.value = attempt
    }

    fun closeDialog() {
        _dialogAttempt.value = null
    }

    fun selectAttempt(attempt: OnlineLyricFetcher.ProviderAttempt) {
        selectAttemptForRole(ResultRole.MAIN, attempt)
    }

    fun selectAttemptForRole(role: ResultRole, attempt: OnlineLyricFetcher.ProviderAttempt) {
        val mediaInfo = liveMetadata.value ?: run {
            _error.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        val result = attempt.result
        if (result == null || !canUseAttemptForRole(attempt, role)) {
            _error.value = s(R.string.online_lyric_debug_error_result_unavailable)
            return
        }

        _isFetching.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val rule = ParserRuleHelper.getRuleForPackage(getApplication(), mediaInfo.packageName)
                    ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)
                val currentSongState = withContext(Dispatchers.IO) {
                    cacheStore.getCurrentSongState(
                        mediaInfo = mediaInfo,
                        fallbackTitle = mediaInfo.title,
                        fallbackArtist = mediaInfo.artist,
                        useRawMetadata = rule.useRawMetadataForOnlineMatching
                    )
                }
                if (trackIdSongKey(liveMetadata.value) != trackIdSongKey(mediaInfo)) return@launch
                val currentMain = when (role) {
                    ResultRole.MAIN -> result
                    ResultRole.TRANSLATION,
                    ResultRole.ROMANIZATION -> _selectedMainResult.value ?: _selectedResult.value
                }
                if (currentMain == null || !isUsableMainResult(currentMain)) {
                    _error.value = s(R.string.online_lyric_debug_error_result_unavailable)
                    return@launch
                }
                val nextTranslation = when (role) {
                    ResultRole.MAIN -> {
                        if (translationDisabledByUser) {
                            null
                        } else {
                            _selectedTranslationResult.value
                                ?: result.takeIf { !it.translationLyrics.isNullOrBlank() }
                        }
                    }
                    ResultRole.TRANSLATION -> {
                        translationDisabledByUser = false
                        result
                    }
                    ResultRole.ROMANIZATION -> _selectedTranslationResult.value
                }
                val nextRoman = when (role) {
                    ResultRole.MAIN -> {
                        if (romanDisabledByUser) {
                            null
                        } else {
                            _selectedRomanResult.value
                                ?: result.takeIf { !it.romanLyrics.isNullOrBlank() }
                        }
                    }
                    ResultRole.TRANSLATION -> _selectedRomanResult.value
                    ResultRole.ROMANIZATION -> {
                        romanDisabledByUser = false
                        result
                    }
                }
                persistAndApplySelection(
                    mediaInfo = mediaInfo,
                    queryTitle = currentSongState.effectiveTitle,
                    queryArtist = currentSongState.effectiveArtist,
                    mainResult = currentMain,
                    translationResult = nextTranslation,
                    romanResult = nextRoman,
                    cacheMessage = s(
                        R.string.online_lyric_debug_cache_written_fmt,
                        buildCombinedApiLabel(currentMain, nextTranslation, nextRoman)
                    )
                )
                _dialogAttempt.value = null
                AppLogger.getInstance().log("OnlineLyricDebug", "手动选择[$role]: ${result.api} (${result.score})")
            } catch (e: Exception) {
                _error.value = s(R.string.online_lyric_debug_error_switch_failed_fmt, e.message ?: "")
            } finally {
                _isFetching.value = false
                syncCurrentSongQuery()
            }
        }
    }

    fun clearSidecarForRole(role: ResultRole) {
        if (role == ResultRole.MAIN) return
        val mediaInfo = liveMetadata.value ?: run {
            _error.value = s(R.string.online_lyric_debug_error_no_song)
            return
        }
        val currentMain = _selectedMainResult.value ?: _selectedResult.value
        if (currentMain == null || !isUsableMainResult(currentMain)) {
            _error.value = s(R.string.online_lyric_debug_error_result_unavailable)
            return
        }

        _isFetching.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val rule = ParserRuleHelper.getRuleForPackage(getApplication(), mediaInfo.packageName)
                    ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)
                val currentSongState = withContext(Dispatchers.IO) {
                    cacheStore.getCurrentSongState(
                        mediaInfo = mediaInfo,
                        fallbackTitle = mediaInfo.title,
                        fallbackArtist = mediaInfo.artist,
                        useRawMetadata = rule.useRawMetadataForOnlineMatching
                    )
                }
                if (trackIdSongKey(liveMetadata.value) != trackIdSongKey(mediaInfo)) return@launch
                val nextTranslation = when (role) {
                    ResultRole.TRANSLATION -> {
                        translationDisabledByUser = true
                        null
                    }
                    ResultRole.ROMANIZATION -> _selectedTranslationResult.value
                    ResultRole.MAIN -> _selectedTranslationResult.value
                }
                val nextRoman = when (role) {
                    ResultRole.TRANSLATION -> _selectedRomanResult.value
                    ResultRole.ROMANIZATION -> {
                        romanDisabledByUser = true
                        null
                    }
                    ResultRole.MAIN -> _selectedRomanResult.value
                }
                persistAndApplySelection(
                    mediaInfo = mediaInfo,
                    queryTitle = currentSongState.effectiveTitle,
                    queryArtist = currentSongState.effectiveArtist,
                    mainResult = currentMain,
                    translationResult = nextTranslation,
                    romanResult = nextRoman,
                    cacheMessage = s(
                        R.string.online_lyric_debug_cache_written_fmt,
                        buildCombinedApiLabel(currentMain, nextTranslation, nextRoman)
                    )
                )
                AppLogger.getInstance().log("OnlineLyricDebug", "手动清除[$role]")
            } catch (e: Exception) {
                _error.value = s(R.string.online_lyric_debug_error_switch_failed_fmt, e.message ?: "")
            } finally {
                _isFetching.value = false
                syncCurrentSongQuery()
            }
        }
    }
}

