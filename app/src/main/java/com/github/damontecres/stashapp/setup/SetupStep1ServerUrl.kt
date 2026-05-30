package com.github.damontecres.stashapp.setup

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionEditText
import androidx.lifecycle.lifecycleScope
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.TestResult
import com.github.damontecres.stashapp.util.isNotNullOrBlank
import kotlinx.coroutines.launch

class SetupStep1ServerUrl : SetupGuidedStepSupportFragment() {
    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            getString(R.string.stash_server_url),
            getString(R.string.stash_server_url_desc),
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
                .id(SetupFragment.ACTION_SERVER_URL)
                .title("Server URL")
                .descriptionInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
                .descriptionEditable(true)
                .hasNext(true)
                .build(),
        )
        actions.add(
            GuidedAction
                .Builder(requireContext())
                .id(GuidedAction.ACTION_ID_OK)
                .title(R.string.stashapp_actions_submit)
                .hasNext(true)
                .enabled(true)
                .build(),
        )
    }

    override fun onGuidedActionEditedAndProceed(action: GuidedAction): Long {
        if (action.id == SetupFragment.ACTION_SERVER_URL) {
//            val okAction = findActionById(GuidedAction.ACTION_ID_OK)
//            okAction.isEnabled = serverUrl.isNotNullOrBlank()
//            notifyActionChanged(findActionPositionById(GuidedAction.ACTION_ID_OK))
            testServerUrl(action)
        }
        return GuidedAction.ACTION_ID_CURRENT
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (action.id == GuidedAction.ACTION_ID_OK) {
            val serverUrlAction = findActionById(SetupFragment.ACTION_SERVER_URL)!!
            testServerUrl(serverUrlAction)
        }
    }

    private fun testServerUrl(action: GuidedAction) {
        var serverUrl = action.description
        if (serverUrl.isNullOrBlank()) {
            // Work around in weird cases where the editDescription isn't updated, but the text field has text
            // This attempts to find that text field and get its value
            try {
                val view = getActionItemView(findActionPositionById(action.id))!!
                val descView =
                    view.findViewById<GuidedActionEditText>(androidx.leanback.R.id.guidedactions_item_description)
                serverUrl = descView.text?.toString()
            } catch (ex: Exception) {
                Log.w(TAG, "Exception getting view", ex)
            }
        }
        if (serverUrl.isNotNullOrBlank()) {
            val state = SetupState(serverUrl)
            viewLifecycleOwner.lifecycleScope.launch(StashCoroutineExceptionHandler()) {
                val result = testConnection(serverUrl.toString(), null, false)
                when (result) {
                    TestResult.SelfSignedCertRequired -> {
                        nextStep(SetupStep2Ssl(state))
                    }

                    TestResult.AuthRequired -> {
                        nextStep(SetupStep3ApiKey(state))
                    }

                    is TestResult.Success,
                    is TestResult.UnsupportedVersion,
                    -> {
                        nextStep(SetupStep4Pin(state))
                    }

                    is TestResult.Error,
                    TestResult.SslRequired,
                    -> {
                        // The connection failed (bad/unreachable URL, plain-HTTP, etc.).
                        // Surface the reason so the user understands why setup didn't proceed:
                        // inline on the OK action's description AND a guaranteed-visible Toast.
                        showConnectionError(result.message)
                    }
                }
            }
        }
    }

    /**
     * Make the connection failure clearly visible to the user. Updates the submit action's
     * description (inline, persistent) and shows a Toast as a guaranteed-visible fallback in
     * case the action row is scrolled off-screen.
     */
    private fun showConnectionError(message: String) {
        val displayMessage = message.ifBlank { "Could not connect to the server. Check the URL and try again." }
        val okAction = findActionById(GuidedAction.ACTION_ID_OK)
        if (okAction != null) {
            okAction.description = displayMessage
            notifyActionChanged(findActionPositionById(GuidedAction.ACTION_ID_OK))
        }
        Toast
            .makeText(requireContext(), displayMessage, Toast.LENGTH_LONG)
            .show()
        Log.w(TAG, "Connection test failed: $message")
    }

    companion object {
        private const val TAG = "SetupStep1ServerUrl"
    }
}
