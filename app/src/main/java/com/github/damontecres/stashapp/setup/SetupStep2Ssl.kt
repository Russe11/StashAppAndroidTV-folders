package com.github.damontecres.stashapp.setup

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.util.StashClient
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.TestResult
import com.github.damontecres.stashapp.views.dialog.ConfirmationDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupStep2Ssl(
    private val setupState: SetupState,
) : SetupGuidedStepSupportFragment() {
    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            getString(R.string.setup_ssl_title),
            getString(R.string.setup_ssl_description),
            null,
            ContextCompat.getDrawable(requireContext(), R.drawable.stash_logo),
        )

    override fun onCreateActions(
        actions: MutableList<GuidedAction>,
        savedInstanceState: Bundle?,
    ) {
        actions.add(
            GuidedAction
                .Builder(requireContext())
                .id(GuidedAction.ACTION_ID_YES)
                .title("Yes")
                .description("Pin this server's certificate")
                .build(),
        )
        actions.add(
            GuidedAction
                .Builder(requireContext())
                .id(GuidedAction.ACTION_ID_NO)
                .title("No")
                .description("Enter a different URL")
                .build(),
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (action.id == GuidedAction.ACTION_ID_YES) {
            captureAndPinThenConnect()
        } else {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    /**
     * Trust-On-First-Use: capture the leaf certificate the server presents, show the user its
     * SHA-256 fingerprint to accept, persist it as the per-server pin, then connect. After this,
     * the trust manager trusts exactly this cert (and rejects a silently-swapped one).
     */
    private fun captureAndPinThenConnect() {
        val newState = setupState.copy(trustCerts = true)
        viewLifecycleOwner.lifecycleScope.launch(StashCoroutineExceptionHandler()) {
            val fingerprint =
                withContext(Dispatchers.IO) {
                    StashClient.captureLeafFingerprint(newState.serverUrl)
                }
            if (fingerprint == null) {
                // Either the server is already system-trusted (no pin needed) or unreachable.
                // Try connecting directly; the trust manager will accept a system cert and reject
                // an un-pinned self-signed one with a clear error.
                connect(newState)
                return@launch
            }
            ConfirmationDialogFragment(
                getString(R.string.setup_ssl_pin_prompt, prettyFingerprint(fingerprint)),
            ) { dialog, which ->
                if (which == android.content.DialogInterface.BUTTON_POSITIVE) {
                    StashServer.setCertPin(requireContext(), newState.serverUrl, fingerprint)
                    viewLifecycleOwner.lifecycleScope.launch(StashCoroutineExceptionHandler()) {
                        connect(newState)
                    }
                } else {
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }.show(childFragmentManager, "cert-pin")
        }
    }

    private suspend fun connect(newState: SetupState) {
        when (val result = testConnection(newState.serverUrl, null, true)) {
            TestResult.AuthRequired -> {
                nextStep(SetupStep3ApiKey(newState))
            }

            is TestResult.Error,
            TestResult.SslRequired,
            TestResult.SelfSignedCertRequired,
            -> {
                // The cert didn't match the pin we just stored, or another error. Surface it and
                // let the user re-pin by re-entering setup rather than silently locking out.
                Toast
                    .makeText(
                        requireContext(),
                        getString(R.string.setup_ssl_pin_failed, result.message),
                        Toast.LENGTH_LONG,
                    ).show()
                Log.w(TAG, "Pinned connect failed: ${result.message}")
                requireActivity().supportFragmentManager.popBackStack()
            }

            is TestResult.Success,
            is TestResult.UnsupportedVersion,
            -> {
                nextStep(SetupStep4Pin(newState))
            }
        }
    }

    /** Render the fingerprint in short, human-comparable blocks for the accept dialog. */
    private fun prettyFingerprint(fingerprint: String): String = fingerprint.removePrefix("sha256/")

    companion object {
        const val TAG = "SetupStep2Ssl"
    }
}
