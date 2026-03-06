package com.example.islandlyrics.shizuku

import android.content.Context
import android.net.IConnectivityManager
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuHook {

    val hookedConnectivityManager: IConnectivityManager by lazy {
        try {
            // Get the original remote IBinder from the system server
            val originalBinder = SystemServiceHelper.getSystemService(Context.CONNECTIVITY_SERVICE)
                ?: throw IllegalStateException("Could not get Connectivity Service binder")
            
            val originalCM = IConnectivityManager.Stub.asInterface(originalBinder)
            
            // Wrap the binder with ShizukuBinderWrapper to route calls through Shizuku's privileged UID
            val wrapper = ShizukuBinderWrapper(originalCM.asBinder())
            
            // Re-cast the wrapped binder back to IConnectivityManager
            IConnectivityManager.Stub.asInterface(wrapper)
        } catch (e: Exception) {
            throw RuntimeException("Failed to wrap IConnectivityManager through Shizuku", e)
        }
    }
}
