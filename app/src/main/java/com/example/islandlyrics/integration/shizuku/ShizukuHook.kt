package com.example.islandlyrics.integration.shizuku

import android.content.Context
import android.net.IConnectivityManager
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

    private val hookedConnectivityManager: IConnectivityManager by lazy {
        Log.d(TAG, "Creating on-demand hooked IConnectivityManager...")
        val originalBinder = SystemServiceHelper.getSystemService(Context.CONNECTIVITY_SERVICE)
        val originalCM = IConnectivityManager.Stub.asInterface(originalBinder)
        val wrapper: IBinder = ShizukuBinderWrapper(originalCM.asBinder())
        IConnectivityManager.Stub.asInterface(wrapper).also {
            Log.i(TAG, "On-demand hooked IConnectivityManager created.")
        }
    }

    fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean) {
        var typedFailure: Throwable? = null

        try {
            setPackageNetworkingEnabledViaInstallerXPath(uid, enabled)
            return
        } catch (t: Throwable) {
            typedFailure = t
            Log.w(TAG, "Typed IConnectivityManager path failed, falling back to reflective compat", t)
        }

        try {
            FirewallCompat.setPackageNetworkingEnabled(uid, enabled) { serviceName, stubClassName, label ->
                getWrappedService(serviceName, stubClassName, label)
            }
            Log.d(TAG, "Network ${if (enabled) "restored" else "blocked"} for uid=$uid via reflective firewall backend")
        } catch (fallbackFailure: Throwable) {
            fallbackFailure.addSuppressed(typedFailure)
            throw IllegalStateException(
                buildString {
                    append("Typed hooked IConnectivityManager failed")
                    append(": ")
                    append(typedFailure.javaClass.name)
                    typedFailure.message?.takeIf(String::isNotBlank)?.let { message ->
                        append(": ")
                        append(message)
                    }
                    append("; reflective fallback failed: ")
                    append(fallbackFailure.message ?: fallbackFailure.javaClass.name)
                },
                fallbackFailure
            )
        }
    }

    private fun setPackageNetworkingEnabledViaInstallerXPath(uid: Int, enabled: Boolean) {
        val cm = hookedConnectivityManager

        // The integer 3 actually means FIREWALL_CHAIN_POWERSAVE (Whitelist mode).
        // We must use 9, which represents FIREWALL_CHAIN_OEM_DENY_3 (Blacklist mode).
        val chain = 9

        // FIREWALL_RULE_DEFAULT = 0, FIREWALL_RULE_ALLOW = 1, FIREWALL_RULE_DENY = 2
        // For a DENY chain, use DENY (2) to block, and DEFAULT (0) to remove the block.
        val rule = if (enabled) 0 else 2

        if (!enabled) {
            // Block network: Ensure the chain is enabled, then apply DENY rule to the UID
            cm.setFirewallChainEnabled(chain, true)
            cm.setUidFirewallRule(chain, uid, rule)
            Log.i(TAG, "Network BLOCKED for UID: $uid via OEM_DENY_3")
        } else {
            // Restore network: Reset the UID rule to DEFAULT to remove the restriction
            cm.setUidFirewallRule(chain, uid, rule)
            // WARNING: Do NOT disable the entire chain here, otherwise other apps blocked
            // in this chain will also regain network access unexpectedly.
            Log.i(TAG, "Network RESTORED for UID: $uid via OEM_DENY_3")
        }
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
