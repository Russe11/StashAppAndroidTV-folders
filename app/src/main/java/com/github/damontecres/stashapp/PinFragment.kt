package com.github.damontecres.stashapp

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import com.github.damontecres.stashapp.util.BiometricLock
import com.github.damontecres.stashapp.views.models.ServerViewModel

class PinFragment : Fragment(R.layout.pin_dialog) {
    private val serverViewModel: ServerViewModel by activityViewModels<ServerViewModel>()

    private lateinit var pinEditText: EditText

    /**
     * True once a biometric prompt has been shown for this gate appearance, so we don't re-show it
     * (e.g. on every [onResume]) after the user cancels and falls back to the PIN.
     */
    private var biometricPromptShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pinCode =
            PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getString(getString(R.string.pref_key_pin_code), "")
        if (pinCode.isNullOrBlank()) {
            startMain()
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        pinEditText = view.findViewById(R.id.pin_edit_text)

        val pinCode =
            PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getString(getString(R.string.pref_key_pin_code), "")

        val submit = view.findViewById<Button>(R.id.pin_submit)
        submit.setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            if (enteredPin == pinCode) {
                startMain()
            } else {
                Toast.makeText(requireContext(), "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
        }

        val autoSubmitPin =
            PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.pref_key_pin_code_auto), false)
        if (autoSubmitPin) {
            submit.visibility = View.INVISIBLE
            pinEditText.doAfterTextChanged {
                val enteredPin = it.toString()
                if (enteredPin == pinCode) {
                    startMain()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        maybePromptBiometric()
        pinEditText.requestFocus()
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)!!
        imm.showSoftInput(pinEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onStop() {
        super.onStop()
        // Reset so the prompt is offered again the next time the gate is shown (re-lock on
        // backgrounding is driven by RootActivity navigating back to Destination.Pin).
        biometricPromptShown = false
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)!!
        imm.hideSoftInputFromWindow(pinEditText.windowToken, 0)
    }

    /**
     * If the user has opted in to biometric unlock and the device can present a prompt, show
     * [BiometricPrompt]. On success the gate clears exactly as a correct PIN would; on failure or
     * cancellation the PIN entry remains as the fallback. The biometric result is never logged or
     * persisted.
     */
    private fun maybePromptBiometric() {
        if (biometricPromptShown) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val pinSet =
            !prefs.getString(getString(R.string.pref_key_pin_code), "").isNullOrBlank()
        val biometricEnabled =
            prefs.getBoolean(getString(R.string.pref_key_require_biometric), false)
        val biometricManager = BiometricManager.from(requireContext())
        val biometricAvailable =
            BiometricLock.isBiometricAvailable(
                biometricManager.canAuthenticate(BiometricLock.ALLOWED_AUTHENTICATORS),
            )

        if (!BiometricLock.shouldPromptBiometric(pinSet, biometricEnabled, biometricAvailable)) {
            return
        }

        biometricPromptShown = true

        val executor = ContextCompat.getMainExecutor(requireContext())
        val prompt =
            BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        // Do not log the result; just unlock.
                        startMain()
                    }
                    // onAuthenticationError / onAuthenticationFailed intentionally leave the gate
                    // shown so the PIN remains available as the fallback. Nothing is logged.
                },
            )
        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setAllowedAuthenticators(BiometricLock.ALLOWED_AUTHENTICATORS)
                .build()
        prompt.authenticate(promptInfo)
    }

    private fun startMain() {
        serverViewModel.navigationManager.clearPinFragment()
    }
}
