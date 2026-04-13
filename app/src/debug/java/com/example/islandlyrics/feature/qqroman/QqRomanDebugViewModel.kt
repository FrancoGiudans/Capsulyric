package com.example.islandlyrics.feature.qqroman

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.QqRomanFetcher
import kotlinx.coroutines.launch

class QqRomanDebugViewModel(application: Application) : AndroidViewModel(application) {
    data class UiState(
        val queryTitle: String = "",
        val queryArtist: String = "",
        val loading: Boolean = false,
        val error: String? = null,
        val result: QqRomanFetcher.Result? = null
    )

    private val repo = LyricRepository.getInstance()
    private val fetcher = QqRomanFetcher()
    private val _uiState = MutableLiveData(UiState())
    val uiState: LiveData<UiState> = _uiState
    val liveMetadata = repo.liveMetadata

    fun syncFromCurrentSong() {
        val metadata = repo.liveMetadata.value ?: return
        _uiState.value = _uiState.value?.copy(
            queryTitle = metadata.title,
            queryArtist = metadata.artist,
            error = null
        )
    }

    fun updateTitle(value: String) {
        _uiState.value = _uiState.value?.copy(queryTitle = value, error = null)
    }

    fun updateArtist(value: String) {
        _uiState.value = _uiState.value?.copy(queryArtist = value, error = null)
    }

    fun fetch() {
        val state = _uiState.value ?: UiState()
        val title = state.queryTitle.trim()
        val artist = state.queryArtist.trim()
        if (title.isBlank()) {
            _uiState.value = state.copy(error = "请先输入歌名")
            return
        }

        _uiState.value = state.copy(loading = true, error = null, result = null)
        viewModelScope.launch {
            val result = fetcher.fetchRomanLyrics(title, artist)
            _uiState.value = if (result == null) {
                UiState(
                    queryTitle = title,
                    queryArtist = artist,
                    loading = false,
                    error = "未抓取到 QQ 罗马音，可能该歌曲没有 `contentroma` 或搜索未命中",
                    result = null
                )
            } else {
                UiState(
                    queryTitle = title,
                    queryArtist = artist,
                    loading = false,
                    error = null,
                    result = result
                )
            }
        }
    }
}
