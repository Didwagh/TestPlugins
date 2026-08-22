package com.example

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class ExamplePlugin : Plugin() {
    override fun load(context: Context) {
        // 1. Initialize the config so it can read/write settings
        PluginConfig.init(context)

        // 2. Register the provider
        registerMainAPI(ExampleProvider())

        // 3. Enable the Settings Gear Icon
        openSettings = { activity: FragmentActivity ->
            val settings = SettingsBottomSheet()
            settings.show(activity.supportFragmentManager, "settings")
        }
    }
}