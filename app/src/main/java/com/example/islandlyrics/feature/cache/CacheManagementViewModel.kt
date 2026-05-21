package com.example.islandlyrics.feature.cache

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.islandlyrics.R
import com.example.islandlyrics.core.cache.AppImageCacheManager
import com.example.islandlyrics.data.lyric.LyricExporter
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

    private val _selectedIds = MutableLiveData<Set<String>>(emptySet())
    val selectedIds: LiveData<Set<String>> = _selectedIds

    private val _isSelectionMode = MutableLiveData(false)
    val isSelectionMode: LiveData<Boolean> = _isSelectionMode

    private fun s(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

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

    fun enterSelectionMode(firstId: String) {
        _isSelectionMode.value = true
        _selectedIds.value = setOf(firstId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: String) {
        val current = _selectedIds.value.orEmpty().toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
        if (current.isEmpty()) exitSelectionMode()
    }

    fun selectAll() {
        _selectedIds.value = _lyricEntries.value.orEmpty().map { it.id }.toSet()
    }

    fun exportEntry(entryId: String) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { lyricCacheStore.getEntryMetadata(entryId) }
            val lines = withContext(Dispatchers.IO) { lyricCacheStore.getEntryParsedLines(entryId) }
            if (meta == null || lines == null) {
                _statusMessage.value = s(R.string.export_lyric_no_lyrics)
                return@launch
            }
            val result = LyricExporter.exportCacheEntry(getApplication(), meta.first, meta.second, lines)
            _statusMessage.value = when {
                result.success -> s(R.string.export_lyric_success, result.fileName ?: "")
                result.error == "no_directory" -> s(R.string.export_lyric_no_directory)
                else -> s(R.string.export_lyric_failed)
            }
        }
    }

    fun exportSelected() {
        val ids = _selectedIds.value.orEmpty()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            var successCount = 0
            for (id in ids) {
                val meta = withContext(Dispatchers.IO) { lyricCacheStore.getEntryMetadata(id) } ?: continue
                val lines = withContext(Dispatchers.IO) { lyricCacheStore.getEntryParsedLines(id) } ?: continue
                val result = LyricExporter.exportCacheEntry(getApplication(), meta.first, meta.second, lines)
                if (result.success) successCount++
            }
            _statusMessage.value = s(R.string.cache_management_export_batch_result, successCount, ids.size)
            _busy.value = false
            exitSelectionMode()
        }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.orEmpty()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                for (id in ids) {
                    lyricCacheStore.deleteLyricEntry(id)
                }
            }
            _statusMessage.value = s(R.string.cache_management_delete_batch_result, ids.size)
            exitSelectionMode()
            refresh()
        }
    }

    fun deleteLyricEntry(entryId: String) {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                lyricCacheStore.deleteLyricEntry(entryId)
            }
            _statusMessage.value = s(R.string.cache_management_status_deleted)
            refresh()
        }
    }

    fun clearLyricCache() {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                lyricCacheStore.clearLyricCache()
            }
            _statusMessage.value = s(R.string.cache_management_status_lyrics_cleared)
            refresh()
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) {
                AppImageCacheManager.clear(getApplication())
            }
            _statusMessage.value = s(R.string.cache_management_status_images_cleared)
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
            _statusMessage.value = s(R.string.cache_management_status_all_cleared)
            refresh()
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }
}
