package com.example

import android.content.Context
import android.content.SharedPreferences

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
        get() = prefs.getLong(KEY_CHANNEL_ID, -1004374443616L) // Using your default ID
        set(value) = prefs.edit().putLong(KEY_CHANNEL_ID, value).apply()

    fun isConfigured(): Boolean = channelId != 0L
}