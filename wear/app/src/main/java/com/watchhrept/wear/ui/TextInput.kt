package com.watchhrept.wear.ui

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.wear.input.RemoteInputIntentHelper

private const val RESULT_KEY = "text"

/**
 * Returns a launcher for the system Wear input screen (voice, keyboard, emoji, or typing on the
 * paired phone). [onResult] receives the entered text.
 */
@Composable
fun rememberTextInput(onResult: (String) -> Unit): (label: String, choices: List<String>) -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data: Intent = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(RESULT_KEY)?.toString()
        if (!text.isNullOrBlank()) currentOnResult(text)
    }
    return remember(launcher) {
        { label: String, choices: List<String> ->
            val remoteInput = RemoteInput.Builder(RESULT_KEY)
                .setLabel(label)
                .apply { if (choices.isNotEmpty()) setChoices(choices.toTypedArray<CharSequence>()) }
                .build()
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
            launcher.launch(intent)
        }
    }
}
