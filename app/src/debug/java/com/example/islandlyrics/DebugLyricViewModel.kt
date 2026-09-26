package com.example.islandlyrics

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import kotlinx.coroutines.launch

class DebugLyricViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LyricRepository.getInstance()
    private val fetcher = OnlineLyricFetcher()

    private val _apiResults = MutableLiveData<List<OnlineLyricFetcher.LyricResult>>(emptyList())
    val apiResults: LiveData<List<OnlineLyricFetcher.LyricResult>> = _apiResults

    private val _selectedResult = MutableLiveData<OnlineLyricFetcher.LyricResult?>(null)
    val selectedResult: LiveData<OnlineLyricFetcher.LyricResult?> = _selectedResult

    private val _isFetching = MutableLiveData(false)
    val isFetching: LiveData<Boolean> = _isFetching

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _providerOrder = MutableLiveData(OnlineLyricProvider.defaultOrder())
    val providerOrder: LiveData<List<OnlineLyricProvider>> = _providerOrder

    private val _attempts = MutableLiveData<List<OnlineLyricFetcher.ProviderAttempt>>(emptyList())
    val attempts: LiveData<List<OnlineLyricFetcher.ProviderAttempt>> = _attempts

    private val _usedCleanTitleFallback = MutableLiveData(false)
    val usedCleanTitleFallback: LiveData<Boolean> = _usedCleanTitleFallback

    // State for live preview
    private val _parsedLyrics = MutableLiveData<List<OnlineLyricFetcher.LyricLine>>(emptyList())
    val parsedLyrics: LiveData<List<OnlineLyricFetcher.LyricLine>> = _parsedLyrics

    val liveMetadata = repo.liveMetadata
    val liveLyric = repo.liveLyric
    val liveProgress = repo.liveProgress
    val isPlaying = repo.isPlaying

    fun syncProviderOrderFromCurrentRule() {
        val pkg = liveMetadata.value?.packageName ?: return
        val rule = ParserRuleHelper.getRuleForPackage(getApplication(), pkg)
            ?: ParserRuleHelper.createDefaultRule(pkg)
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
        if (mediaInfo == null || mediaInfo.title.isBlank() || mediaInfo.artist.isBlank()) {
            _error.value = "没有可用的歌曲信息"
            return
        }

        _isFetching.value = true
        _error.value = null
        _apiResults.value = emptyList()
        _attempts.value = emptyList()
        _parsedLyrics.value = emptyList()
        _selectedResult.value = null
        _usedCleanTitleFallback.value = false

        viewModelScope.launch {
            try {
                val outcome = fetcher.fetchLyrics(
                    title = mediaInfo.title,
                    artist = mediaInfo.artist,
                    providerOrderIds = _providerOrder.value.orEmpty().map { it.id }
                )
                val filtered = outcome.attempts.mapNotNull { it.result }.sortedByDescending { it.score }
                _apiResults.value = filtered
                _attempts.value = outcome.attempts
                _usedCleanTitleFallback.value = outcome.usedCleanTitleFallback
                val best = outcome.bestResult
                if (best != null) {
                    _selectedResult.value = best
                    _parsedLyrics.value = best.parsedLines ?: emptyList()
                    AppLogger.getInstance().log("DebugLyric", "自动选择: ${best.api} (分: ${best.score})")
                } else {
                    _error.value = "所有API都未返回结果"
                }
            } catch (e: Exception) {
                _error.value = "获取失败: ${e.message}"
            } finally {
                _isFetching.value = false
            }
        }
    }
}

