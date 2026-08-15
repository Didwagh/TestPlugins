package com.example

import android.content.Context

/**
 * Simple holder so TelegramClient (a singleton object, not an Activity)
 * can still get a Context for TDLib's database directory.
 * Set once in ExamplePlugin.load().
 */
object AppContextHolder {
    lateinit var appContext: Context

    fun isReady(): Boolean = ::appContext.isInitialized
}