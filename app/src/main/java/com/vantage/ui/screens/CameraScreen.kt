package com.vantage.ui.screens

import android.graphics.SurfaceTexture
import androidx.compose.animation.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.*
import java.io.File
import java.io.FileOutputStream
import androidx.compose.runtime.collectAsState

private val BottomControlsHeight = 172.dp

@Composable
fun CameraScreen(viewModel: CameraViewModel, uiState: CameraUiState) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val availableLenses by viewModel.availableLenses.collectAsState()
    val selectedLens by viewModel.selectedLens.collectAsState()
    val currentRatio by viewModel.currentRatio.collectAsState()

    val brightness by viewModel.brightness.collectAsState()
    val contrast by viewModel.contrast.collectAsState()
    val saturation by viewModel.saturation.collectAsState()
    val gamma by viewModel.gamma.collectAsState()
    val settingsValues by viewModel.settingsValues.collectAsState()
    val activeAdjustment by viewModel.activeAdjustment.collectAsState()
    val isFilterListVisible by viewModel.isFilterListVisible.collectAsState()
    val manualSettings by viewModel.manualSettings.collectAsState()

    var currentSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var streamSize by remember { mutableStateOf<android.util.Size?>(null) }

    var settingsOpen by remember { mutableStateOf(false) }
    val coachCaptureSignal by viewModel.coachCaptureSignal.collectAsState()

    LaunchedEffect(uiState.flashMode) {
        imageCapture.flashMode = when (uiState.flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    // One capture per signal tick — ViewModel fires the next tick only after inference completes,
    // keeping camera captures and NPU inference strictly sequential (no DSP contention).
    LaunchedEffect(coachCaptureSignal) {
        if (coachCaptureSignal == 0 || uiState.appMode != AppMode.COACH_ME) return@LaunchedEffect
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val file = File(context.cacheDir, "coach_frame.jpg")
                    FileOutputStream(file).use { it.write(bytes) }
                    image.close()
                    viewModel.onCoachingFrame(file.absolutePath)
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e("Vantage", "Coach capture failed", e)
                }
            }
        )
    }

    // Unified Camera Lifecycle Management
    LaunchedEffect(selectedLens, currentSurfaceTexture, currentRatio) {
        val lens = selectedLens
        val st = currentSurfaceTexture
        if (lens != null && st != null) {
            viewModel.cameraManager.openCamera(lens.logicalId, currentRatio, st) { size ->
                streamSize = size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val currentZoom = manualSettings.zoomRatio ?: 1.0f
                    // Smoothly update zoom based on pinch factor
                    // Min zoom 0.6x (Ultra-wide), Max zoom 10x
                    val nextZoom = (currentZoom * zoom).coerceIn(0.6f, 10.0f)
                    if (nextZoom != currentZoom) {
                        viewModel.onZoomChanged(nextZoom)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Full Screen Filtered Viewport
        AndroidView(
            factory = { ctx ->
                FilterViewport(ctx).apply {
                    onSurfaceReady = { st ->
                        currentSurfaceTexture = st
                    }
                }
            },
            update = { view ->
                view.setFilter(uiState.currentFilter)
                view.setAdjustments(brightness, contrast, saturation, gamma)
                streamSize?.let { view.setStreamSize(it) }
                selectedLens?.let { lens ->
                    val orientation = viewModel.cameraManager.getSensorOrientation(lens.logicalId)
                    val isFront = lens.facing == android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT
                    view.setSensorOrientation(orientation, isFront)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Bar
        var settingsExpanded by remember { mutableStateOf(false) }
        TopBarControls(
            onSettingsToggle = { settingsExpanded = !settingsExpanded },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
        )

        // Settings Dropdown
        ProSettingsDropdown(
            expanded = settingsExpanded,
            activeAdjustment = activeAdjustment,
            onAdjustmentSelected = {
                viewModel.onActiveAdjustmentChanged(it)
                settingsExpanded = false
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 70.dp, end = 16.dp)
        )

        // Chat bubble overlay
        // Flash toggle — top left
        IconButton(
            onClick = { viewModel.onFlashToggled() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Black.copy(alpha = 0.3f), CircleShape)
        ) {
            val icon = when (uiState.flashMode) {
                FlashMode.OFF -> Icons.Default.FlashOff
                FlashMode.ON -> Icons.Default.FlashOn
                FlashMode.AUTO -> Icons.Default.FlashAuto
            }
            Icon(imageVector = icon, contentDescription = "Flash Mode", tint = White)
        }

        // Settings button — top right
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            IconButton(
                onClick = { settingsOpen = !settingsOpen },
                modifier = Modifier.background(Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = White)
            }

            if (settingsOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 52.dp)
                        .wrapContentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ChatBubbleBg.copy(alpha = 0.92f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Speak coaching steps",
                            color = White,
                            fontSize = 14.sp
                        )
                        Switch(
                            checked = uiState.voiceCoachEnabled,
                            onCheckedChange = { viewModel.onVoiceCoachToggled() }
                        )
                    }
                }
            }
        }

        // Subject input + debug info — floats below the top buttons in Coach Me mode
        if (uiState.appMode == AppMode.COACH_ME) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 68.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubjectInputRow(
                    subject = uiState.coachingSubject,
                    onSubjectChanged = { viewModel.onCoachingSubjectChanged(it) }
                )
                if (uiState.lastCoachDebug.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Black.copy(alpha = 0.6f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = uiState.lastCoachDebug,
                            color = White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        CoachingOverlay(
            suggestion = uiState.pendingUserActions.firstOrNull()
                ?.takeIf { uiState.appMode == AppMode.COACH_ME },
            revision = uiState.coachRevision,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BottomControlsHeight)
        )

        ChatBubbleOverlay(
            messages = uiState.chatMessages.takeLast(3),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 220.dp)
                .align(Alignment.BottomStart)
                .padding(bottom = BottomControlsHeight + 160.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 0. Zoom Control (Above Mode Chips, Left Side)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                ZoomControl(
                    currentZoom = manualSettings.zoomRatio ?: 1.0f,
                    onZoomSelected = { viewModel.onZoomChanged(it) },
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Filter Selector Strip (Above Mode Chips)
            FilterSelectorStrip(
                visible = isFilterListVisible,
                currentFilter = uiState.currentFilter,
                onFilterSelected = { viewModel.onFilterSelected(it) }
            )

            // 2. Horizontal Adjustment Scale (Above Mode Chips)
            val activeDef = AdvancedSettingsRegistry.ALL_SETTINGS.find { it.type == activeAdjustment }
            if (activeDef != null && activeAdjustment != AdvancedSettingsRegistry.SettingType.NONE) {
                HorizontalAdjustmentScale(
                    type = activeAdjustment,
                    label = activeDef.label,
                    value = settingsValues[activeAdjustment] ?: activeDef.defaultValue,
                    range = activeDef.range,
                    onValueChange = { viewModel.onAdjustmentChanged(activeAdjustment, it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. Mode Toggle (Above Shutter)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                ModeToggle(
                    currentMode = uiState.appMode,
                    onToggle = { viewModel.onModeToggled() },
                    modifier = Modifier.align(Alignment.Center)
                )

                // Filter Toggle Button (Above Mode Chips, Right Side)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isFilterListVisible) AIAccentBlue.copy(alpha = 0.4f) else White.copy(alpha = 0.2f))
                        .clickable { viewModel.onFilterToggleTapped() }
                        .align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✦", color = White, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Bottom Controls (Shutter and Flip)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Invisible placeholder to keep shutter centered
                Box(modifier = Modifier.size(48.dp))

                ProShutterButton(
                    onClick = { viewModel.onManualShutter() }
                )

                // Camera Flip
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(White.copy(alpha = 0.2f))
                        .clickable {
                            val currentFacing = selectedLens?.facing
                            val nextFacing = if (currentFacing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK)
                                android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT else android.hardware.camera2.CameraMetadata.LENS_FACING_BACK

                            Log.d("Vantage", "Flip tapped. Current facing: $currentFacing, Target: $nextFacing")
                            val lens = availableLenses.find { it.facing == nextFacing }
                            if (lens != null) {
                                Log.d("Vantage", "Switching to lens: ${lens.label} (ID: ${lens.logicalId})")
                                viewModel.onLensSelected(lens)
                            } else {
                                Log.e("Vantage", "No lens found for facing $nextFacing")
            CaptureButton(
                readyToCapture = uiState.readyToCapture,
                onClick = {
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val file = File(context.cacheDir, "preview_frame.jpg")
                                FileOutputStream(file).use { it.write(bytes) }
                                image.close()
                                Log.d("Vantage", "Frame captured: ${file.absolutePath} (${bytes.size} bytes)")
                                viewModel.onCaptureButtonTapped(file.absolutePath)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                Log.e("Vantage", "Capture failed", exception)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "↺", color = White, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        MicButton(
            isListening = uiState.isListening,
            onToggleListening = { viewModel.onMicButtonToggled() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .padding(bottom = 80.dp)
        )
    }
}

@Composable
private fun ChatBubbleOverlay(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        messages.forEach { message ->
            AnimatedChatBubble(message = message)
        }
    }
}

@Composable
private fun AnimatedChatBubble(message: ChatMessage) {
    var visible by remember(message) { mutableStateOf(false) }

    LaunchedEffect(message) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(ChatBubbleBg.copy(alpha = 0.88f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                color = White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ModeToggle(currentMode: AppMode, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val isDoItForMe = currentMode == AppMode.DO_IT_FOR_ME

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(White.copy(alpha = 0.15f))
            .clickable { onToggle() }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeChip(text = "Do it for me", selected = isDoItForMe)
        Spacer(modifier = Modifier.width(4.dp))
        ModeChip(text = "Coach me", selected = !isDoItForMe)
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AIAccentBlue else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SubjectInputRow(
    subject: String,
    onSubjectChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = subject,
        onValueChange = onSubjectChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "What are you photographing?",
                color = White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = White,
            unfocusedTextColor = White,
            focusedContainerColor = Black.copy(alpha = 0.45f),
            unfocusedContainerColor = Black.copy(alpha = 0.35f),
            focusedBorderColor = White.copy(alpha = 0.5f),
            unfocusedBorderColor = White.copy(alpha = 0.25f),
            cursorColor = White
        ),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun CaptureButton(readyToCapture: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "captureReady")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val borderColor = if (readyToCapture) ReadyGreen.copy(alpha = pulseAlpha) else White
    val borderWidth = if (readyToCapture) 5.dp else 4.dp

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .clickable { onClick() }
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (readyToCapture) ReadyGreen.copy(alpha = 0.3f) else White)
    )
}
