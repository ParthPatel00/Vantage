package com.vantage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vantage.models.EnhancementInfo
import com.vantage.models.PhotoPair
import com.vantage.ui.screens.CameraScreen
import com.vantage.ui.screens.GalleryScreen
import com.vantage.ui.screens.PhotoDetailScreen
import com.vantage.ui.theme.VantageTheme
import com.vantage.viewmodel.CameraViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionsLauncher.launch(requiredPermissions)
            return
        }

        setContent {
            VantageTheme {
                val viewModel: CameraViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val enhancementMetadata by viewModel.enhancementMetadata.collectAsState()
                val scope = rememberCoroutineScope()

                val pagerState = rememberPagerState(initialPage = 0) { 2 }
                var selectedPhotos by remember { mutableStateOf<List<PhotoPair>?>(null) }
                var selectedIndex by remember { mutableStateOf(0) }

                Crossfade(
                    targetState = selectedPhotos,
                    animationSpec = tween(250),
                    label = "detail"
                ) { photos ->
                    if (photos != null) {
                        PhotoDetailScreen(
                            photos = photos,
                            initialIndex = selectedIndex,
                            enhancementMetadata = enhancementMetadata,
                            onBack = { selectedPhotos = null }
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 1
                        ) { page ->
                            when (page) {
                                0 -> CameraScreen(
                                    viewModel = viewModel,
                                    uiState = uiState,
                                    onGalleryTapped = {
                                        scope.launch { pagerState.animateScrollToPage(1) }
                                    }
                                )
                                1 -> GalleryScreen(
                                    onBack = {
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    },
                                    onPhotoClick = { photos, index ->
                                        selectedPhotos = photos
                                        selectedIndex = index
                                    },
                                    refreshTrigger = uiState.lastCapturedUri
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
