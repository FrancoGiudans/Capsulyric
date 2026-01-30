package com.example.islandlyrics

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.islandlyrics.ThemeHelper.isDynamicColorEnabled
import com.example.islandlyrics.ThemeHelper.isPureBlackEnabled
import com.example.islandlyrics.ThemeHelper.setDarkMode
import com.example.islandlyrics.ThemeHelper.setDynamicColor
import com.example.islandlyrics.ThemeHelper.setFollowSystem
import com.example.islandlyrics.ThemeHelper.setLanguage
import com.example.islandlyrics.ThemeHelper.setPureBlack
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : BaseActivity() {

    // Service & General UI References
    private lateinit var itemServiceSwitch: View
    private lateinit var switchMaster: MaterialSwitch

    private lateinit var itemNotificationPerm: View
    private lateinit var switchNotification: MaterialSwitch

    private lateinit var itemPostNotificationPerm: View
    private lateinit var switchPostNotification: MaterialSwitch

    // Help & Guide UI
    private lateinit var itemGuide0Hook: View

    private lateinit var itemWhitelist: View
    private lateinit var itemBattery: View
    private lateinit var itemGithub: View
    private lateinit var itemLogs: View

    // Appearance UI References
    private lateinit var itemLanguage: View
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var itemFollowSystem: View
    private lateinit var switchFollowSystem: MaterialSwitch
    private lateinit var itemDarkMode: View
    private lateinit var switchDarkMode: MaterialSwitch
    private lateinit var itemPureBlack: View
    private lateinit var switchPureBlack: MaterialSwitch
    private lateinit var itemDynamicColor: View
    private lateinit var switchDynamicColor: MaterialSwitch

    // Developer Mode State
    private var devClickCount = 0
    private var lastDevClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Handle Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Bind Views
        itemServiceSwitch = findViewById(R.id.item_service_switch)
        switchMaster = findViewById(R.id.switch_master)

        itemNotificationPerm = findViewById(R.id.item_notification_perm)
        switchNotification = findViewById(R.id.switch_notification)

        itemPostNotificationPerm = findViewById(R.id.item_post_notification_perm)
        switchPostNotification = findViewById(R.id.switch_post_notification)

        itemGuide0Hook = findViewById(R.id.item_guide_0_hook)

        // Bind Appearance Views
        itemLanguage = findViewById(R.id.item_language)
        tvCurrentLanguage = findViewById(R.id.tv_current_language)
        itemFollowSystem = findViewById(R.id.item_theme_follow_system)
        switchFollowSystem = findViewById(R.id.switch_theme_follow_system)
        itemDarkMode = findViewById(R.id.item_theme_dark_mode)
        switchDarkMode = findViewById(R.id.switch_theme_dark_mode)
        itemPureBlack = findViewById(R.id.item_theme_pure_black)
        switchPureBlack = findViewById(R.id.switch_theme_pure_black)
        itemDynamicColor = findViewById(R.id.item_theme_dynamic_color)
        switchDynamicColor = findViewById(R.id.switch_theme_dynamic_color)

        itemWhitelist = findViewById(R.id.item_whitelist)
        itemBattery = findViewById(R.id.item_battery)
        itemGithub = findViewById(R.id.item_github)
        itemLogs = findViewById(R.id.item_logs)

        val tvFooter = findViewById<TextView>(R.id.tv_footer_version)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            val code = pInfo.versionCode // Deprecated in 28 but necessary/available
            // Modern LongVersionCode handling if minSdk increased but int is fine for display
            tvFooter.text = "v$version ($code) - ${if (BuildConfig.DEBUG) "Alpha Debug" else "Release"}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        // Load Preferences & Init UI
        val isEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("service_enabled", true)
        switchMaster.isChecked = isEnabled

        initAppearanceUI()
        initDeveloperMode()
        setupClickListeners()
    }

    private fun initDeveloperMode() {
        val isDevMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("dev_mode_enabled", false)
        itemLogs.visibility = if (BuildConfig.DEBUG || isDevMode) View.VISIBLE else View.GONE
    }

    private fun initAppearanceUI() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Language
        val lang = prefs.getString("language_code", "")
        when (lang) {
            "en" -> tvCurrentLanguage.text = "English"
            "zh-CN" -> tvCurrentLanguage.text = "简体中文"
            else -> tvCurrentLanguage.text = getString(R.string.settings_theme_follow_system) // Reusing "Follow System" string or "Auto"
        }

        // Theme
        val followSystem = prefs.getBoolean("theme_follow_system", true)
        switchFollowSystem.isChecked = followSystem

        val darkMode = prefs.getBoolean("theme_dark_mode", false)
        switchDarkMode.isChecked = darkMode
        switchDarkMode.isEnabled = !followSystem
        itemDarkMode.alpha = if (followSystem) 0.5f else 1.0f

        val pureBlack = prefs.getBoolean("theme_pure_black", false)
        switchPureBlack.isChecked = pureBlack

        val dynamicColor = prefs.getBoolean("theme_dynamic_color", true)
        switchDynamicColor.isChecked = dynamicColor
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndSyncUI()
    }

    @SuppressLint("InlinedApi")
    private fun setupClickListeners() {
        // --- Service Switch ---
        itemServiceSwitch.setOnClickListener {
            switchMaster.toggle()
            val newState = switchMaster.isChecked
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean("service_enabled", newState).apply()
        }
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean("service_enabled", isChecked).apply()
        }

        // --- Notification Permission ---
        itemNotificationPerm.setOnClickListener {
            val hasPermission = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
            if (hasPermission) {
                // If already granted, open settings to allow user to revoke
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else {
                showPrivacyDisclaimer()
            }
        }

        // --- Post Notification Permission (Android 13+) ---
        itemPostNotificationPerm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    // Open App Settings to revoke if they want
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", packageName, null)
                    startActivity(intent)
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                }
            } else {
                Toast.makeText(this, "Not required for this Android version", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Guide ---
        itemGuide0Hook.setOnClickListener { show0HookGuide() }

        // --- Appearance Logic ---

        // Language
        itemLanguage.setOnClickListener { showLanguageDialog() }

        // Follow System
        itemFollowSystem.setOnClickListener { switchFollowSystem.toggle() }
        switchFollowSystem.setOnCheckedChangeListener { _, isChecked ->
            setFollowSystem(this, isChecked)
            initAppearanceUI() // Refresh UI state (enable/disable dark mode switch)
        }

        // Dark Mode
        itemDarkMode.setOnClickListener {
            if (switchDarkMode.isEnabled) switchDarkMode.toggle()
        }
        switchDarkMode.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed || itemDarkMode.isPressed) { // Avoid recursion if set via code
                setDarkMode(this, isChecked)
            }
        }

        // Pure Black
        itemPureBlack.setOnClickListener { switchPureBlack.toggle() }
        switchPureBlack.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed || itemPureBlack.isPressed) {
                setPureBlack(this, isChecked)
                recreate()
            }
        }

        // Dynamic Colors
        itemDynamicColor.setOnClickListener { switchDynamicColor.toggle() }
        switchDynamicColor.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed || itemDynamicColor.isPressed) {
                setDynamicColor(this, isChecked)
                recreate() // Dynamic Colors need Activity recreation
            }
        }

        // --- Other Items ---
        itemWhitelist.setOnClickListener { startActivity(Intent(this, WhitelistActivity::class.java)) }

        itemBattery.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        itemGithub.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FrancoGiudans/Capsulyric"))
            startActivity(browserIntent)
        }

        itemLogs.setOnClickListener { showLogConsole() }

        // Developer Mode Trigger (Secret)
        val tvFooter = findViewById<TextView>(R.id.tv_footer_version)
        tvFooter.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDevClickTime > 1000) {
                devClickCount = 0
            }
            devClickCount++
            lastDevClickTime = currentTime

            if (devClickCount in 3..6) {
                Toast.makeText(this, "${7 - devClickCount} steps away from developer mode...", Toast.LENGTH_SHORT).show()
            } else if (devClickCount == 7) {
                // Enable Dev Mode
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean("dev_mode_enabled", true).apply()
                itemLogs.visibility = View.VISIBLE
                Toast.makeText(this, "Developer Mode Enabled! 👩‍💻", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("System Default", "English", "简体中文")
        val codes = arrayOf("", "en", "zh-CN")

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_language)
            .setItems(languages) { _, which ->
                setLanguage(this, codes[which])
                recreate()
            }
            .show()
    }

    private fun checkPermissionsAndSyncUI() {
        // Sync Notification Switch
        val listenerGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        switchNotification.isChecked = listenerGranted

        // Check POST_NOTIFICATIONS (Android 13+) - Independent check
        if (Build.VERSION.SDK_INT >= 33) {
            val postGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            switchPostNotification.isChecked = postGranted
        } else {
            switchPostNotification.isChecked = true // Always true on older Androids
            switchPostNotification.isEnabled = false
            itemPostNotificationPerm.alpha = 0.5f
        }
    }

    private fun show0HookGuide() {
        AlertDialog.Builder(this)
            .setTitle(R.string.guide_title)
            .setMessage(android.text.Html.fromHtml(getString(R.string.guide_message), android.text.Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showPrivacyDisclaimer() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_privacy_title)
            .setMessage(getString(R.string.dialog_privacy_message))
            .setPositiveButton(R.string.dialog_btn_understand) { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(R.string.dialog_btn_cancel, null)
            .show()
    }

    private fun showLogConsole() {
        val bottomSheet = BottomSheetDialog(this)

        val logView = TextView(this)
        logView.setPadding(32, 32, 32, 32)
        logView.setTextIsSelectable(true)
        logView.typeface = android.graphics.Typeface.MONOSPACE

        val scroll = androidx.core.widget.NestedScrollView(this)
        scroll.addView(logView)

        bottomSheet.setContentView(scroll)
        bottomSheet.show()

        AppLogger.getInstance().logs.observe(this) { logs ->
            logView.text = logs
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    companion object {
        private const val PREFS_NAME = "IslandLyricsPrefs"
    }
}
