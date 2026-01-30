package com.example.islandlyrics

import java.util.Objects

data class WhitelistItem(var packageName: String, var isEnabled: Boolean) : Comparable<WhitelistItem> {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as WhitelistItem
        return packageName == that.packageName
    }

    override fun hashCode(): Int {
        return Objects.hash(packageName)
    }

    override fun compareTo(other: WhitelistItem): Int {
        return packageName.compareTo(other.packageName)
    }
}
