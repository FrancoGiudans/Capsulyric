package com.example.islandlyrics.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.example.islandlyrics.IPrivilegedService
import rikka.shizuku.Shizuku
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

object ShizukuUserServiceRecycler {

    suspend fun getPrivilegedService(): IPrivilegedService = callbackFlow {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service != null) {
                    val privileged = IPrivilegedService.Stub.asInterface(service)
                    trySend(privileged)
                } else {
                    close(Exception("Shizuku UserService bound but returned null binder"))
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                close(Exception("Shizuku UserService disconnected"))
            }
        }

        val args = Shizuku.UserServiceArgs(
            ComponentName(com.example.islandlyrics.BuildConfig.APPLICATION_ID, PrivilegedServiceImpl::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(com.example.islandlyrics.BuildConfig.DEBUG) // Use build variant flag
            .version(1)

        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (ignored: Exception) {}
        }
    }.first()
}
