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
        AppContextHolder.appContext = context.applicationContext

        registerMainAPI(ExampleProvider())

        // Tapping this provider's settings (gear icon) in CloudStream now
        // opens the Telegram login sheet instead of the old blank info panel.
        openSettings = {
            activity?.let {
                LoginBottomSheet().show(it.supportFragmentManager, "TelegramLogin")
            }
        }
    }
}