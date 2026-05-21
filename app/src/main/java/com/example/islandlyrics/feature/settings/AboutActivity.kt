package com.example.islandlyrics.feature.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.feature.diagnostics.DiagnosticsActivity
import com.example.islandlyrics.feature.settings.material.AboutScreen
import com.example.islandlyrics.feature.settings.miuix.MiuixAboutScreen
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.common.PredictiveBackActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.IslandLyricsMaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AboutActivity : BaseActivity() {

    private var releaseDialogState by mutableStateOf<ReleaseDialogState?>(null)
    private var releaseLookupMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve version info (keep consistent with SettingsActivity)
        var version = "Unknown"
        var build = "Unknown"
        val codename = BuildConfig.VERSION_CODENAME
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName ?: "Unknown"
            build = BuildConfig.GIT_COMMIT_HASH
        } catch (_: Exception) {
            // ignore
        }

        setContent {
            if (isMiuixEnabled(this@AboutActivity)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixAboutScreen(
                        updateVersionText = version,
                        updateCodenameText = codename,
                        updateBuildText = build,
                        onShowDiagnostics = { showDiagnostics() },
                        onCheckUpdate = { performUpdateCheck() },
                        onViewCurrentVersionChangelog = { showCurrentVersionChangelog() },
                        releaseDialogState = releaseDialogState,
                        onReleaseDialogDismiss = { releaseDialogState = null },
                        onUpdateIgnore = { version ->
                            UpdateChecker.setIgnoredVersion(this@AboutActivity, version)
                            AppLogger.getInstance().log("Update", "Ignored version: $version")
                        },
                        releaseLookupMessage = releaseLookupMessage,
                        onReleaseLookupMessageDismiss = { releaseLookupMessage = null }
                    )
                    }
                }
            } else {
                IslandLyricsMaterialTheme {
                    PredictiveBackActivity {
                        AboutScreen(
                            updateVersionText = version,
                            updateCodenameText = codename,
                            updateBuildText = build,
                            onShowDiagnostics = { showDiagnostics() },
                            onCheckUpdate = { performUpdateCheck() },
                            onViewCurrentVersionChangelog = { showCurrentVersionChangelog() },
                            releaseDialogState = releaseDialogState,
                            onReleaseDialogDismiss = { releaseDialogState = null },
                            onUpdateIgnore = { version ->
                                UpdateChecker.setIgnoredVersion(this@AboutActivity, version)
                                AppLogger.getInstance().log("Update", "Ignored version: $version")
                            },
                            releaseLookupMessage = releaseLookupMessage,
                            onReleaseLookupMessageDismiss = { releaseLookupMessage = null }
                        )
                    }
                }
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
                val release = UpdateChecker.checkForUpdate(this@AboutActivity)
                if (release != null) {
                    releaseDialogState = ReleaseDialogState(
                        releaseInfo = release,
                        mode = ReleaseDialogMode.UPDATE_AVAILABLE
                    )
                    AppLogger.getInstance().log("About", "Update found: ${UpdateChecker.getComparableVersion(release)}")
                } else {
                    Toast.makeText(
                        this@AboutActivity,
                        R.string.update_no_update,
                        Toast.LENGTH_SHORT
                    ).show()
                    AppLogger.getInstance().log("About", "No update available")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@AboutActivity,
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
                val release = UpdateChecker.fetchReleaseForVersion(this@AboutActivity)
                if (release != null) {
                    releaseDialogState = ReleaseDialogState(
                        releaseInfo = release,
                        mode = ReleaseDialogMode.CURRENT_VERSION_CHANGELOG
                    )
                    AppLogger.getInstance().log(
                        "About",
                        "Current version changelog found: ${UpdateChecker.getComparableVersion(release)}"
                    )
                } else {
                    releaseLookupMessage = getString(R.string.update_current_version_changelog_unavailable_message)
                    AppLogger.getInstance().log("About", "Current version changelog unavailable")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@AboutActivity,
                    R.string.update_check_failed,
                    Toast.LENGTH_SHORT
                ).show()
                AppLogger.getInstance().log("About", "Current version changelog lookup failed: ${e.message}")
            }
        }
    }

    private fun showDiagnostics() {
        startActivity(android.content.Intent(this, DiagnosticsActivity::class.java))
    }
}

data class ReleaseDialogState(
    val releaseInfo: UpdateChecker.ReleaseInfo,
    val mode: ReleaseDialogMode
)

enum class ReleaseDialogMode {
    UPDATE_AVAILABLE,
    CURRENT_VERSION_CHANGELOG
}
