package com.example.islandlyrics.feature.cache

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.islandlyrics.core.cache.AppImageCacheManager
import com.example.islandlyrics.data.lyric.OnlineLyricCacheStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CacheManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val lyricCacheStore = OnlineLyricCacheStore(application)

    private val _lyricStats = MutableLiveData(OnlineLyricCacheStore.LyricCacheStats())
    val lyricStats: LiveData<OnlineLyricCacheStore.LyricCacheStats> = _lyricStats

    private val _lyricEntries = MutableLiveData<List<OnlineLyricCacheStore.LyricCacheEntrySummary>>(emptyList())
    val lyricEntries: LiveData<List<OnlineLyricCacheStore.LyricCacheEntrySummary>> = _lyricEntries

    private val _imageStats = MutableLiveData(AppImageCacheManager.ImageCacheStats())
    val imageStats: LiveData<AppImageCacheManager.ImageCacheStats> = _imageStats

    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy

    private val _statusMessage = MutableLiveData<String?>(null)
    val statusMessage: LiveData<String?> = _statusMessage

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            val lyricStats = withContext(Dispatchers.IO) { lyricCacheStore.getLyricCacheStats() }
            val lyricEntries = withContext(Dispatchers.IO) { lyricCacheStore.getLyricCacheSummaries() }
            val imageStats = withContext(Dispatchers.IO) { AppImageCacheManager.getStats(getApplication()) }
            _lyricStats.value = lyricStats
            _lyricEntries.value = lyricEntries
            _imageStats.value = imageStats
            _busy.value = false
        }
    }

    fun deleteLyricEntry(entryId: String) {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                lyricCacheStore.deleteLyricEntry(entryId)
            }
            _statusMessage.value = "已删除歌词缓存"
            refresh()
        }
    }

    fun clearLyricCache() {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                lyricCacheStore.clearLyricCache()
            }
            _statusMessage.value = "已清空歌词缓存"
            refresh()
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                AppImageCacheManager.clear(getApplication())
            }
            _statusMessage.value = "已清空图片缓存"
            refresh()
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                lyricCacheStore.clearLyricCache()
                AppImageCacheManager.clear(getApplication())
            }
            _statusMessage.value = "已清空全部缓存"
            refresh()
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }
}
