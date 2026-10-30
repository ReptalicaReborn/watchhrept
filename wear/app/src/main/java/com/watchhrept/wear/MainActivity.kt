package com.watchhrept.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.watchhrept.wear.ui.AppViewModel
import com.watchhrept.wear.ui.WatchApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchApp(viewModel) }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startLiveUpdates()
    }

    override fun onStop() {
        viewModel.stopLiveUpdates()
        super.onStop()
    }
}
