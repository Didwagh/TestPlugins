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

        // 3. FIX: Changed 'activity: FragmentActivity' to 'context' 
        // then cast it to FragmentActivity to show the dialog.
        openSettings = { ctx ->
            val fragmentActivity = ctx as? FragmentActivity
            if (fragmentActivity != null) {
                val settings = SettingsBottomSheet()
                settings.show(fragmentActivity.supportFragmentManager, "settings")
            }
        }
    }
}