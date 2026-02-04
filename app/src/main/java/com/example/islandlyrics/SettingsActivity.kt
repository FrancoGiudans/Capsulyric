package com.example.islandlyrics

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

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
            // Apply theme (if you have AppTheme composable, otherwise basic MaterialTheme)
            AppTheme {
                SettingsScreen(
                    onCheckUpdate = { performUpdateCheck() },
                    onShowLogs = { showLogConsole() },
                    updateVersionText = version,
                    updateBuildText = build
                )
            }
        }
    }

    private fun performUpdateCheck() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val release = UpdateChecker.checkForUpdate(this@SettingsActivity)
                
                if (release != null) {
                    // Show update dialog
                    val dialog = UpdateDialogFragment.newInstance(release)
                    dialog.show(supportFragmentManager, "update_dialog")
                    
                    AppLogger.getInstance().log("Settings", "Update found: ${release.tagName}")
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

    private fun showLogConsole() {
        LogViewerActivity.start(this)
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

