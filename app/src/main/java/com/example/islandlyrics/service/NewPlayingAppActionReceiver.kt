package com.example.islandlyrics.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NewPlayingAppActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(NewPlayingAppNotifier.EXTRA_PACKAGE_NAME) ?: return
        val appName = intent.getStringExtra(NewPlayingAppNotifier.EXTRA_APP_NAME)

        when (intent.action) {
            NewPlayingAppNotifier.ACTION_ADD_APP -> {
                NewPlayingAppNotifier.addApp(context, packageName, appName)
            }
            NewPlayingAppNotifier.ACTION_IGNORE_APP -> {
                NewPlayingAppNotifier.ignoreApp(context, packageName)
            }
        }
    }
}
