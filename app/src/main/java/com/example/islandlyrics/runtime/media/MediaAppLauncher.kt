package com.example.islandlyrics.runtime.media

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.islandlyrics.R

internal fun openMediaApp(
    context: Context,
    controller: MediaController,
    appName: String,
    onOpened: (() -> Unit)? = null
) {
    val packageName = controller.packageName
    val opened = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return@runCatching false
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false) || openMediaAppFallback(context, controller)

    if (opened) {
        onOpened?.invoke()
    } else {
        val message = context.getString(R.string.media_control_cannot_open, appName.ifBlank { packageName })
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

private fun openMediaAppFallback(context: Context, controller: MediaController): Boolean {
    @Suppress("DEPRECATION")
    val launchMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
    } else {
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
    }
    val options = ActivityOptions.makeBasic()
        .setPendingIntentBackgroundActivityStartMode(launchMode)
        .toBundle()
    val packageName = controller.packageName
    return runCatching {
        val launcher = context.packageManager.getLaunchIntentSenderForPackage(packageName)
        context.startIntentSender(launcher, null, 0, 0, 0, options)
    }.recoverCatching { launchFailure ->
        val sessionActivity = controller.sessionActivity
            ?.takeIf { it.isActivity && it.creatorPackage == packageName }
            ?: throw launchFailure
        sessionActivity.send(context, 0, null, null, null, null, options)
    }.onFailure {
        Log.w("MediaAppLauncher", "Unable to open playback app $packageName", it)
    }.isSuccess
}
