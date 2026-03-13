package com.example.islandlyrics.integration.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.util.Log
import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object XmsfNetworkHelper {
    
    private const val TAG = "XmsfNetworkHelper"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 500L

    suspend fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        val logger = AppLogger.getInstance()
        try {
            val pm = context.packageManager
            val uid = try {
                pm.getPackageUid(XMSF_PACKAGE, 0)
            } catch (e: Exception) {
                Log.w(TAG, "XMSF package not found (UID lookup failed)")
                return false
            }
            
            Log.d(TAG, "🚀 setXmsfNetworkingEnabled called: enabled=$enabled, uid=$uid")

            // Use the utility to ensure permission and then perform the action
            return try {
                requireShizukuPermissionGranted {
                    Log.d(TAG, "✓ Shizuku permission granted, entering retry loop")
                    var lastError: Exception? = null
                    
                    for (attempt in 0 until MAX_RETRIES) {
                        try {
                            Log.d(TAG, "📡 Attempt ${attempt + 1}/$MAX_RETRIES: Getting privileged service...")
                            val service = ShizukuUserServiceRecycler.getPrivilegedService()
                            Log.d(TAG, "✓ Got privileged service, calling setPackageNetworkingEnabled...")
                            
                            val success = service.setPackageNetworkingEnabled(uid, enabled)
                            if (success) {
                                Log.d(TAG, "✓ Successfully set XMSF networking to $enabled")
                                return@requireShizukuPermissionGranted true
                            } else {
                                Log.e(TAG, "❌ Privileged service returned failure for $uid")
                                lastError = Exception("Privileged service internal failure")
                            }
                        } catch (e: CancellationException) {
                            Log.w(TAG, "⚠️ Operation cancelled")
                            return@requireShizukuPermissionGranted false
                        } catch (e: DeadObjectException) {
                            lastError = e
                            Log.w(TAG, "⚠️ DeadObjectException on attempt ${attempt + 1}")
                            if (attempt + 1 < MAX_RETRIES) {
                                delay(RETRY_DELAY_MS)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error on attempt ${attempt + 1}: ${e.message}")
                            return@requireShizukuPermissionGranted false
                        }
                    }
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Shizuku permission or logic failed: ${e.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Critical error in setXmsfNetworkingEnabled: ${e.message}")
            return false
        }
    }
}

