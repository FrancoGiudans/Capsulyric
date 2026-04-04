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
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import kotlinx.coroutines.launch

class OnlineLyricDebugViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = LyricRepository.getInstance()
    private val fetcher = OnlineLyricFetcher()

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

    val liveMetadata = repo.liveMetadata
    val liveLyric = repo.liveLyric
    val liveProgress = repo.liveProgress
    val isPlaying = repo.isPlaying

    fun syncProviderOrderFromCurrentRule() {
        val pkg = liveMetadata.value?.packageName ?: return
        val rule = ParserRuleHelper.getRuleForPackage(getApplication(), pkg)
            ?: ParserRuleHelper.createDefaultRule(pkg)
        _useSmartSelection.value = rule.useSmartOnlineLyricSelection
        _providerOrder.value = OnlineLyricProvider.normalizeOrder(rule.onlineLyricProviderOrder)
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

    fun fetchLyrics() {
        val mediaInfo = liveMetadata.value
        if (mediaInfo == null) {
            _error.value = "没有可用的歌曲信息"
            return
        }
        val rule = ParserRuleHelper.getRuleForPackage(getApplication(), mediaInfo.packageName)
            ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)
        val queryTitle = if (rule.useRawMetadataForOnlineMatching) mediaInfo.rawTitle else mediaInfo.title
        val queryArtist = if (rule.useRawMetadataForOnlineMatching) mediaInfo.rawArtist else mediaInfo.artist
        if (queryTitle.isBlank() || queryArtist.isBlank()) {
            _error.value = "没有可用的歌曲信息"
            return
        }

        _isFetching.value = true
        _error.value = null
        _attempts.value = emptyList()
        _parsedLyrics.value = emptyList()
        _selectedResult.value = null
        _usedCleanTitleFallback.value = false

        viewModelScope.launch {
            try {
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
                    AppLogger.getInstance().log("OnlineLyricDebug", "自动选择: ${outcome.bestResult.api} (${outcome.bestResult.score})")
                }
            } catch (e: Exception) {
                _error.value = "获取失败: ${e.message}"
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
}
