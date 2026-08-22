package com.example

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class ExamplePlugin : Plugin() {
    private var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as? AppCompatActivity
        PluginConfig.init(context)

        registerMainAPI(ExampleProvider())

        // Tapping the gear icon next to "Telegram Vault" in CloudStream's
        // plugin list opens this instead of rebuilding the plugin every
        // time your phone's IP or channel changes.
        openSettings = {
            activity?.let {
                SettingsBottomSheet().show(it.supportFragmentManager, "TelegramVaultSettings")
            }
        }
    }
}
