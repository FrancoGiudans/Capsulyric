package com.example.islandlyrics

import android.annotation.SuppressLint
import java.io.BufferedReader
import java.io.InputStreamReader

object RomUtils {

    fun getRomInfo(): String {
        // Known keys for various ROMs
        
        // HyperOS / MIUI
        // Try precise version first
        val hyperOsVersion = getSystemProperty("ro.mi.os.version.name") 
        if (!hyperOsVersion.isNullOrEmpty()) {
             val inc = getSystemProperty("ro.build.version.incremental")
             if (inc.isNotEmpty() && !hyperOsVersion.contains(inc)) {
                 return "HyperOS $hyperOsVersion ($inc)"
             }
             return "HyperOS $hyperOsVersion"
        }
        
        // ColorOS / OxygenOS
        val colorOsVersion = getSystemProperty("ro.build.version.opporom")
        if (!colorOsVersion.isNullOrEmpty()) {
             val detailed = getSystemProperty("ro.rom.version") // Example fallback
             return if (!detailed.isNullOrEmpty()) "ColorOS/OxygenOS $colorOsVersion ($detailed)" else "ColorOS/OxygenOS $colorOsVersion"
        }

        // FuntouchOS / OriginOS
        val vivoOsVersion = getSystemProperty("ro.vivo.os.version")
        if (!vivoOsVersion.isNullOrEmpty()) {
             val display = getSystemProperty("ro.vivo.os.build.display.id")
             return if (display.isNotEmpty()) "Funtouch/OriginOS $vivoOsVersion ($display)" else "Funtouch/OriginOS $vivoOsVersion"
        }
        
        // Flyme
        val flymeUi = getSystemProperty("ro.flyme.ui.version.name")
        if (flymeUi.isNotEmpty()) {
             return "Flyme $flymeUi"
        }

        // Custom ROMs
        val derpfestVersion = getSystemProperty("ro.derpfest.version")
        if (!derpfestVersion.isNullOrEmpty()) return "DerpFest $derpfestVersion"

        val lineageVersion = getSystemProperty("ro.lineage.version")
        if (!lineageVersion.isNullOrEmpty()) return "LineageOS $lineageVersion"
        
        val pixelExperience = getSystemProperty("org.pixelexperience.version")
        if (!pixelExperience.isNullOrEmpty()) return "PixelExperience $pixelExperience"
        
        val evoX = getSystemProperty("ro.evolution.version")
        if (!evoX.isNullOrEmpty()) return "Evolution X $evoX"

        // Fallback to standard display ID if it looks like a custom ROM
        val displayId = getSystemProperty("ro.modversion")
        if (displayId.isNotEmpty()) return "Custom: $displayId"

        return ""
    }

    fun getRomType(): String {
        // HyperOS / MIUI
        if (getSystemProperty("ro.mi.os.version.name").isNotEmpty()) return "HyperOS"
        
        // ColorOS / OxygenOS
        if (getSystemProperty("ro.build.version.opporom").isNotEmpty()) return "ColorOS"
        
        // FuntouchOS / OriginOS
        if (getSystemProperty("ro.vivo.os.version").isNotEmpty()) {
             val version = getSystemProperty("ro.vivo.os.version")
             // Simple heuristic: newer versions likely OriginOS, but name isn't always clear
             // Just return "OriginOS/FuntouchOS" to be safe or check specific props if known
             return "OriginOS/FuntouchOS"
        }
        
        // Flyme
        if (getSystemProperty("ro.flyme.ui.version.name").isNotEmpty()) return "Flyme"

        // OneUI (Samsung)
        if (getSystemProperty("ro.build.version.sem").isNotEmpty() || 
            getSystemProperty("ro.build.version.sep").isNotEmpty() ||
            android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return "OneUI"

        // MagicOS (Honor)
        if (getSystemProperty("ro.build.version.magic").isNotEmpty()) return "MagicOS"
        
        // RealmeUI (often covered by ColorOS check, but just in case)
        if (getSystemProperty("ro.build.version.realmerom").isNotEmpty()) return "RealmeUI"

        // Custom ROMs
        if (getSystemProperty("ro.derpfest.version").isNotEmpty()) return "DerpFest"
        if (getSystemProperty("ro.lineage.version").isNotEmpty()) return "LineageOS"
        if (getSystemProperty("org.pixelexperience.version").isNotEmpty()) return "PixelExperience"
        if (getSystemProperty("ro.evolution.version").isNotEmpty()) return "Evolution X"
        
        return "AOSP"
    }

    fun isHyperOsVersionAtLeast(major: Int, minor: Int, patch: Int): Boolean {
        val versionStr = getSystemProperty("ro.mi.os.version.name")
        if (versionStr.isEmpty()) return false

        // Remove "OS" prefix if present (e.g. "OS1.0.1.0")
        val cleanVersion = versionStr.removePrefix("OS").trim()
        
        // Split by dots
        val parts = cleanVersion.split(".")
        if (parts.isEmpty()) return false

        try {
            val vMajor = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val vMinor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val vPatch = parts.getOrNull(2)?.toIntOrNull() ?: 0

            return vMajor > major || 
                   (vMajor == major && vMinor > minor) || 
                   (vMajor == major && vMinor == minor && vPatch >= patch)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String): String {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java)
            val value = getMethod.invoke(null, key) as String
            return value
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}
