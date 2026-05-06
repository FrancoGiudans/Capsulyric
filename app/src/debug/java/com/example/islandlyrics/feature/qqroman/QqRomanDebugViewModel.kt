package com.example.islandlyrics.feature.qqroman

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.NeteaseRomanFetcher
import com.example.islandlyrics.data.lyric.QqRomanFetcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class QqRomanDebugViewModel(application: Application) : AndroidViewModel(application) {
    enum class DebugSource(val displayName: String) {
        QQ("QQ 音乐"),
        Netease("网易云")
    }

    data class UiState(
        val queryTitle: String = "",
        val queryArtist: String = "",
        val loading: Boolean = false,
        val error: String? = null,
        val selectedSource: DebugSource = DebugSource.QQ,
        val qqResult: QqRomanFetcher.Result? = null,
        val neteaseResult: NeteaseRomanFetcher.Result? = null
    )

    private val repo = LyricRepository.getInstance()
    private val qqFetcher = QqRomanFetcher()
    private val neteaseFetcher = NeteaseRomanFetcher()
    private val _uiState = MutableLiveData(UiState())
    val uiState: LiveData<UiState> = _uiState
    val liveMetadata = repo.liveMetadata
    val liveLyric = repo.liveLyric
    val superLyricDebug = repo.liveSuperLyricDebug

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

    fun updateSource(value: DebugSource) {
        _uiState.value = _uiState.value?.copy(selectedSource = value, error = null)
    }

    fun fetch() {
        val state = _uiState.value ?: UiState()
        val title = state.queryTitle.trim()
        val artist = state.queryArtist.trim()
        if (title.isBlank()) {
            _uiState.value = state.copy(error = "请先输入歌名")
            return
        }

        _uiState.value = state.copy(loading = true, error = null)
        viewModelScope.launch {
            when (state.selectedSource) {
                DebugSource.QQ -> {
                    val result = qqFetcher.fetchRomanLyrics(title, artist)
                    val current = _uiState.value ?: state
                    _uiState.value = current.copy(
                        queryTitle = title,
                        queryArtist = artist,
                        loading = false,
                        error = if (result == null) "未抓取到 QQ 音乐罗马音/翻译，可能该歌曲没有对应字段或搜索未命中" else null,
                        qqResult = result
                    )
                }

                DebugSource.Netease -> {
                    val result = neteaseFetcher.fetchRomanLyrics(title, artist)
                    val current = _uiState.value ?: state
                    _uiState.value = current.copy(
                        queryTitle = title,
                        queryArtist = artist,
                        loading = false,
                        error = if (result == null) "未抓取到 网易云罗马音/翻译，可能该歌曲没有对应字段或搜索未命中" else null,
                        neteaseResult = result
                    )
                }
            }
        }
    }

    fun fetchAll() {
        val state = _uiState.value ?: UiState()
        val title = state.queryTitle.trim()
        val artist = state.queryArtist.trim()
        if (title.isBlank()) {
            _uiState.value = state.copy(error = "请先输入歌名")
            return
        }

        _uiState.value = state.copy(loading = true, error = null)
        viewModelScope.launch {
            val qq = async { qqFetcher.fetchRomanLyrics(title, artist) }
            val netease = async { neteaseFetcher.fetchRomanLyrics(title, artist) }
            val qqResult = qq.await()
            val neteaseResult = netease.await()
            _uiState.value = UiState(
                queryTitle = title,
                queryArtist = artist,
                selectedSource = state.selectedSource,
                loading = false,
                error = if (qqResult == null && neteaseResult == null) {
                    "QQ 音乐和网易云都没有抓取到罗马音/翻译"
                } else null,
                qqResult = qqResult,
                neteaseResult = neteaseResult
            )
        }
    }

    fun clearResults() {
        _uiState.value = _uiState.value?.copy(
            error = null,
            qqResult = null,
            neteaseResult = null
        )
    }
}
