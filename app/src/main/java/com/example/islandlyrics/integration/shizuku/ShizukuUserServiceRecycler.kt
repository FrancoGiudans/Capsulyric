package com.example.islandlyrics.integration.shizuku

import android.content.ComponentName
import com.example.islandlyrics.BuildConfig
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.example.islandlyrics.IPrivilegedService
import com.example.islandlyrics.core.logging.AppLogger
import rikka.shizuku.Shizuku
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ShizukuUserServiceRecycler {

    private val serviceMutex = Mutex()
    private var cachedService: IPrivilegedService? = null
    private var serviceConnection: ServiceConnection? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null
    private var lastPingAttempt = 0L
    private const val TAG = "ShizukuRecycler"
    private const val PING_INTERVAL_MS = 5000L  // Only ping every 5 seconds to avoid overhead
    private const val BIND_TIMEOUT_MS = 10000L  // 10 second timeout for service binding

    private val log get() = AppLogger.getInstance()

    /**
     * Gets or creates a persistent connection to the privileged service.
     * Uses caching to avoid repeated bind/unbind cycles.
     * Only validates connection periodically to minimize Binder calls and avoid throttling.
     */
    suspend fun getPrivilegedService(): IPrivilegedService {
        serviceMutex.withLock {
            cachedService?.let { cached ->
                val now = System.currentTimeMillis()
                // Only ping periodically, not on every call, to avoid Binder overhead
                if (now - lastPingAttempt > PING_INTERVAL_MS) {
                    try {
                        if (cached.asBinder().pingBinder()) {
                            lastPingAttempt = now
                            log.d(TAG, "Service cache still valid (ping successful)")
                            return cached
                        }
                    } catch (e: Exception) {
                        // Binder is dead, will reinitialize below
                        log.w(TAG, "Service ping failed, rebinding: ${e.message}")
                    }
                    lastPingAttempt = now
                    cachedService = null
                } else {
                    // Recent ping succeeded or not yet time to check, use cached
                    log.d(TAG, "Using cached service (${now - lastPingAttempt}ms since last ping)")
                    return cached
                }
            }

            // Need to establish new connection
            log.d(TAG, "Establishing new service connection...")
            return establishServiceConnection().also {
                lastPingAttempt = System.currentTimeMillis()
                log.d(TAG, "Service connection established successfully")
            }
        }
    }

    private suspend fun establishServiceConnection(): IPrivilegedService {
        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        log.d(TAG, "onServiceConnected called with service=$service")
                        if (service != null) {
                            val privileged = IPrivilegedService.Stub.asInterface(service)
                            cachedService = privileged
                            serviceConnection = this
                            continuation.resume(privileged)
                        } else {
                            log.e(TAG, "onServiceConnected but binder is null!")
                            continuation.resumeWithException(Exception("Shizuku UserService bound but returned null binder"))
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        log.w(TAG, "Service disconnected unexpectedly")
                        cachedService = null
                    }
                }

                val args = Shizuku.UserServiceArgs(
                    ComponentName(BuildConfig.APPLICATION_ID, PrivilegedServiceImpl::class.java.name)
                )
                    .daemon(true)  // Keep as daemon to avoid repeated reconnections
                    .processNameSuffix("privileged")
                    .debuggable(BuildConfig.DEBUG)
                    .version(2)

                serviceArgs = args

                try {
                    log.d(TAG, "Calling Shizuku.bindUserService()...")
                    Shizuku.bindUserService(args, connection)
                } catch (e: Exception) {
                    log.e(TAG, "bindUserService threw exception: ${e.message}", e)
                    continuation.resumeWithException(e)
                }

                continuation.invokeOnCancellation {
                    log.d(TAG, "Service binding cancelled, unbinding...")
                    try {
                        Shizuku.unbindUserService(args, connection, true)
                    } catch (ignored: Exception) {
                        log.w(TAG, "Error during unbind: ${ignored.message}")
                    }
                }
            }
        } ?: throw Exception("Service binding timed out after ${BIND_TIMEOUT_MS}ms")
    }

    /**
     * Executes an action with the privileged service, using cached connection.
     */
    suspend fun <T> executeWithService(action: suspend (IPrivilegedService) -> T): T {
        log.d(TAG, "executeWithService called")
        return try {
            action(getPrivilegedService()).also {
                log.d(TAG, "executeWithService completed successfully")
            }
        } catch (e: Exception) {
            log.e(TAG, "executeWithService failed: ${e.message}", e)
            throw e
        }
    }
}
