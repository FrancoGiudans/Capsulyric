package com.example.islandlyrics.integration.shizuku

import androidx.annotation.Keep
import com.example.islandlyrics.IPrivilegedService
import com.example.islandlyrics.IPrivilegedLogCallback
import android.net.IConnectivityManager
import android.util.Log
import android.os.IBinder
import java.lang.reflect.InvocationTargetException
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Keep
class PrivilegedServiceImpl : IPrivilegedService.Stub() {

    companion object {
        private const val TAG = "PrivilegedServiceImpl"
        private const val OP_TIMEOUT_MS = 3000L
        private val workerThread: HandlerThread by lazy {
            HandlerThread("PrivilegedServiceWorker").apply { start() }
        }
        private val workerHandler: Handler by lazy { Handler(workerThread.looper) }
        
        init {
            try {
                Log.d(TAG, "⚡ PrivilegedServiceImpl class loaded")
            } catch (ignored: Exception) {}
        }
    }
    
    init {
        try {
            logD("⚡ PrivilegedServiceImpl instance created")
        } catch (ignored: Exception) {}
    }

    @Volatile private var logCallback: IPrivilegedLogCallback? = null

    override fun setLogCallback(callback: IPrivilegedLogCallback?) {
        logCallback = callback
        logD("Log callback set: ${callback != null}")
    }

    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean {
        logD("🚀 ENTRY: setPackageNetworkingEnabled(uid=$uid, enabled=$enabled)")
        
        try {
            val resultRef = AtomicReference<Result<Boolean>?>(null)
            val latch = CountDownLatch(1)

            workerHandler.post {
                val result = runCatching {
                    logD("Step 1: Getting ConnectivityManager...")
                    val cm = getConnectivityManagerInstance()
                    logD("Step 2: Got ConnectivityManager: ${cm.javaClass.name}")
                    
                    // Chain IDs: 9 = OEM_DENY_3 (Blacklist mode). Matches InstallerX.
                    val chain = 9
                    val rule = if (enabled) 0 else 2 // 0 = ALLOW, 2 = DENY

                    if (!enabled) {
                        // setFirewallChainEnabled may be missing on some devices (e.g. MediaTek).
                        // Try it but fall through to setUidFirewallRule if unavailable —
                        // the OEM deny chain may already be enabled.
                        try {
                            logD("Step 3: Calling setFirewallChainEnabled($chain, true)...")
                            cm.setFirewallChainEnabled(chain, true)
                            logD("Step 4: setFirewallChainEnabled succeeded")
                        } catch (e: Exception) {
                            logW("setFirewallChainEnabled not available (chain may already be enabled): ${e.message}")
                        }
                    }

                    logD("Step 5: Calling setUidFirewallRule($chain, $uid, $rule)...")
                    cm.setUidFirewallRule(chain, uid, rule)
                    
                    logD("✅ SUCCESS: Firewall rules updated for $uid")
                    true
                }

                resultRef.set(result)
                latch.countDown()
            }

            val completed = latch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                logE("❌ FAILURE in setPackageNetworkingEnabled: timeout after ${OP_TIMEOUT_MS}ms")
                return false
            }

            val result = resultRef.get() ?: return false
            
            if (result.isFailure) {
                val e = result.exceptionOrNull()
                logE("❌ FAILURE in setPackageNetworkingEnabled: ${e?.javaClass?.name}: ${e?.message}")
                e?.cause?.let { cause ->
                    logE("❌ Root cause: ${cause.javaClass.name}: ${cause.message}")
                }
                e?.printStackTrace()
                return false
            }
            
            return result.getOrDefault(false)
            
        } catch (e: Throwable) {
            // ABSOLUTE guard against any crash in the privileged process
            logE("🔥 CRITICAL ERROR in PrivilegedServiceImpl: ${e.message}")
            return false
        } finally {
            logD("🏁 EXIT: setPackageNetworkingEnabled")
        }
    }
    
    private fun getConnectivityManagerInstance(): IConnectivityManager {
        // Use raw ServiceManager.getService() + typed AIDL — this runs in the Shizuku
        // user service process which already has system UID. Hidden APIs are accessible
        // here without ShizukuBinderWrapper. Matches InstallerX's approach exactly.
        val smClass = Class.forName("android.os.ServiceManager")
        val getService = smClass.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "connectivity") as? IBinder
            ?: throw RuntimeException("connectivity service not found")

        return IConnectivityManager.Stub.asInterface(binder)
            ?: throw RuntimeException("asInterface returned null")
    }
    
    private fun logD(message: String) {
        Log.d(TAG, message)
        logCallback?.let { runCatching { it.log(0, TAG, message) } }
    }

    private fun logW(message: String) {
        Log.w(TAG, message)
        logCallback?.let { runCatching { it.log(2, TAG, message) } }
    }

    private fun logE(message: String) {
        Log.e(TAG, message)
        logCallback?.let { runCatching { it.log(3, TAG, message) } }
    }
}

