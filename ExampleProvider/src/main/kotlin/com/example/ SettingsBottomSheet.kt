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

/**
 * Opened via the gear icon next to "Telegram Vault" in CloudStream's
 * plugin list. Lets you set the companion app's host/IP and the
 * Telegram channel_id without rebuilding the plugin.
 */
class SettingsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val padding = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val title = TextView(requireContext()).apply {
            text = "Telegram Vault Settings"
            textSize = 18f
        }

        val hostLabel = TextView(requireContext()).apply {
            text = "\nCompanion app host/IP"
            textSize = 14f
        }
        val hostInput = EditText(requireContext()).apply {
            hint = "127.0.0.1 (same device) or e.g. 192.168.1.42 (TV)"
            setText(PluginConfig.host)
        }

        val channelLabel = TextView(requireContext()).apply {
            text = "\nTelegram channel_id"
            textSize = 14f
        }
        val channelInput = EditText(requireContext()).apply {
            hint = "e.g. -1004374443616"
            val current = PluginConfig.channelId
            if (current != 0L) setText(current.toString())
        }

        val saveButton = Button(requireContext()).apply { text = "Save" }
        saveButton.setOnClickListener {
            val host = hostInput.text.toString().trim()
            val channelId = channelInput.text.toString().trim().toLongOrNull()

            if (host.isEmpty()) {
                Toast.makeText(requireContext(), "Host can't be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (channelId == null) {
                Toast.makeText(requireContext(), "Enter a valid channel_id (a negative number)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            PluginConfig.host = host
            PluginConfig.channelId = channelId
            Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        root.addView(title)
        root.addView(hostLabel)
        root.addView(hostInput)
        root.addView(channelLabel)
        root.addView(channelInput)
        root.addView(saveButton)
        return root
    }
}
