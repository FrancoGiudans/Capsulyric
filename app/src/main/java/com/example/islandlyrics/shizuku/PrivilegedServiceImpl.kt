package com.example.islandlyrics.shizuku

import androidx.annotation.Keep
import com.example.islandlyrics.IPrivilegedService
import android.util.Log
import android.os.IBinder
import android.os.IInterface

@Keep
class PrivilegedServiceImpl : IPrivilegedService.Stub() {

    companion object {
        private const val TAG = "PrivilegedServiceImpl"
        
        init {
            try {
                Log.d(TAG, "⚡ PrivilegedServiceImpl class loaded")
            } catch (ignored: Exception) {}
        }
    }
    
    init {
        try {
            Log.d(TAG, "⚡ PrivilegedServiceImpl instance created")
        } catch (ignored: Exception) {}
    }

    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean {
        Log.d(TAG, "🚀 ENTRY: setPackageNetworkingEnabled(uid=$uid, enabled=$enabled)")
        
        try {
            // Use reflection safely to avoid crashing the entire binder flow
            val result = runCatching {
                Log.d(TAG, "Step 1: Getting ConnectivityManager...")
                val realCm = getConnectivityManagerInstance()
                Log.d(TAG, "Step 2: Got ConnectivityManager: ${realCm.javaClass.name}")
                
                // Chain IDs: 9 = FILTER_CHAIN_NAME_STANDBY_ALLOWLIST or similar on some ROMs
                // On some vendors, 2 or 1 might be used. 9 is most common for firewall.
                val chain = 9
                
                Log.d(TAG, "Step 3: Calling setFirewallChainEnabled($chain, true)...")
                // Pass Boolean instead of Integer to match expected 'boolean' type
                callMethodResilient(realCm, "setFirewallChainEnabled", chain, true)
                Log.d(TAG, "Step 4: setFirewallChainEnabled succeeded")
                
                val rule = if (enabled) 0 else 2 // 0 = ALLOW, 2 = DENY
                Log.d(TAG, "Step 5: Calling setUidFirewallRule($chain, $uid, $rule)...")
                callMethodResilient(realCm, "setUidFirewallRule", chain, uid, rule)
                
                Log.d(TAG, "✅ SUCCESS: Firewall rules updated for $uid")
                true
            }
            
            if (result.isFailure) {
                val e = result.exceptionOrNull()
                Log.e(TAG, "❌ FAILURE in setPackageNetworkingEnabled: ${e?.message}")
                e?.printStackTrace()
                return false
            }
            
            return result.getOrDefault(false)
            
        } catch (e: Throwable) {
            // ABSOLUTE guard against any crash in the privileged process
            Log.e(TAG, "🔥 CRITICAL ERROR in PrivilegedServiceImpl: ${e.message}")
            return false
        } finally {
            Log.d(TAG, "🏁 EXIT: setPackageNetworkingEnabled")
        }
    }
    
    private fun getConnectivityManagerInstance(): Any {
        val smClass = Class.forName("android.os.ServiceManager")
        val getService = smClass.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "connectivity") as? IBinder
            ?: throw RuntimeException("connectivity service not found")
            
        val stubClass = Class.forName("android.net.IConnectivityManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, binder) ?: throw RuntimeException("asInterface returned null")
    }
    
    /**
     * More resilient method call that searches for the best matching method
     */
    private fun callMethodResilient(obj: Any, methodName: String, vararg args: Any) {
        val clazz = obj.javaClass
        val methods = clazz.methods
        
        // Find a method that matches by name and parameter count
        val targetMethod = methods.find { it.name == methodName && it.parameterCount == args.size }
            ?: throw NoSuchMethodException("Could not find method $methodName with ${args.size} params on ${clazz.name}")
            
        targetMethod.isAccessible = true
        
        // Ensure arguments match primitive types if needed
        val finalArgs = Array(args.size) { i ->
            val paramType = targetMethod.parameterTypes[i]
            val arg = args[i]
            
            when {
                paramType == Int::class.javaPrimitiveType && arg is Int -> arg
                paramType == Boolean::class.javaPrimitiveType && arg is Boolean -> arg
                // Force conversion if there's a mismatch (common in reflection)
                paramType == Boolean::class.javaPrimitiveType && arg is Number -> arg.toInt() != 0
                paramType == Int::class.javaPrimitiveType && arg is Boolean -> if (arg) 1 else 0
                else -> arg
            }
        }
        
        targetMethod.invoke(obj, *finalArgs)
    }
}

