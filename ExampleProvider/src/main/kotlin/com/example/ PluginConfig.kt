package com.example

import android.content.Context
import android.content.SharedPreferences

object PluginConfig {
    private const val PREFS_NAME = "TeleStreamPluginPrefs"
    private const val KEY_IP = "server_ip"
    private const val KEY_PORT = "server_port"
    private const val KEY_CHANNEL_ID = "channel_id"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBaseUrl(): String {
        val ip = prefs.getString(KEY_IP, "127.0.0.1") ?: "127.0.0.1"
        val port = prefs.getInt(KEY_PORT, 38471)
        val cleanIp = ip.removePrefix("http://").removePrefix("https://").trimEnd('/')
        return "http://$cleanIp:$port"
    }

    fun getServerIp(): String {
        return prefs.getString(KEY_IP, "127.0.0.1") ?: "127.0.0.1"
    }

    fun getServerPort(): Int {
        return prefs.getInt(KEY_PORT, 38471)
    }

    fun getChannelId(): String {
        return prefs.getString(KEY_CHANNEL_ID, "") ?: ""
    }

    fun setServerIp(ip: String) {
        prefs.edit().putString(KEY_IP, ip.trim()).apply()
    }

    fun setServerPort(port: Int) {
        prefs.edit().putInt(KEY_PORT, port).apply()
    }

    fun setChannelId(channelId: String) {
        prefs.edit().putString(KEY_CHANNEL_ID, channelId.trim()).apply()
    }
}