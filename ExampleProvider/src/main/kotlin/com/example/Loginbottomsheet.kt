package com.example
 
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
 
/**
 * One-time Telegram login screen. No XML layout — built in code so there's
 * nothing extra to wire up in res/. Open it from the provider's settings
 * (gear) icon in CloudStream.
 *
 * Flow: enter phone -> enter code sent via Telegram -> (if 2FA enabled)
 * enter password -> Ready. TDLib persists the session afterward, so this
 * only needs to happen once per install.
 */
class LoginBottomSheet : BottomSheetDialogFragment() {
 
    private lateinit var statusText: TextView
    private lateinit var input: EditText
    private lateinit var submitButton: Button
 
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val padding = (16 * resources.displayMetrics.density).toInt()
 
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
 
        statusText = TextView(requireContext()).apply {
            text = "Enter your Telegram phone number (e.g. +15551234567)"
            textSize = 16f
        }
 
        input = EditText(requireContext()).apply {
            hint = "Phone number"
        }
 
        submitButton = Button(requireContext()).apply {
            text = "Submit"
            setOnClickListener { onSubmit() }
        }
 
        root.addView(statusText)
        root.addView(input)
        root.addView(submitButton)
        return root
    }
 
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
 
        if (!AppContextHolder.isReady()) {
            statusText.text = "Plugin not fully loaded yet — reopen CloudStream and try again."
            submitButton.isEnabled = false
            return
        }
 
        TelegramClient.init()
 
        lifecycleScope.launch {
            TelegramClient.authState.collect { state ->
                when (state) {
                    is AuthState.Idle -> {
                        statusText.text = "Starting..."
                        input.isEnabled = false
                    }
                    is AuthState.WaitPhone -> {
                        statusText.text = "Enter your Telegram phone number (e.g. +15551234567)"
                        input.hint = "Phone number"
                        input.isEnabled = true
                        input.text.clear()
                    }
                    is AuthState.WaitCode -> {
                        statusText.text = "Enter the login code Telegram just sent you"
                        input.hint = "Code"
                        input.isEnabled = true
                        input.text.clear()
                    }
                    is AuthState.WaitPassword -> {
                        statusText.text = "Enter your 2FA password"
                        input.hint = "Password"
                        input.isEnabled = true
                        input.text.clear()
                    }
                    is AuthState.Ready -> {
                        statusText.text = "Logged in! You can close this and play videos now."
                        input.isEnabled = false
                        submitButton.isEnabled = false
                    }
                    is AuthState.Error -> {
                        statusText.text = "Error: ${state.message}"
                    }
                }
            }
        }
    }
 
    private fun onSubmit() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
 
        when (TelegramClient.authState.value) {
            is AuthState.WaitPhone -> TelegramClient.submitPhone(text)
            is AuthState.WaitCode -> TelegramClient.submitCode(text)
            is AuthState.WaitPassword -> TelegramClient.submitPassword(text)
            else -> { /* not in an input-accepting state, ignore */ }
        }
    }
}
 