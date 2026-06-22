package com.example.islandlyrics.runtime.playingapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.runtime.service.MediaMonitorService

object NewPlayingAppNotifier {
    const val PREF_ENABLED = AppPreferences.Keys.NEW_PLAYING_APP_ALERT_ENABLED
    const val ACTION_ADD_APP = "com.example.islandlyrics.action.ADD_NEW_PLAYING_APP"
    const val ACTION_IGNORE_APP = "com.example.islandlyrics.action.IGNORE_NEW_PLAYING_APP"
    const val EXTRA_PACKAGE_NAME = "package_name"
    const val EXTRA_APP_NAME = "app_name"

    private const val TAG = "NewPlayingAppNotifier"
    private const val PREF_IGNORED_PACKAGES = AppPreferences.Keys.NEW_PLAYING_APP_ALERT_IGNORED_PACKAGES
    private const val PREF_ALERTED_PACKAGES = AppPreferences.Keys.NEW_PLAYING_APP_ALERT_ALERTED_PACKAGES
    private const val PREF_LAST_NOTIFY_TIME = AppPreferences.Keys.NEW_PLAYING_APP_ALERT_LAST_NOTIFY_TIME
    private const val CHANNEL_ID = "new_playing_app_alerts"
    private const val NOTIFICATION_ID = 2002
    private const val NOTIFY_COOLDOWN_MS = 30 * 60 * 1000L // 30 minutes

    fun maybeNotify(
        context: Context,
        controllers: List<MediaController>,
        configuredPackages: Set<String>
    ) {
        val prefs = AppPreferences.of(context)
        if (!prefs.getBoolean(PREF_ENABLED, false)) return
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        // Cooldown: don't notify more than once per NOTIFY_COOLDOWN_MS
        val now = SystemClock.uptimeMillis()
        val lastNotifyTime = prefs.getLong(PREF_LAST_NOTIFY_TIME, 0L)
        if (now - lastNotifyTime < NOTIFY_COOLDOWN_MS) return

        val ignoredPackages = prefs.getStringSet(PREF_IGNORED_PACKAGES, emptySet()) ?: emptySet()
        val alertedPackages = prefs.getStringSet(PREF_ALERTED_PACKAGES, emptySet()) ?: emptySet()
        val candidate = controllers.firstOrNull { controller ->
            val packageName = controller.packageName
            packageName.isNotBlank() &&
                packageName != context.packageName &&
                !configuredPackages.contains(packageName) &&
                !ignoredPackages.contains(packageName) &&
                !alertedPackages.contains(packageName) &&
                isPlaying(controller.playbackState?.state)
        } ?: return

        val packageName = candidate.packageName
        val appName = resolveAppName(context, packageName)
        createChannel(context)

        val appLine = context.getString(
            R.string.new_playing_app_notification_text_fmt,
            appName,
            packageName
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(context.getString(R.string.new_playing_app_notification_title))
            .setContentText(appLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(appLine))
            .setContentIntent(createContentIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .addAction(
                0,
                context.getString(R.string.new_playing_app_action_add),
                createActionIntent(context, ACTION_ADD_APP, packageName, appName, 1)
            )
            .addAction(
                0,
                context.getString(R.string.new_playing_app_action_ignore),
                createActionIntent(context, ACTION_IGNORE_APP, packageName, appName, 2)
            )
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            val updatedAlerted = (prefs.getStringSet(PREF_ALERTED_PACKAGES, emptySet()) ?: emptySet()).toMutableSet()
            updatedAlerted.add(packageName)
            prefs.edit {
                putStringSet(PREF_ALERTED_PACKAGES, updatedAlerted)
                putLong(PREF_LAST_NOTIFY_TIME, now)
            }
            AppLogger.getInstance().log(TAG, "Prompted for unconfigured playing app: $packageName")
        } catch (e: SecurityException) {
            AppLogger.getInstance().e(TAG, "Cannot post new app prompt: ${e.message}")
        }
    }

    fun addApp(context: Context, packageName: String, appName: String?) {
        val cleanName = appName?.takeIf { it.isNotBlank() } ?: resolveAppName(context, packageName)
        ParserRuleHelper.updateRule(context, packageName) { current ->
            current.copy(
                enabled = true,
                customName = current.customName?.takeIf { it.isNotBlank() } ?: cleanName
            )
        }
        removeIgnoredPackage(context, packageName)
        cancelAll(context)
        MediaMonitorService.triggerRecheck()
        Toast.makeText(
            context,
            context.getString(R.string.new_playing_app_added_to_rules, cleanName),
            Toast.LENGTH_SHORT
        ).show()
        AppLogger.getInstance().log(TAG, "Added unconfigured playing app: $packageName")
    }

    fun ignoreApp(context: Context, packageName: String) {
        val prefs = AppPreferences.of(context)
        val ignored = (prefs.getStringSet(PREF_IGNORED_PACKAGES, emptySet()) ?: emptySet()).toMutableSet()
        ignored.add(packageName)
        prefs.edit { putStringSet(PREF_IGNORED_PACKAGES, ignored) }
        cancelAll(context)
        AppLogger.getInstance().log(TAG, "Ignored unconfigured playing app prompt: $packageName")
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun removeIgnoredPackage(context: Context, packageName: String) {
        val prefs = AppPreferences.of(context)
        val ignored = (prefs.getStringSet(PREF_IGNORED_PACKAGES, emptySet()) ?: emptySet()).toMutableSet()
        if (ignored.remove(packageName)) {
            prefs.edit { putStringSet(PREF_IGNORED_PACKAGES, ignored) }
        }
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_new_playing_app_alerts),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_new_playing_app_alerts_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createActionIntent(
        context: Context,
        action: String,
        packageName: String,
        appName: String,
        requestCodeOffset: Int
    ): PendingIntent {
        val intent = Intent(context, NewPlayingAppActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_APP_NAME, appName)
        }
        return PendingIntent.getBroadcast(
            context,
            packageName.hashCode() + requestCodeOffset,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun resolveAppName(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun isPlaying(state: Int?): Boolean {
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }
}
