package com.example.islandlyrics.feature.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.feature.diagnostics.DiagnosticsActivity
import com.example.islandlyrics.feature.settings.material.AboutScreen
import com.example.islandlyrics.feature.settings.miuix.MiuixAboutScreen
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme

class AboutActivity : BaseActivity() {

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
                        onShowDiagnostics = { showDiagnostics() }
                    )
                }
            } else {
                AppTheme {
                    AboutScreen(
                        updateVersionText = version,
                        updateCodenameText = codename,
                        updateBuildText = build,
                        onShowDiagnostics = { showDiagnostics() }
                    )
                }
            }
        }
    }

    private fun showDiagnostics() {
        startActivity(android.content.Intent(this, DiagnosticsActivity::class.java))
    }
}

