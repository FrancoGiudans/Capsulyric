package com.example.islandlyrics.integration.shizuku

import android.util.Log

/**
 * Deprecated: ShizukuHook is no longer used.
 * PrivilegedServiceImpl now directly uses IConnectivityManager.Stub.asInterface()
 * to get the ConnectivityManager AIDL interface from the system Binder.
 */
@Deprecated("No longer used - see PrivilegedServiceImpl")
object ShizukuHook {
    private val TAG = "ShizukuHook"
    
    init {
        Log.d(TAG, "ShizukuHook is deprecated and should not be used")
    }
}
