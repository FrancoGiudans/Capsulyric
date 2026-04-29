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
import com.example.islandlyrics.feature.update.material.UpdateDialog
import com.example.islandlyrics.feature.update.miuix.MiuixUpdateDialog
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AboutActivity : BaseActivity() {

    private var updateReleaseInfo by mutableStateOf<UpdateChecker.ReleaseInfo?>(null)

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
                    MiuixAboutScreen(
                        updateVersionText = version,
                        updateCodenameText = codename,
                        updateBuildText = build,
                        onShowDiagnostics = { showDiagnostics() },
                        onCheckUpdate = { performUpdateCheck() }
                    )

                    if (updateReleaseInfo != null) {
                        MiuixUpdateDialog(
                            show = true,
                            releaseInfo = updateReleaseInfo!!,
                            onDismiss = { updateReleaseInfo = null },
                            onIgnore = { tag ->
                                UpdateChecker.setIgnoredVersion(this@AboutActivity, tag)
                                AppLogger.getInstance().log("Update", "Ignored version: $tag")
                            }
                        )
                    }
                }
            } else {
                AppTheme {
                    AboutScreen(
                        updateVersionText = version,
                        updateCodenameText = codename,
                        updateBuildText = build,
                        onShowDiagnostics = { showDiagnostics() },
                        onCheckUpdate = { performUpdateCheck() }
                    )

                    if (updateReleaseInfo != null) {
                        UpdateDialog(
                            releaseInfo = updateReleaseInfo!!,
                            onDismiss = { updateReleaseInfo = null },
                            onIgnore = { tag ->
                                UpdateChecker.setIgnoredVersion(this@AboutActivity, tag)
                                AppLogger.getInstance().log("Update", "Ignored version: $tag")
                            }
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
                    updateReleaseInfo = release
                    AppLogger.getInstance().log("About", "Update found: ${release.tagName}")
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

    private fun showDiagnostics() {
        startActivity(android.content.Intent(this, DiagnosticsActivity::class.java))
    }
}
