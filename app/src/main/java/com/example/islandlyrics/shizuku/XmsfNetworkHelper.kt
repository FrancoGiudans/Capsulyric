package com.example.islandlyrics.shizuku

import android.content.Context
import android.content.pm.PackageManager
import com.example.islandlyrics.AppLogger

object XmsfNetworkHelper {
    
    private const val TAG = "XmsfNetworkHelper"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"

    suspend fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        val logger = AppLogger.getInstance()
        try {
            val pm = context.packageManager
            val uid = pm.getPackageUid(XMSF_PACKAGE, 0)

            return requireShizukuPermissionGranted {
                val service = ShizukuUserServiceRecycler.getPrivilegedService()
                service.setPackageNetworkingEnabled(uid, enabled)
                true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            logger.e(TAG, "XMSF package not found.", e)
            return false
        } catch (e: Exception) {
            logger.e(TAG, "Failed to set XMSF networking to $enabled", e)
            return false
        }
    }
}
