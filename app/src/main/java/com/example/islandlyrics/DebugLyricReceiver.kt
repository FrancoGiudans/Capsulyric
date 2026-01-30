package com.example.islandlyrics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DebugLyricReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return

        if ("com.franco.capsulyric.DEBUG_UPDATE" == intent.action) {
            val title = intent.getStringExtra("title")
            val artist = intent.getStringExtra("artist")
            val lyric = intent.getStringExtra("lyric")

            Log.d(TAG, "Debug broadcast received: $lyric")

            val serviceIntent = Intent(context, LyricService::class.java)
            serviceIntent.action = "FORCE_UPDATE_UI"
            serviceIntent.putExtra("title", title)
            serviceIntent.putExtra("artist", artist)
            serviceIntent.putExtra("lyric", lyric)
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "DebugLyricReceiver"
    }
}
