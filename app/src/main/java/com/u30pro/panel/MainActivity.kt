package com.u30pro.panel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.u30pro.panel.ui.PanelApp
import com.u30pro.panel.ui.PanelViewModel
import com.u30pro.panel.ui.theme.U30Theme

class MainActivity : ComponentActivity() {

    private val viewModel: PanelViewModel by viewModels {
        val container = (application as U30App).container
        PanelViewModel.factory(container.repository, container.probeExporter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            U30Theme {
                val state by viewModel.state.collectAsState()
                PanelApp(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onBackground()
    }
}