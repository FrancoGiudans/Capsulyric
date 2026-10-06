/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.core.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import com.example.islandlyrics.core.logging.AppLogger

object RomUtils {
    var forcedRomType: String? = null

    @Volatile
    private var appContext: Context? = null

    private data class RomDetection(val type: String, val version: String = "")

    fun getRomInfo(): String {
        val rom = detectRom()
        return if (rom.version.isEmpty()) rom.type else "${rom.type} (${rom.version})"
    }

    fun getRomType(context: Context? = null): String {
        if (context != null) appContext = context.applicationContext
        return detectRom().type
    }

    private fun detectRom(): RomDetection {
        forcedRomType?.takeIf { it.isNotEmpty() }?.let { return RomDetection(it, "Forced") }

        // Dedicated third-party identifiers take precedence over inherited OEM/Lineage properties.
        if (Build.VERSION.SDK_INT >= 36) {
            val crDroid = getSystemProperty("ro.crdroid.version")
            if (crDroid.isNotEmpty()) {
                val version = getSystemProperty("ro.crdroid.display.version")
                    .ifEmpty { getSystemProperty("ro.crdroid.build.version") }
                    .ifEmpty { crDroid }
                return RomDetection("crDroid", version)
            }
            val omni = getSystemProperty("ro.omni.version")
            if (omni.isNotEmpty()) return RomDetection("OmniROM", omni)
        }

        for ((key, type) in listOf(
            "ro.derpfest.version" to "DerpFest",
            "org.pixelexperience.version" to "PixelExperience",
            "ro.evolution.version" to "Evolution X",
            "ro.lineage.version" to "LineageOS"
        )) {
            val version = getSystemProperty(key)
            if (version.isNotEmpty()) return RomDetection(type, version)
        }
        val modVersion = getSystemProperty("ro.modversion")
        if (modVersion.isNotEmpty()) return RomDetection("Custom", modVersion)

        val displayId = getSystemProperty("ro.build.display.id")
        val hyperOs = getSystemProperty("ro.mi.os.version.name")
        if (hyperOs.isNotEmpty()) {
            val incremental = getSystemProperty("ro.build.version.incremental")
            val version = if (incremental.isNotEmpty() && !hyperOs.contains(incremental)) {
                "$hyperOs / $incremental"
            } else hyperOs
            return RomDetection("HyperOS", version)
        }
        // Keep the existing MIUI-property compatibility without guessing from the manufacturer.
        val miui = getSystemProperty("ro.miui.ui.version.name")
        if (miui.isNotEmpty()) return RomDetection("HyperOS", miui)

        val realme = getSystemProperty("ro.build.version.realmeui")
            .ifEmpty { getSystemProperty("ro.build.version.realmerom") }
        if (realme.isNotEmpty()) return RomDetection("RealmeUI", realme)

        val oplus = getSystemProperty("ro.build.version.oplusrom")
            .ifEmpty { getSystemProperty("ro.build.version.opporom") }
        if (oplus.isNotEmpty()) {
            val displayVersion = getSystemProperty("ro.build.version.oplusrom.display")
            val colorOs = namedRom("ColorOS", getSystemProperty("ro.rom.version"))
                ?: namedRom("ColorOS", displayId)
            if (colorOs != null) {
                return RomDetection("ColorOS", displayVersion.ifEmpty { colorOs.version })
            }
            // OPlus properties identify a family, not ColorOS versus OxygenOS.
            if (Build.VERSION.SDK_INT >= 36 && getOplusSettingsName() == "ColorOS") {
                return RomDetection("ColorOS", displayVersion.ifEmpty { "OPlus $oplus" })
            }
            return RomDetection("Custom", "OPlus $oplus")
        }

        val vivoDisplay = getSystemProperty("ro.vivo.os.build.display.id")
        namedRom("OriginOS", vivoDisplay)?.let { return it }
        namedRom("FuntouchOS", vivoDisplay)?.let { return it }
        namedRom("FuntouchOS", vivoDisplay, "Funtouch OS")?.let { return it }
        val vivoVersion = getSystemProperty("ro.vivo.os.version")
        if (vivoDisplay.isNotEmpty() || vivoVersion.isNotEmpty()) {
            return RomDetection("Custom", "vivo ${vivoDisplay.ifEmpty { vivoVersion }}")
        }

        val flyme = getSystemProperty("ro.flyme.ui.version.name")
        if (flyme.isNotEmpty()) return RomDetection("Flyme", flyme)

        val oneUi = getSystemProperty("ro.build.version.oneui")
        if (oneUi.isNotEmpty()) {
            val encoded = oneUi.toIntOrNull()
            val version = if (encoded != null && encoded >= 10000) {
                val patch = encoded % 100
                "${encoded / 10000}.${encoded / 100 % 100}" + if (patch == 0) "" else ".$patch"
            } else oneUi
            return RomDetection("OneUI", version)
        }

        val magic = getSystemProperty("ro.build.version.magic")
        if (magic.isNotEmpty()) return RomDetection("MagicOS", magic)

        val samsungLegacy = getSystemProperty("ro.build.version.sem")
            .ifEmpty { getSystemProperty("ro.build.version.sep") }
        if (samsungLegacy.isNotEmpty()) return RomDetection("Custom", "Samsung $samsungLegacy")
        return RomDetection("Unknown")
    }

    private fun namedRom(type: String, value: String, name: String = type): RomDetection? {
        val match = Regex("^${Regex.escape(name)}(?=$|[\\s\\d._-])", RegexOption.IGNORE_CASE)
            .find(value) ?: return null
        val version = value.substring(match.range.last + 1).trim().trimStart('-', '_').trim()
        return RomDetection(type, version)
    }

    // Settings resources belong to another APK, so their IDs cannot be referenced through our R class.
    @SuppressLint("DiscouragedApi")
    private fun getOplusSettingsName(): String {
        val context = appContext ?: run {
            AppLogger.getInstance().d("RomUtils", "Settings ROM name unavailable before Context initialization")
            return ""
        }
        return try {
            val resources = context.packageManager.getResourcesForApplication("com.android.settings")
            val id = resources.getIdentifier("device_brand_version_name", "string", "com.android.settings")
            if (id == 0) {
                AppLogger.getInstance().d("RomUtils", "Settings ROM name resource is missing")
                ""
            } else {
                resources.getString(id).trim().also {
                    if (it.isEmpty()) AppLogger.getInstance().d("RomUtils", "Settings ROM name resource is empty")
                }
            }
        } catch (e: Exception) {
            AppLogger.getInstance().e("RomUtils", "Failed to read Settings ROM name", e)
            ""
        }
    }

    fun isHyperOsVersionAtLeast(major: Int, minor: Int, patch: Int): Boolean {
        if (forcedRomType == "HyperOS") return true // Bypass check if explicitly forced
        if (!isHyperOs()) return false

        val hyperOsVersion = getSystemProperty("ro.mi.os.version.name")
        if (hyperOsVersion.isNotEmpty() && checkVersionString(hyperOsVersion, major, minor, patch)) return true
        
        // Fallback to incremental version
        val incremental = getSystemProperty("ro.build.version.incremental")
        if (incremental.isNotEmpty() && checkVersionString(incremental, major, minor, patch)) return true
        
        return false
    }

    private fun checkVersionString(versionStr: String, major: Int, minor: Int, patch: Int): Boolean {
        if (versionStr.isEmpty()) return false

        // Remove "OS" prefix if present
        val cleanVersion = versionStr.removePrefix("OS").trim()
        
        // Split by dots
        val parts = cleanVersion.split(".")
        if (parts.isEmpty()) return false

        val vMajor = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val vMinor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val vPatch = parts.getOrNull(2)?.toIntOrNull() ?: 0

        return vMajor > major ||
               (vMajor == major && vMinor > minor) ||
               (vMajor == major && vMinor == minor && vPatch >= patch)
    }

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String): String {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java)
            val value = (getMethod.invoke(null, key) as String).trim()
            if (value.isEmpty()) AppLogger.getInstance().d("RomUtils", "System property '$key' is missing")
            return value
        } catch (e: Exception) {
            AppLogger.getInstance().e("RomUtils", "Failed to read system property '$key'", e)
            return ""
        }
    }

    fun isHeavySkin(): Boolean {
        val type = getRomType()
        return type == "HyperOS" || type == "ColorOS" || type == "OriginOS" ||
               type == "FuntouchOS" || type == "OriginOS/FuntouchOS" ||
               type == "Flyme" || type == "OneUI" || type == "MagicOS" || type == "RealmeUI"
    }

    fun isHyperOs(): Boolean = getRomType() == "HyperOS"

    @Suppress("unused")
    fun isColorOsFamily(): Boolean = getRomType() == "ColorOS" || getRomType() == "RealmeUI"

    fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) || isHyperOs()

    fun isLiveUpdateSupported(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        if (isHyperOs()) {
            return isHyperOsVersionAtLeast(3, 0, 300)
        }
        return true
    }

    fun getAutostartPermissionIntent(context: Context): android.content.Intent? {
        val intent = android.content.Intent()
        val type = getRomType(context)
        
        try {
            when (type) {
                "HyperOS" -> {
                    intent.component = android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                }
                "ColorOS", "RealmeUI" -> {
                    intent.component = android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                    if (context.packageManager.resolveActivity(intent, 0) == null) {
                         intent.component = android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
                    }
                }
                "OriginOS", "FuntouchOS", "OriginOS/FuntouchOS" -> {
                    intent.component = android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                }
                "Flyme" -> {
                     intent.component = android.content.ComponentName("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC")
                     intent.addCategory(android.content.Intent.CATEGORY_DEFAULT)
                     intent.putExtra("packageName", context.packageName)
                }
                "OneUI" -> {
                    // Samsung usually doesn't have a direct "Autostart" activity accessible, 
                    // but "Battery > Background usage limits" is close.
                    // For now, return null to avoid confusion, or link to Battery optimization
                    return null 
                }
                else -> return null
            }
            
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                return intent
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun isIslandSupported(): Boolean {
        return getSystemProperty("persist.sys.feature.island") == "true"
    }

    fun getFocusProtocolVersion(context: Context): Int {
        return try {
            android.provider.Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol", 0
            )
        } catch (_: Exception) {
            0
        }
    }

    fun hasFocusPermission(context: Context): Boolean {
        return try {
            val uri = "content://miui.statusbar.notification.public".toUri()
            val extras = android.os.Bundle().apply {
                putString("package", context.packageName)
            }
            val bundle = context.contentResolver.call(uri, "canShowFocus", null, extras)
            bundle?.getBoolean("canShowFocus", false) ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 36) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            return nm?.canPostPromotedNotifications() ?: false
        }
        return false
    }
}
