package com.example.islandlyrics.integration.lastfm

data class LastFmCredentials(
    val apiKey: String,
    val apiSecret: String,
    val sessionKey: String? = null,
    val username: String? = null
) {
    val hasApiCredentials: Boolean
        get() = apiKey.isNotBlank() && apiSecret.isNotBlank()

    val isConnected: Boolean
        get() = hasApiCredentials && !sessionKey.isNullOrBlank()
}
