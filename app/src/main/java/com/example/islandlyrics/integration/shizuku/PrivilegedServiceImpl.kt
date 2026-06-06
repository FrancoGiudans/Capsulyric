package com.example.islandlyrics.integration.shizuku

import androidx.annotation.Keep
import com.example.islandlyrics.IPrivilegedService
import com.example.islandlyrics.IPrivilegedLogCallback
import android.util.Log
import android.os.IBinder
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
                    logD("Step 1: Resolving compatible firewall backend...")
                    FirewallCompat.setPackageNetworkingEnabled(uid, enabled) { serviceName, stubClassName, label ->
                        getSystemServiceProxy(serviceName, stubClassName, label)
                    }
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
    
    private fun getSystemServiceProxy(serviceName: String, stubClassName: String, label: String): Any {
        // Use raw ServiceManager.getService() in the Shizuku user-service process to avoid
        // verifier failures on vendor ROMs where specific hidden firewall methods are absent.
        val smClass = Class.forName("android.os.ServiceManager")
        val getService = smClass.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, serviceName) as? IBinder
            ?: throw RuntimeException("$serviceName service not found")
        val stubClass = Class.forName(stubClassName)
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, binder)
            ?: throw RuntimeException("$label asInterface returned null")
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

