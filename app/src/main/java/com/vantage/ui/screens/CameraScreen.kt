package com.vantage.ui.screens

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.vantage.camera.standard.AspectRatioManager
import com.vantage.filters.FilterViewport
import com.vantage.settings.AdvancedSettingsRegistry
import com.vantage.ui.pro.*
import com.vantage.models.AppMode
import com.vantage.models.CameraUiState
import com.vantage.models.ChatMessage
import com.vantage.models.FlashMode
import com.vantage.ui.components.MicButton
import com.vantage.ui.components.CoachingOverlay
import com.vantage.ui.theme.AIAccentBlue
import com.vantage.ui.theme.Black
import com.vantage.ui.theme.ChatBubbleBg
import com.vantage.ui.theme.ReadyGreen
import com.vantage.ui.theme.White
import com.vantage.viewmodel.CameraViewModel
import kotlinx.coroutines.delay

private val BottomControlsHeight = 172.dp

@Composable
fun CameraScreen(viewModel: CameraViewModel, uiState: CameraUiState) {
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

    // Auto-dismiss Quick Settings after 10 seconds
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) {
            delay(10000)
            settingsOpen = false
        }
    }

    val coachCaptureSignal by viewModel.coachCaptureSignal.collectAsState()

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

    // Capture logic integration for Coaching
    LaunchedEffect(coachCaptureSignal) {
        if (coachCaptureSignal == 0 || uiState.appMode != AppMode.COACH_ME) return@LaunchedEffect
        viewModel.cameraManager.takePicture { file ->
            viewModel.onCoachingFrame(file.absolutePath)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val currentZoom = manualSettings.zoomRatio ?: 1.0f
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

        // Auto-dismiss Pro Settings Dropdown after 10 seconds
        LaunchedEffect(settingsExpanded) {
            if (settingsExpanded) {
                delay(10000)
                settingsExpanded = false
            }
        }

        // Auto-dismiss active adjustment scale after 10 seconds
        LaunchedEffect(activeAdjustment) {
            if (activeAdjustment != AdvancedSettingsRegistry.SettingType.NONE) {
                delay(10000)
                viewModel.onActiveAdjustmentChanged(AdvancedSettingsRegistry.SettingType.NONE)
            }
        }

        // Auto-dismiss filter list after 10 seconds
        LaunchedEffect(isFilterListVisible) {
            if (isFilterListVisible) {
                delay(10000)
                viewModel.onFilterToggleTapped()
            }
        }

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

        // Quick Settings button — top left
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            IconButton(
                onClick = { settingsOpen = !settingsOpen },
                modifier = Modifier.background(Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Quick Settings", tint = White)
            }

            if (settingsOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
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

            // 4. Bottom Controls (Mic, Shutter, and Flip)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Button (Left of shutter)
                MicButton(
                    isListening = uiState.isListening,
                    onToggleListening = { viewModel.onMicButtonToggled() },
                    modifier = Modifier.size(48.dp)
                )

                ProShutterButton(
                    onClick = { viewModel.onManualShutter() }
                )

                // Camera Flip (Right of shutter)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(White.copy(alpha = 0.2f))
                        .clickable {
                            val currentFacing = selectedLens?.facing
                            val nextFacing = if (currentFacing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK)
                                android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT else android.hardware.camera2.CameraMetadata.LENS_FACING_BACK

                            val lens = availableLenses.find { it.facing == nextFacing }
                            if (lens != null) {
                                viewModel.onLensSelected(lens)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "↺", color = White, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
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
