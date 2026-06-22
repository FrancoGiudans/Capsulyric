package com.example.islandlyrics.runtime.metadata

import android.content.Context
import com.example.islandlyrics.rules.ParserRuleHelper
import java.util.concurrent.ConcurrentHashMap

class AppNameResolver(
    private val context: Context
) {
    private val cache = ConcurrentHashMap<String, String>()

    fun resolve(packageName: String): String {
        cache[packageName]?.let { return it }

        val customName = ParserRuleHelper.getRuleForPackage(context, packageName)
            ?.customName
            ?.takeIf { it.isNotBlank() }
        if (customName != null) {
            cache[packageName] = customName
            return customName
        }

        return runCatching {
            val packageManager = context.packageManager
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
            .also { name ->
                if (name.isNotEmpty()) {
                    cache[packageName] = name
                }
            }
    }
}
