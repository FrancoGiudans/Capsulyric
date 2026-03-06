package com.example.islandlyrics.shizuku

import androidx.annotation.Keep
import com.example.islandlyrics.IPrivilegedService

@Keep
class PrivilegedServiceImpl : IPrivilegedService.Stub() {

    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean) {
        val cm = ShizukuHook.hookedConnectivityManager
        try {
            // FIREWALL_CHAIN_OEM_DENY_3 = 9
            cm.setFirewallChainEnabled(9, true)
            // FIREWALL_RULE_DEFAULT = 0, FIREWALL_RULE_ALLOW = 1, FIREWALL_RULE_DENY = 2
            val rule = if (enabled) 0 else 2
            cm.setUidFirewallRule(9, uid, rule)
            // No need for explicit printStackTrace, follow project logging if available
        } catch (e: Exception) {
            throw RuntimeException("Failed to toggle network for UID $uid", e)
        }
    }
}
