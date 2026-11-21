package com.nabungah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nabungah.ui.CanvasBg
import com.nabungah.ui.Dashboard
import com.nabungah.ui.MainViewModel
import com.nabungah.ui.NabungTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // An app left open overnight keeps yesterday's month until this check runs.
        vm.checkMonthRollover()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Touch the ViewModel before setContent so the first database read overlaps
        // Compose initialization instead of starting after the first frame.
        val viewModel = vm
        setContent {
            NabungTheme {
                LaunchedEffect(viewModel.loaded) {
                    // Report the moment the dashboard is actually interactive, not just
                    // the first (empty) frame.
                    if (viewModel.loaded) reportFullyDrawn()
                }
                Surface(modifier = Modifier.fillMaxSize(), color = CanvasBg) {
                    Dashboard(
                        vm = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            }
        }
    }
}
