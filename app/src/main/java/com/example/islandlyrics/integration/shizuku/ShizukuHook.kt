package com.example.islandlyrics.integration.shizuku

import android.content.Context
import android.os.IBinder
import android.util.Log
import java.lang.reflect.InvocationTargetException
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
    private const val OEM_DENY_CHAIN = 9

    @Volatile
    private var hookedConnectivityManager: Any? = null

    fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean) {
        val connectivityManager = getHookedConnectivityManager()
        val rule = if (enabled) 0 else 2

        if (!enabled) {
            callMethodResilient(connectivityManager, "setFirewallChainEnabled", OEM_DENY_CHAIN, true)
            Log.d(TAG, "Enabled firewall chain $OEM_DENY_CHAIN before blocking uid=$uid")
        }

        callMethodResilient(connectivityManager, "setUidFirewallRule", OEM_DENY_CHAIN, uid, rule)
        Log.d(TAG, "Network ${if (enabled) "restored" else "blocked"} for uid=$uid via hooked ConnectivityManager")
    }

    private fun getHookedConnectivityManager(): Any {
        hookedConnectivityManager?.let { return it }

        return synchronized(this) {
            hookedConnectivityManager?.let { return@synchronized it }

            val originalBinder = SystemServiceHelper.getSystemService(Context.CONNECTIVITY_SERVICE)
                ?: throw IllegalStateException("Connectivity service binder is null")

            val wrapper: IBinder = ShizukuBinderWrapper(originalBinder)
            val stubClass = Class.forName("android.net.IConnectivityManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val hooked = asInterface.invoke(null, wrapper)
                ?: throw IllegalStateException("IConnectivityManager.Stub.asInterface returned null")

            hookedConnectivityManager = hooked
            Log.i(TAG, "Created hooked ConnectivityManager binder wrapper")
            hooked
        }
    }

    private fun callMethodResilient(target: Any, methodName: String, vararg args: Any) {
        val method = target.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == args.size
        } ?: throw NoSuchMethodException(
            "Could not find method $methodName with ${args.size} args on ${target.javaClass.name}"
        )

        method.isAccessible = true
        val adaptedArgs = Array(args.size) { index ->
            val arg = args[index]
            when (val expected = method.parameterTypes[index]) {
                Int::class.javaPrimitiveType -> when (arg) {
                    is Int -> arg
                    is Boolean -> if (arg) 1 else 0
                    is Number -> arg.toInt()
                    else -> throw IllegalArgumentException("Unsupported arg $arg for int parameter")
                }

                Boolean::class.javaPrimitiveType -> when (arg) {
                    is Boolean -> arg
                    is Number -> arg.toInt() != 0
                    else -> throw IllegalArgumentException("Unsupported arg $arg for boolean parameter")
                }

                else -> if (expected.isInstance(arg)) arg
                else throw IllegalArgumentException("Arg $arg does not match ${expected.name}")
            }
        }

        try {
            method.invoke(target, *adaptedArgs)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }
}
