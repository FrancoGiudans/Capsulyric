package com.example.islandlyrics.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ShizukuNotWorkException(cause: Throwable? = null) : Exception("Shizuku is not working or permission denied.", cause)

suspend fun <T> requireShizukuPermissionGranted(action: suspend () -> T): T {
    try {
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return action()
        } else {
            throw ShizukuNotWorkException(Exception("Shizuku permission denied or not running"))
        }
    } catch (e: Exception) {
        throw ShizukuNotWorkException(e)
    }
}
