package com.example

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey

@CloudstreamPlugin
class ExamplePlugin : Plugin() {

    override fun load(context: Context) {
        // Register the main API
        registerMainAPI(ExampleProvider())
    }

    companion object {
        const val PREF_HOST = "companion_host"
        const val PREF_CHANNEL_ID = "telegram_channel_id"
    }
}