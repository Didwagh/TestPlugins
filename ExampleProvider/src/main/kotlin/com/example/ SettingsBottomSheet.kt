package com.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(context).apply {
            text = "TeleStream Settings"
            textSize = 18f
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        layout.addView(title)

        val ipLabel = TextView(context).apply { text = "Companion App IP (e.g. 127.0.0.1)" }
        val ipInput = EditText(context).apply {
            setText(PluginConfig.getServerIp())
        }
        layout.addView(ipLabel)
        layout.addView(ipInput)

        val portLabel = TextView(context).apply { text = "Port (Default: 38471)" }
        val portInput = EditText(context).apply {
            setText(PluginConfig.getServerPort().toString())
        }
        layout.addView(portLabel)
        layout.addView(portInput)

        val channelLabel = TextView(context).apply { text = "Channel ID (e.g. -1001234567890)" }
        val channelInput = EditText(context).apply {
            setText(PluginConfig.getChannelId())
        }
        layout.addView(channelLabel)
        layout.addView(channelInput)

        val saveButton = Button(context).apply {
            text = "Save Settings"
            setOnClickListener {
                val ip = ipInput.text.toString().trim()
                val port = portInput.text.toString().trim().toIntOrNull() ?: 38471
                val channelId = channelInput.text.toString().trim()

                PluginConfig.setServerIp(ip)
                PluginConfig.setServerPort(port)
                PluginConfig.setChannelId(channelId)

                Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
        layout.addView(saveButton)

        return layout
    }
}