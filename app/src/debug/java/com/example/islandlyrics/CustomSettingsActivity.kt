package com.example.islandlyrics

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class CustomSettingsActivity : BaseActivity() {

    private var updateReleaseInfo by mutableStateOf<UpdateChecker.ReleaseInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Retrieve version info
        var version = "Unknown"
        var build = "Unknown"
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName ?: "Unknown"
            build = BuildConfig.GIT_COMMIT_HASH
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            AppTheme {
                CustomSettingsScreen(
                    onBack = { finish() },
                    onCheckUpdate = { performUpdateCheck() },
                    onShowLogs = { showLogConsole() },
                    updateVersionText = version,
                    updateBuildText = build
                )

                if (updateReleaseInfo != null) {
                    UpdateDialog(
                        releaseInfo = updateReleaseInfo!!,
                        onDismiss = { updateReleaseInfo = null },
                        onIgnore = { tag ->
                            UpdateChecker.setIgnoredVersion(this, tag)
                            AppLogger.getInstance().log("Update", "Ignored version: $tag")
                        }
                    )
                }
            }
        }
    }

    private fun performUpdateCheck() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val release = UpdateChecker.checkForUpdate(this@CustomSettingsActivity)
                
                if (release != null) {
                    // Show update dialog
                    updateReleaseInfo = release
                    
                    AppLogger.getInstance().log("Settings", "Update found: ${release.tagName}")
                } else {
                    // No update available
                    Toast.makeText(
                        this@CustomSettingsActivity,
                        R.string.update_no_update,
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    AppLogger.getInstance().log("Settings", "No update available")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@CustomSettingsActivity,
                    R.string.update_check_failed,
                    Toast.LENGTH_SHORT
                ).show()
                
                AppLogger.getInstance().log("Settings", "Update check failed: ${e.message}")
            }
        }
    }

    private fun showLogConsole() {
        val intent = Intent(this, LogViewerActivity::class.java)
        startActivity(intent)
    }
}
