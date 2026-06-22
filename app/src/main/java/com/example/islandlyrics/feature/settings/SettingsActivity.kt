package com.example.islandlyrics.feature.settings

import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PageStackHost
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import android.os.Bundle
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.feature.cache.material.CacheManagementScreen
import com.example.islandlyrics.feature.cache.miuix.MiuixCacheManagementScreen
import com.example.islandlyrics.feature.customsettings.material.CustomSettingsScreen
import com.example.islandlyrics.feature.customsettings.miuix.MiuixCustomSettingsScreen
import com.example.islandlyrics.feature.diagnostics.material.DiagnosticsScreen
import com.example.islandlyrics.feature.diagnostics.miuix.MiuixDiagnosticsScreen
import com.example.islandlyrics.feature.faq.material.FAQScreen
import com.example.islandlyrics.feature.faq.miuix.MiuixFAQScreen
import com.example.islandlyrics.feature.lab.material.LabScreen
import com.example.islandlyrics.feature.lab.miuix.MiuixLabScreen
import com.example.islandlyrics.feature.locallyrics.material.LocalLyricDirectoryScreen
import com.example.islandlyrics.feature.locallyrics.miuix.MiuixLocalLyricDirectoryScreen
import com.example.islandlyrics.feature.logviewer.material.LogViewerScreen
import com.example.islandlyrics.feature.logviewer.miuix.MiuixLogViewerScreen
import com.example.islandlyrics.feature.onlinelyricdebug.material.OnlineLyricDebugScreen
import com.example.islandlyrics.feature.onlinelyricdebug.miuix.MiuixOnlineLyricDebugScreen
import com.example.islandlyrics.feature.settings.miuix.MiuixSettingsScreen
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.feature.update.material.UpdateDialog
import com.example.islandlyrics.feature.settings.material.SettingsScreen
import com.example.islandlyrics.feature.settings.material.AboutScreen
import com.example.islandlyrics.feature.settings.miuix.MiuixAboutScreen
import com.example.islandlyrics.ui.theme.material.IslandLyricsMaterialTheme
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    private var updateReleaseInfo by mutableStateOf<UpdateChecker.ReleaseInfo?>(null)
    private var aboutReleaseDialogState by mutableStateOf<ReleaseDialogState?>(null)
    private var aboutReleaseLookupMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Retrieve version info
        var version = "Unknown"
        var build = "Unknown"
        val codename = BuildConfig.VERSION_CODENAME
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName ?: "Unknown"
            build = BuildConfig.GIT_COMMIT_HASH
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            if (isMiuixEnabled(this@SettingsActivity)) {
                MiuixAppTheme {
                    val pageStack = remember { mutableStateListOf<SettingsPage>() }
                    fun pushPage(page: SettingsPage) {
                        pageStack.add(page)
                    }
                    fun popPage() {
                        if (pageStack.isNotEmpty()) pageStack.removeAt(pageStack.lastIndex)
                    }
                    PredictiveBackActivity(enabled = pageStack.isEmpty()) {
                        PageStackHost(
                            stack = pageStack,
                            onPop = ::popPage,
                            backdropColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface),
                            backgroundContent = {
                                MiuixSettingsScreen(
                                    onCheckUpdate = { performUpdateCheck() },
                                    onShowDiagnostics = { pushPage(SettingsPage.Diagnostics) },
                                    updateVersionText = version,
                                    updateCodenameText = codename,
                                    updateBuildText = build,
                                    onOpenCustomSettings = { pushPage(SettingsPage.CustomSettings) },
                                    onOpenFaq = { pushPage(SettingsPage.Faq) },
                                    onOpenAbout = { pushPage(SettingsPage.About) },
                                    onOpenLocalLyricDirectory = { uri, name ->
                                        pushPage(SettingsPage.LocalLyricDirectory(uri.toString(), name))
                                    },
                                    onOpenOnlineLyricRematch = { pushPage(SettingsPage.OnlineLyricDebug) },
                                    onOpenCacheManagement = { pushPage(SettingsPage.CacheManagement) },
                                    onOpenLab = { pushPage(SettingsPage.Lab) },
                                    updateReleaseInfo = updateReleaseInfo,
                                    onUpdateDismiss = { updateReleaseInfo = null },
                                    onUpdateIgnore = { version ->
                                        UpdateChecker.setIgnoredVersion(this@SettingsActivity, version)
                                        AppLogger.getInstance().log("Update", "Ignored version: $version")
                                    }
                                )
                            },
                            pageContent = { page ->
                                MiuixSettingsPage(
                                    page = page,
                                    onBack = ::popPage,
                                    onPushPage = ::pushPage,
                                    updateVersionText = version,
                                    updateBuildText = build
                                )
                            }
                        )
                    }
                }
            } else {
                IslandLyricsMaterialTheme {
                    val pageStack = remember { mutableStateListOf<SettingsPage>() }
                    fun pushPage(page: SettingsPage) {
                        pageStack.add(page)
                    }
                    fun popPage() {
                        if (pageStack.isNotEmpty()) pageStack.removeAt(pageStack.lastIndex)
                    }
                    PredictiveBackActivity(enabled = pageStack.isEmpty()) {
                        PageStackHost(
                            stack = pageStack,
                            onPop = ::popPage,
                            backdropColor = MaterialTheme.colorScheme.background,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            backgroundContent = {
                                SettingsScreen(
                                    onCheckUpdate = { performUpdateCheck() },
                                    onShowDiagnostics = { pushPage(SettingsPage.Diagnostics) },
                                    updateVersionText = version,
                                    updateCodenameText = codename,
                                    updateBuildText = build,
                                    onOpenCustomSettings = { pushPage(SettingsPage.CustomSettings) },
                                    onOpenFaq = { pushPage(SettingsPage.Faq) },
                                    onOpenAbout = { pushPage(SettingsPage.About) },
                                    onOpenLocalLyricDirectory = { uri, name ->
                                        pushPage(SettingsPage.LocalLyricDirectory(uri.toString(), name))
                                    },
                                    onOpenOnlineLyricRematch = { pushPage(SettingsPage.OnlineLyricDebug) },
                                    onOpenCacheManagement = { pushPage(SettingsPage.CacheManagement) },
                                    onOpenLab = { pushPage(SettingsPage.Lab) }
                                )

                                if (updateReleaseInfo != null) {
                                    UpdateDialog(
                                        releaseInfo = updateReleaseInfo!!,
                                        onDismiss = { updateReleaseInfo = null },
                                        onIgnore = { version ->
                                            UpdateChecker.setIgnoredVersion(this@SettingsActivity, version)
                                            AppLogger.getInstance().log("Update", "Ignored version: $version")
                                        }
                                    )
                                }
                            },
                            pageContent = { page ->
                                MaterialSettingsPage(
                                    page = page,
                                    onBack = ::popPage,
                                    onPushPage = ::pushPage,
                                    updateVersionText = version,
                                    updateBuildText = build
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MaterialSettingsPage(
        page: SettingsPage,
        onBack: () -> Unit,
        onPushPage: (SettingsPage) -> Unit,
        updateVersionText: String,
        updateBuildText: String
    ) {
        when (page) {
            SettingsPage.CustomSettings -> CustomSettingsScreen(
                onBack = onBack,
                onCheckUpdate = {},
                onShowLogs = { onPushPage(SettingsPage.LogViewer) },
                updateVersionText = updateVersionText,
                updateBuildText = updateBuildText
            )
            SettingsPage.Faq -> FAQScreen(onBack = onBack)
            SettingsPage.About -> AboutScreen(
                updateVersionText = updateVersionText,
                updateCodenameText = BuildConfig.VERSION_CODENAME,
                updateBuildText = updateBuildText,
                onCheckUpdate = { performAboutUpdateCheck() },
                onViewCurrentVersionChangelog = { showCurrentVersionChangelog() },
                onBack = onBack,
                releaseDialogState = aboutReleaseDialogState,
                onReleaseDialogDismiss = { aboutReleaseDialogState = null },
                onUpdateIgnore = { version ->
                    UpdateChecker.setIgnoredVersion(this@SettingsActivity, version)
                    AppLogger.getInstance().log("Update", "Ignored version: $version")
                },
                releaseLookupMessage = aboutReleaseLookupMessage,
                onReleaseLookupMessageDismiss = { aboutReleaseLookupMessage = null }
            )
            SettingsPage.Diagnostics -> DiagnosticsScreen(
                onBack = onBack,
                onOpenLogViewer = { onPushPage(SettingsPage.LogViewer) }
            )
            SettingsPage.OnlineLyricDebug -> OnlineLyricDebugScreen(onBack = onBack)
            SettingsPage.CacheManagement -> CacheManagementScreen(onBack = onBack)
            SettingsPage.Lab -> LabScreen(onBack = onBack)
            SettingsPage.LogViewer -> LogViewerScreen(onBack = onBack)
            is SettingsPage.LocalLyricDirectory -> LocalLyricDirectoryScreen(
                directoryUri = page.directoryUri.toUri(),
                directoryName = page.directoryName,
                onBack = onBack
            )
        }
    }

    @Composable
    private fun MiuixSettingsPage(
        page: SettingsPage,
        onBack: () -> Unit,
        onPushPage: (SettingsPage) -> Unit,
        updateVersionText: String,
        updateBuildText: String
    ) {
        when (page) {
            SettingsPage.CustomSettings -> MiuixCustomSettingsScreen(
                onBack = onBack,
                onCheckUpdate = {},
                onShowLogs = { onPushPage(SettingsPage.LogViewer) },
                updateVersionText = updateVersionText,
                updateBuildText = updateBuildText
            )
            SettingsPage.Faq -> MiuixFAQScreen(onBack = onBack)
            SettingsPage.About -> MiuixAboutScreen(
                updateVersionText = updateVersionText,
                updateCodenameText = BuildConfig.VERSION_CODENAME,
                updateBuildText = updateBuildText,
                onCheckUpdate = { performAboutUpdateCheck() },
                onViewCurrentVersionChangelog = { showCurrentVersionChangelog() },
                onBack = onBack,
                releaseDialogState = aboutReleaseDialogState,
                onReleaseDialogDismiss = { aboutReleaseDialogState = null },
                onUpdateIgnore = { version ->
                    UpdateChecker.setIgnoredVersion(this@SettingsActivity, version)
                    AppLogger.getInstance().log("Update", "Ignored version: $version")
                },
                releaseLookupMessage = aboutReleaseLookupMessage,
                onReleaseLookupMessageDismiss = { aboutReleaseLookupMessage = null }
            )
            SettingsPage.Diagnostics -> MiuixDiagnosticsScreen(
                onBack = onBack,
                onOpenLogViewer = { onPushPage(SettingsPage.LogViewer) }
            )
            SettingsPage.OnlineLyricDebug -> MiuixOnlineLyricDebugScreen(onBack = onBack)
            SettingsPage.CacheManagement -> MiuixCacheManagementScreen(onBack = onBack)
            SettingsPage.Lab -> MiuixLabScreen(onBack = onBack)
            SettingsPage.LogViewer -> MiuixLogViewerScreen(onBack = onBack)
            is SettingsPage.LocalLyricDirectory -> MiuixLocalLyricDirectoryScreen(
                directoryUri = page.directoryUri.toUri(),
                directoryName = page.directoryName,
                onBack = onBack
            )
        }
    }

    private fun performAboutUpdateCheck() {
        if (OfflineModeManager.isEnabled(this)) {
            Toast.makeText(this, R.string.offline_mode_network_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val release = UpdateChecker.checkForUpdate(this@SettingsActivity)

                if (release != null) {
                    aboutReleaseDialogState = ReleaseDialogState(
                        releaseInfo = release,
                        mode = ReleaseDialogMode.UPDATE_AVAILABLE
                    )
                    AppLogger.getInstance().log("About", "Update found: ${UpdateChecker.getComparableVersion(release)}")
                } else {
                    Toast.makeText(
                        this@SettingsActivity,
                        R.string.update_no_update,
                        Toast.LENGTH_SHORT
                    ).show()
                    AppLogger.getInstance().log("About", "No update available")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.update_check_failed,
                    Toast.LENGTH_SHORT
                ).show()
                AppLogger.getInstance().log("About", "Update check failed: ${e.message}")
            }
        }
    }

    private fun showCurrentVersionChangelog() {
        if (OfflineModeManager.isEnabled(this)) {
            Toast.makeText(this, R.string.offline_mode_network_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.update_current_version_changelog_loading, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val release = UpdateChecker.fetchReleaseForVersion(this@SettingsActivity)
                if (release != null) {
                    aboutReleaseDialogState = ReleaseDialogState(
                        releaseInfo = release,
                        mode = ReleaseDialogMode.CURRENT_VERSION_CHANGELOG
                    )
                    AppLogger.getInstance().log(
                        "About",
                        "Current version changelog found: ${UpdateChecker.getComparableVersion(release)}"
                    )
                } else {
                    aboutReleaseLookupMessage = getString(R.string.update_current_version_changelog_unavailable_message)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.update_check_failed,
                    Toast.LENGTH_SHORT
                ).show()
                AppLogger.getInstance().log("About", "Current version changelog lookup failed: ${e.message}")
            }
        }
    }

    private fun performUpdateCheck() {
        if (OfflineModeManager.isEnabled(this)) {
            Toast.makeText(this, R.string.offline_mode_network_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val release = UpdateChecker.checkForUpdate(this@SettingsActivity)
                
                if (release != null) {
                    // Show update dialog
                    updateReleaseInfo = release
                    
                    AppLogger.getInstance().log("Settings", "Update found: ${UpdateChecker.getComparableVersion(release)}")
                } else {
                    // No update available
                    Toast.makeText(
                        this@SettingsActivity,
                        R.string.update_no_update,
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    AppLogger.getInstance().log("Settings", "No update available")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.update_check_failed,
                    Toast.LENGTH_SHORT
                ).show()
                
                AppLogger.getInstance().log("Settings", "Update check failed: ${e.message}")
            }
        }
    }

    // onResume for auto-update check is preserved from base logic or can be re-added if needed.
    // Since UI is now declarative, checkPermissionsAndSyncUI is no longer needed as Compose reads state directly.
    // However, if we want to auto-refresh state on resume, the Composable will recompose if the state changes.
    // The state in SettingsScreen.kt initializes from SharedPreferences/SystemService. 
    // If user returns from Settings, Composable might not recompose unless we force it or use Lifecycle effects.
    // For simplicity, we rely on the fact that recreating the activity (common on theme change) or normal composition works.
    // To be safe for permission returns, we could trigger a recomposition or use `rememberUpdatedState`.
    // But `SettingsScreen` uses `remember { notificationGranted }` which won't update on resume automatically without extra logic.
    // For now, let's keep it simple. If "Sync UI" is critical, we might need a simpler mechanism or ViewModel.
}

private sealed class SettingsPage {
    data object CustomSettings : SettingsPage()
    data object Faq : SettingsPage()
    data object About : SettingsPage()
    data object Diagnostics : SettingsPage()
    data object OnlineLyricDebug : SettingsPage()
    data object CacheManagement : SettingsPage()
    data object Lab : SettingsPage()
    data object LogViewer : SettingsPage()
    data class LocalLyricDirectory(
        val directoryUri: String,
        val directoryName: String
    ) : SettingsPage()
}



