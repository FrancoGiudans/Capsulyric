package com.example.islandlyrics.integration.shizuku

import android.os.IBinder
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Minimal "hook mode" helper adapted from InstallerX Revived.
 *
 * Instead of hopping through our own Shizuku user service, we wrap the original
 * system Connectivity binder with [ShizukuBinderWrapper] and invoke the hidden
 * firewall APIs directly through that hooked binder.
 */
object ShizukuHook {
    private const val TAG = "ShizukuHook"
    private val hookedServiceCache = ConcurrentHashMap<String, Any>()

    fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean) {
        FirewallCompat.setPackageNetworkingEnabled(uid, enabled) { serviceName, stubClassName, label ->
            getWrappedService(serviceName, stubClassName, label)
        }
        Log.d(TAG, "Network ${if (enabled) "restored" else "blocked"} for uid=$uid via hooked firewall backend")
    }

    private fun getWrappedService(serviceName: String, stubClassName: String, label: String): Any {
        hookedServiceCache[serviceName]?.let { return it }

        return synchronized(this) {
            hookedServiceCache[serviceName]?.let { return@synchronized it }

            val originalBinder = SystemServiceHelper.getSystemService(serviceName)
                ?: throw IllegalStateException("$serviceName binder is null")
            val stubClass = Class.forName(stubClassName)
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val originalService = asInterface.invoke(null, originalBinder)
                ?: throw IllegalStateException("$label original asInterface returned null")
            val serviceBinderMethod = originalService.javaClass.getMethod("asBinder")
            val serviceBinder = serviceBinderMethod.invoke(originalService) as? IBinder
                ?: throw IllegalStateException("$label asBinder returned null")

            val wrappedBinder: IBinder = ShizukuBinderWrapper(serviceBinder)
            val wrappedService = asInterface.invoke(null, wrappedBinder)
                ?: throw IllegalStateException("$label wrapped asInterface returned null")

            hookedServiceCache[serviceName] = wrappedService
            Log.i(TAG, "Created hooked service wrapper for $serviceName")
            wrappedService
        }
    }
}
