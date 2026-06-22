package com.example.islandlyrics.runtime.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.runtime.service.LyricService

internal class LyricMediaCommandRouter(
    private val service: LyricService,
    private val mediaActionController: MediaActionController
) {
    private val mediaActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleBroadcastAction(intent?.action)
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(ACTION_BROADCAST_PLAY_PAUSE)
            addAction(ACTION_BROADCAST_NEXT)
            addAction(ACTION_BROADCAST_PREVIOUS)
            addAction(ACTION_MIUI_PLAY_PAUSE)
            addAction(ACTION_MIUI_NEXT)
            addAction(ACTION_MIUI_PREVIOUS)
        }
        service.registerReceiver(mediaActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    fun unregister() {
        service.unregisterReceiver(mediaActionReceiver)
    }

    fun handleServiceAction(action: String): Boolean {
        when (action) {
            ACTION_SERVICE_PAUSE -> mediaActionController.pause()
            ACTION_SERVICE_PLAY -> mediaActionController.play()
            ACTION_SERVICE_NEXT -> mediaActionController.next()
            ACTION_SERVICE_PREVIOUS -> mediaActionController.previous()
            else -> return false
        }
        return true
    }

    private fun handleBroadcastAction(action: String?) {
        when (action) {
            ACTION_BROADCAST_PLAY_PAUSE,
            ACTION_MIUI_PLAY_PAUSE -> {
                val playing = LyricRepository.getInstance().isPlaying.value ?: false
                if (playing) mediaActionController.pause() else mediaActionController.play()
            }
            ACTION_BROADCAST_NEXT,
            ACTION_MIUI_NEXT -> mediaActionController.next()
            ACTION_BROADCAST_PREVIOUS,
            ACTION_MIUI_PREVIOUS -> mediaActionController.previous()
        }
    }

    private companion object {
        private const val ACTION_SERVICE_PAUSE = "ACTION_MEDIA_PAUSE"
        private const val ACTION_SERVICE_PLAY = "ACTION_MEDIA_PLAY"
        private const val ACTION_SERVICE_NEXT = "ACTION_MEDIA_NEXT"
        private const val ACTION_SERVICE_PREVIOUS = "ACTION_MEDIA_PREV"
        private const val ACTION_BROADCAST_PLAY_PAUSE = "com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE"
        private const val ACTION_BROADCAST_NEXT = "com.example.islandlyrics.ACTION_MEDIA_NEXT"
        private const val ACTION_BROADCAST_PREVIOUS = "com.example.islandlyrics.ACTION_MEDIA_PREV"
        private const val ACTION_MIUI_PLAY_PAUSE = "miui.focus.action_play_pause"
        private const val ACTION_MIUI_NEXT = "miui.focus.action_next"
        private const val ACTION_MIUI_PREVIOUS = "miui.focus.action_prev"
    }
}
