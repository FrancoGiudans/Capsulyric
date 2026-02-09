package com.example.islandlyrics

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * About page showing app version, build number, commit hash.
 * Provides manual update check button.
 */
class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Display version information
        val tvVersion = findViewById<TextView>(R.id.tv_version)
        val tvBuild = findViewById<TextView>(R.id.tv_build)
        val tvCommit = findViewById<TextView>(R.id.tv_commit)

        tvVersion.text = BuildConfig.VERSION_NAME
        tvBuild.text = BuildConfig.VERSION_CODE.toString()
        tvCommit.text = BuildConfig.GIT_COMMIT_HASH
        
        // Setup hidden log trigger (7 clicks)
        var commitClickCount = 0
        var lastCommitClickTime = 0L
        
        tvCommit.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastCommitClickTime > 2000) {
                commitClickCount = 0
            }
            lastCommitClickTime = now
            commitClickCount++
            
            if (commitClickCount == 7) {
                commitClickCount = 0
                val logger = AppLogger.getInstance()
                val newState = !logger.isLogEnabled
                logger.enableLogging(newState)
                
                val status = if (newState) "Enabled" else "Disabled"
                android.widget.Toast.makeText(this, "Debug Logging $status", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Check for updates button
        val btnCheckUpdate = findViewById<MaterialButton>(R.id.btn_check_update)
        btnCheckUpdate.setOnClickListener {
            checkForUpdates(it)
        }
    }

    /**
     * Manual update check triggered by user.
     */
    private fun checkForUpdates(view: View) {
        view.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val release = UpdateChecker. checkForUpdate(this@AboutActivity)
                
                view.isEnabled = true
                
                if (release != null) {
                    // Show update dialog
                    val dialog = UpdateDialogFragment.newInstance(release)
                    dialog.show(supportFragmentManager, "update_dialog")
                    
                    AppLogger.getInstance().log("About", "Update found: ${release.tagName}")
                } else {
                    // No update available
                    Toast.makeText(
                        this@AboutActivity,
                        R.string.update_no_update,
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    AppLogger.getInstance().log("About", "No update available")
                }
            } catch (e: Exception) {
                view.isEnabled = true
                Toast.makeText(
                    this@AboutActivity,
                    R.string.update_check_failed,
                    Toast.LENGTH_SHORT
                ).show()
                
                AppLogger.getInstance().log("About", "Update check failed: ${e.message}")
            }
        }
    }
}
