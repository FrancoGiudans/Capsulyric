package com.example.islandlyrics

import android.content.BroadcastReceiver
import java.util.Arrays
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MainActivity : BaseActivity() {

    // UI Elements
    private lateinit var cardStatus: MaterialCardView
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusText: TextView
    private lateinit var tvAppVersion: TextView

    private lateinit var tvSong: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvLyric: TextView

    // Dashboard UI
    private lateinit var tvApiPermission: TextView
    private lateinit var tvNotifCapability: TextView
    private lateinit var tvNotifFlag: TextView
    private lateinit var btnOpenPromotedSettings: MaterialButton
    private lateinit var pbProgress: com.google.android.material.progressindicator.LinearProgressIndicator

    private val handler = Handler(Looper.getMainLooper())


    private val diagReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ("com.example.islandlyrics.DIAG_UPDATE" == intent.action) {
                // ... logic for raw logs if needed
            } else if ("com.example.islandlyrics.STATUS_UPDATE" == intent.action) {

                // General Service Status
                val status = intent.getStringExtra("status")
                val tvStatus = findViewById<TextView>(R.id.tv_service_status)
                if (tvStatus != null && status != null) {
                    tvStatus.text = "Status: $status"
                }

                // API Dashboard Data (from LyricService inspection)
                if (intent.hasExtra("hasPromotable")) {
                    val hasPromotable = intent.getBooleanExtra("hasPromotable", false)
                    tvNotifCapability.text = "Notif.hasPromotable: $hasPromotable"
                    tvNotifCapability.setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (hasPromotable) R.color.status_active else R.color.status_inactive
                        )
                    )
                }

                if (intent.hasExtra("isPromoted")) {
                    val isPromoted = intent.getBooleanExtra("isPromoted", false)
                    tvNotifFlag.text = "Flag PROMOTED_ONGOING: $isPromoted"
                    tvNotifFlag.setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (isPromoted) R.color.status_active else R.color.status_inactive
                        )
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle Edge-to-Edge / Status Bar Insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeDefaultWhitelist()
        bindViews()
        setupClickListeners()
        setupObservers()

        checkPromotedNotificationPermission()
        updateVersionInfo()
    }

    // API 36 Permission Check (Standard Runtime Permission)
    private fun checkPromotedNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 36) {
            if (checkSelfPermission("android.permission.POST_PROMOTED_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf("android.permission.POST_PROMOTED_NOTIFICATIONS"), 102)
            }
        }
    }

    private fun initializeDefaultWhitelist() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.contains(PREF_WHITELIST)) {
            val set = HashSet(Arrays.asList(*DEFAULT_WHITELIST))
            prefs.edit().putStringSet(PREF_WHITELIST, set).apply()
        }
    }

    private fun bindViews() {
        cardStatus = findViewById(R.id.cv_status)
        ivStatusIcon = findViewById(R.id.iv_status_icon)
        tvStatusText = findViewById(R.id.tv_status_text)

        tvAppVersion = findViewById(R.id.tv_app_version)

        tvSong = findViewById(R.id.tv_song)
        tvArtist = findViewById(R.id.tv_artist)
        tvLyric = findViewById(R.id.tv_lyric)

        // Dashboard Bindings
        tvApiPermission = findViewById(R.id.tv_api_permission)
        tvNotifCapability = findViewById(R.id.tv_notif_capability)
        tvNotifFlag = findViewById(R.id.tv_notif_flag)
        btnOpenPromotedSettings = findViewById(R.id.btn_open_settings)
        pbProgress = findViewById(R.id.pb_progress)
    }

    private fun setupClickListeners() {
        val cvSettings = findViewById<View>(R.id.cv_settings)
        cvSettings?.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // Open Promoted Settings
        btnOpenPromotedSettings.setOnClickListener {
            try {
                // Try specific API 36 Settings Action
                val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to App Notification Settings
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(intent)
                Toast.makeText(this, "Promoted Settings not found, opening Notification Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToClipboard(label: String, text: String?) {
        if (!text.isNullOrEmpty() && text != "-") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "$label copied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusCardState() {
        // Check Permission
        val listenerGranted = isNotificationListenerEnabled()

        if (!listenerGranted) {
            // State: Missing Permission
            setCardState(false, "Permission Required", R.color.status_inactive) // Red/Orange
        } else {
            // Check Playing Status
            val isPlaying = LyricRepository.getInstance().isPlaying.value
            if (java.lang.Boolean.TRUE == isPlaying) {
                val source = LyricRepository.getInstance().sourceAppName.value
                setCardState(true, "Active: " + (source ?: "Music"), R.color.status_active)
            } else {
                setCardState(true, "Service Ready (Idle)", R.color.status_active) // Or distinct color?
            }
        }
    }

    private fun setCardState(isActive: Boolean, text: String, colorResId: Int) {
        cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, colorResId))
        tvStatusText.text = text

        if (isActive) {
            ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
        } else {
            ivStatusIcon.setImageResource(R.drawable.ic_cancel)
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        return Settings.Secure.getString(contentResolver, "enabled_notification_listeners").contains(packageName)
    }

    private fun updateVersionInfo() {
        try {
            val version = packageManager.getPackageInfo(packageName, 0).versionName
            val type = if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) "Debug" else "Release"
            tvAppVersion.text = getString(R.string.app_version_fmt, version, type)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }

    // Check API Status (Reflection)
    private fun checkApiStatusForDashboard() {
        // Only show in Debug builds
        val cvApiStatus = findViewById<View>(R.id.cv_api_status)
        if (!BuildConfig.DEBUG) {
            cvApiStatus.visibility = View.GONE
            return
        }
        cvApiStatus.visibility = View.VISIBLE

        if (Build.VERSION.SDK_INT < 36) {
            tvApiPermission.text = "Permission: N/A (Pre-API 36)"
            tvNotifCapability.text = "Notif.hasPromotable: N/A"
            tvNotifFlag.text = "Flag PROMOTED: N/A"
            return
        }

        try {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            val m = nm.javaClass.getMethod("canPostPromotedNotifications")
            val granted = m.invoke(nm) as Boolean

            if (granted) {
                tvApiPermission.text = "Permission (canPost): GRANTED ✅"
                tvApiPermission.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            } else {
                tvApiPermission.text = "Permission (canPost): DENIED ❌"
                tvApiPermission.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
            }
        } catch (e: Exception) {
            tvApiPermission.text = "Permission Check Failed: ${e.message}"
        }
    }

    private fun updateDebugProgress() {
        // No-op for now as UI removed
    }

    private fun formatTime(ms: Long): String {
        var seconds = ms / 1000
        val minutes = seconds / 60
        seconds = seconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun setupObservers() {
        val repo = LyricRepository.getInstance()

        // Playback Status Observer
        repo.isPlaying.observe(this) { _ ->
            updateStatusCardState()
        }

        // Lyric Observer
        repo.currentLyric.observe(this) { lyric ->
            if (lyric != null) {
                tvLyric.text = lyric
            } else {
                tvLyric.text = "-"
            }
        }

        // Metadata Observer
        repo.liveMetadata.observe(this) { mediaInfo ->
            if (mediaInfo == null) return@observe
            // Ensure defaults if null
            tvSong.text = mediaInfo.title
            tvArtist.text = mediaInfo.artist
        }
        
        // Progress Observer
        repo.liveProgress.observe(this) { progress ->
            if (progress != null && progress.duration > 0) {
                pbProgress.max = progress.duration.toInt()
                pbProgress.setProgress(progress.position.toInt(), true)
            } else {
                pbProgress.progress = 0
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusCardState()
        checkApiStatusForDashboard()

        checkApiStatusForDashboard()

        // Register Diag Receiver
        val filter = IntentFilter()
        filter.addAction("com.example.islandlyrics.DIAG_UPDATE")
        filter.addAction("com.example.islandlyrics.STATUS_UPDATE")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(diagReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(diagReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(diagReceiver)
        } catch (e: Exception) {
        }
    }

    companion object {
        private const val TAG = "IslandLyrics"
        private const val PREFS_NAME = "IslandLyricsPrefs"
        private const val PREF_WHITELIST = "whitelist_packages"

        // Default Whitelist
        private val DEFAULT_WHITELIST = arrayOf(
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
            "com.kugou.android",
            "com.spotify.music",
            "com.apple.android.music",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music"
        )
    }
}
