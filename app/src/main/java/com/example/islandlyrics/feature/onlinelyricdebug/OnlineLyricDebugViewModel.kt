package com.example.islandlyrics.feature.onlinelyricdebug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.lyric.OnlineLyricFetcher
import com.example.islandlyrics.data.lyric.OnlineLyricCacheStore
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineLyricDebugViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = LyricRepository.getInstance()
    private val fetcher = OnlineLyricFetcher()
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

    private val _usedCleanTitleFallback = MutableLiveData(false)
    val usedCleanTitleFallback: LiveData<Boolean> = _usedCleanTitleFallback

    private val _parsedLyrics = MutableLiveData<List<OnlineLyricFetcher.LyricLine>>(emptyList())
    val parsedLyrics: LiveData<List<OnlineLyricFetcher.LyricLine>> = _parsedLyrics

    private val _dialogAttempt = MutableLiveData<OnlineLyricFetcher.ProviderAttempt?>(null)
    val dialogAttempt: LiveData<OnlineLyricFetcher.ProviderAttempt?> = _dialogAttempt

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

    val liveMetadata = repo.liveMetadata
    val liveLyric = repo.liveLyric
    val liveProgress = repo.liveProgress
    val isPlaying = repo.isPlaying

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

    private fun applyResultToRepository(
        mediaInfo: LyricRepository.MediaInfo,
        result: OnlineLyricFetcher.LyricResult,
        apiPath: String = "Online API"
    ) {
        val lines = result.parsedLines.orEmpty()
        repo.updateParsedLyrics(
            lines = lines,
            hasSyllable = result.hasSyllable,
            sourceLabel = result.api,
            apiPath = apiPath
        )

        val appLabel = ParserRuleHelper.getAppNameForPackage(getApplication(), mediaInfo.packageName)
        val position = liveProgress.value?.position ?: 0L
        val currentLine = findCurrentLine(lines, position)
        repo.updateCurrentLine(currentLine)
        repo.updateLyric((currentLine ?: findFallbackLine(lines, position))?.text.orEmpty(), appLabel, apiPath)
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
        _selectedResult.value = result
        _parsedLyrics.value = result.parsedLines.orEmpty()
        _cacheStatus.value = cacheMessage
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

    fun syncCurrentSongQuery() {
        val mediaInfo = liveMetadata.value ?: return
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
            _customMatchTitle.value = state.matchOverride?.title.orEmpty()
            _customMatchArtist.value = state.matchOverride?.artist.orEmpty()
            _effectiveQuery.value = state.effectiveTitle to state.effectiveArtist
            _querySourceLabel.value = when (state.querySource) {
                OnlineLyricCacheStore.QuerySource.CUSTOM_OVERRIDE -> "当前使用: 自定义匹配信息"
                OnlineLyricCacheStore.QuerySource.RAW_METADATA -> "当前使用: 原始播放器信息"
                OnlineLyricCacheStore.QuerySource.DEFAULT_METADATA -> "当前使用: 当前解析后的播放信息"
            }
            _cacheStatus.value = when {
                state.cachedLyricUpdatedAt != null -> {
                    "已缓存歌词: ${state.cachedProviderLabel ?: "在线歌词"}"
                }
                else -> "当前歌曲暂无歌词缓存"
            }
        }
    }

    fun saveCurrentSongMatchOverride() {
        val mediaInfo = liveMetadata.value ?: run {
            _error.value = "没有可用的歌曲信息"
            return
        }
        val title = _customMatchTitle.value.orEmpty().trim()
        val artist = _customMatchArtist.value.orEmpty().trim()
        if (title.isBlank() && artist.isBlank()) {
            _error.value = "请至少填写歌名或歌手中的一项"
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheStore.saveMatchOverride(mediaInfo, title, artist)
            }
            _cacheStatus.value = "已保存当前歌曲匹配信息，留空项会自动使用原始播放信息"
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
            _cacheStatus.value = "已清除当前歌曲自定义匹配和关联歌词缓存"
            syncCurrentSongQuery()
        }
    }

    fun fetchLyrics(forceRefresh: Boolean = false) {
        val mediaInfo = liveMetadata.value
        if (mediaInfo == null) {
            _error.value = "没有可用的歌曲信息"
            return
        }
        val rule = ParserRuleHelper.getRuleForPackage(getApplication(), mediaInfo.packageName)
            ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)

        _isFetching.value = true
        _error.value = null
        _attempts.value = emptyList()
        _parsedLyrics.value = emptyList()
        _selectedResult.value = null
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
                _querySourceLabel.value = when (currentSongState.querySource) {
                    OnlineLyricCacheStore.QuerySource.CUSTOM_OVERRIDE -> "当前使用: 自定义匹配信息"
                    OnlineLyricCacheStore.QuerySource.RAW_METADATA -> "当前使用: 原始播放器信息"
                    OnlineLyricCacheStore.QuerySource.DEFAULT_METADATA -> "当前使用: 当前解析后的播放信息"
                }
                if (queryTitle.isBlank() || queryArtist.isBlank()) {
                    _error.value = "没有可用的歌曲信息"
                    return@launch
                }

                if (!forceRefresh) {
                    val cacheHit = withContext(Dispatchers.IO) {
                        cacheStore.getCachedLyric(mediaInfo, queryTitle, queryArtist)
                    }
                    if (cacheHit != null) {
                        _selectedResult.value = cacheHit.result
                        _parsedLyrics.value = cacheHit.result.parsedLines ?: emptyList()
                        _attempts.value = emptyList()
                        _cacheStatus.value = "本次结果来自歌词缓存"
                        applyResultToRepository(mediaInfo, cacheHit.result, apiPath = "Online Cache")
                        return@launch
                    }
                }

                val outcome = fetcher.fetchLyrics(
                    title = queryTitle,
                    artist = queryArtist,
                    providerOrderIds = if (rule.useSmartOnlineLyricSelection) {
                        OnlineLyricProvider.defaultIds()
                    } else {
                        _providerOrder.value.orEmpty().map { it.id }
                    },
                    useSmartSelection = rule.useSmartOnlineLyricSelection
                )
                _attempts.value = outcome.attempts
                _usedCleanTitleFallback.value = outcome.usedCleanTitleFallback
                _selectedResult.value = outcome.bestResult
                _parsedLyrics.value = outcome.bestResult?.parsedLines ?: emptyList()
                if (outcome.bestResult == null) {
                    _error.value = "所有API都未返回结果"
                } else {
                    persistAndApplyResult(
                        mediaInfo = mediaInfo,
                        queryTitle = queryTitle,
                        queryArtist = queryArtist,
                        result = outcome.bestResult,
                        cacheMessage = "已切换到 ${outcome.bestResult.api} 并刷新歌词缓存"
                    )
                    AppLogger.getInstance().log("OnlineLyricDebug", "自动选择: ${outcome.bestResult.api} (${outcome.bestResult.score})")
                }
            } catch (e: Exception) {
                _error.value = "获取失败: ${e.message}"
            } finally {
                _isFetching.value = false
                syncCurrentSongQuery()
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
        val mediaInfo = liveMetadata.value ?: run {
            _error.value = "没有可用的歌曲信息"
            return
        }
        val result = attempt.result
        if (result == null || result.error != null || result.parsedLines.isNullOrEmpty()) {
            _error.value = "这个结果不可用，无法切换"
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
                persistAndApplyResult(
                    mediaInfo = mediaInfo,
                    queryTitle = currentSongState.effectiveTitle,
                    queryArtist = currentSongState.effectiveArtist,
                    result = result,
                    cacheMessage = "已切换到 ${result.api} 并写入歌词缓存"
                )
                _dialogAttempt.value = null
                AppLogger.getInstance().log("OnlineLyricDebug", "手动选择: ${result.api} (${result.score})")
            } catch (e: Exception) {
                _error.value = "切换失败: ${e.message}"
            } finally {
                _isFetching.value = false
                syncCurrentSongQuery()
            }
        }
    }
}
