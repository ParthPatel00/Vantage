package com.vantage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vantage.ui.screens.CameraScreen
import com.vantage.ui.screens.LoadingScreen
import com.vantage.ui.theme.VantageTheme
import com.vantage.viewmodel.CameraViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VantageTheme {
                val navController = rememberNavController()
                val viewModel: CameraViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                NavHost(navController = navController, startDestination = "loading") {
                    composable("loading") {
                        LoadingScreen(
                            progress = uiState.modelLoadProgress,
                            onLoaded = { navController.navigate("camera") { popUpTo("loading") { inclusive = true } } }
                        )
                    }
                    composable("camera") {
                        CameraScreen(viewModel = viewModel, uiState = uiState)
                    }
                }
            }
        }
    }
}
