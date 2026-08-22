package com.example

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the companion app's host/IP and Telegram channel_id so they can
 * be edited from CloudStream's plugin settings screen instead of being
 * hardcoded and requiring a rebuild every time your phone's IP changes or
 * you switch channels.
 *
 * Defaults to 127.0.0.1 (correct when CloudStream and the companion app
 * are on the same device). Change this via the settings gear next to
 * "Telegram Vault" in CloudStream's plugin list to your phone's LAN IP
 * (e.g. 192.168.1.42) when using a TV or any other separate device.
 */
object PluginConfig {
    private const val PREFS_NAME = "telegram_vault_config"
    private const val KEY_HOST = "host"
    private const val KEY_CHANNEL_ID = "channel_id"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var host: String
        get() = prefs.getString(KEY_HOST, "127.0.0.1") ?: "127.0.0.1"
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var channelId: Long
        get() = prefs.getLong(KEY_CHANNEL_ID, 0L)
        set(value) = prefs.edit().putLong(KEY_CHANNEL_ID, value).apply()

    fun isConfigured(): Boolean = channelId != 0L
}
