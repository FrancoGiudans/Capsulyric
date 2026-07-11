/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

// Portions of this file are adapted from InstallerX Revived
// (https://github.com/wxxsfxyzm/InstallerX-Revived)
// Copyright (C) 2023–2026 iamr0s, InstallerX Revived contributors
// Licensed under GPL-3.0-only

package com.example.islandlyrics.integration.shizuku

import android.content.Context
import android.content.pm.PackageManager
import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object XmsfNetworkHelper {
    
    private const val TAG = "XmsfNetworkHelper"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 500L

    /** Set to true when all backends have failed on this device, to avoid repeated timeouts. */
    @Volatile
    private var deviceUnsupported = false

    suspend fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        if (deviceUnsupported) return false

        val logger = AppLogger.getInstance()
        try {
            val pm = context.packageManager
            val uid = try {
                pm.getPackageUid(XMSF_PACKAGE, 0)
            } catch (e: Exception) {
                logger.w(TAG, "XMSF package not found (UID lookup failed)")
                return false
            }
            
            logger.d(TAG, "🚀 setXmsfNetworkingEnabled called: enabled=$enabled, uid=$uid")

            // Use the utility to ensure permission and then perform the action
            return try {
                requireShizukuPermissionGranted {
                    logger.d(TAG, "✓ Shizuku permission granted, entering retry loop")
                    var lastError: Exception? = null
                    
                    for (attempt in 0 until MAX_RETRIES) {
                        try {
                            logger.d(TAG, "🪝 Attempt ${attempt + 1}/$MAX_RETRIES: Using InstallerX-style hooked binder...")
                            ShizukuHook.setPackageNetworkingEnabled(uid, enabled)
                            logger.d(TAG, "✓ Successfully set XMSF networking to $enabled via hooked binder")
                            return@requireShizukuPermissionGranted true
                        } catch (e: CancellationException) {
                            logger.w(TAG, "⚠️ Operation cancelled")
                            return@requireShizukuPermissionGranted false
                        } catch (e: Exception) {
                            lastError = e
                            logger.w(TAG, "⚠️ HOOK path failed on attempt ${attempt + 1}: ${e.message}")
                        }

                        if (attempt + 1 < MAX_RETRIES) {
                            delay(RETRY_DELAY_MS)
                        }
                    }
                    lastError?.let {
                        logger.e(TAG, "❌ All XMSF networking paths failed after retries: ${it.message}")
                        deviceUnsupported = true
                    }
                    false
                }
            } catch (e: Exception) {
                logger.e(TAG, "❌ Shizuku permission or logic failed: ${e.message}")
                false
            }
        } catch (e: Exception) {
            logger.e(TAG, "❌ Critical error in setXmsfNetworkingEnabled: ${e.message}")
            return false
        }
    }
}
