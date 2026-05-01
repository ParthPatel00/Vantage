package com.vantage.ui.screens

import android.util.Log
import android.view.ViewGroup
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.io.File
import java.io.FileOutputStream
import androidx.compose.runtime.collectAsState

private val BottomControlsHeight = 172.dp

@Composable
fun CameraScreen(viewModel: CameraViewModel, uiState: CameraUiState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER

                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = surfaceProvider
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

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
            ModeToggle(
                currentMode = uiState.appMode,
                onToggle = { viewModel.onModeToggled() }
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                        }
                    )
                }
            )

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

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderFuture.get().unbindAll()
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
private fun ModeToggle(currentMode: AppMode, onToggle: () -> Unit) {
    val isDoItForMe = currentMode == AppMode.DO_IT_FOR_ME

    Row(
        modifier = Modifier
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
            .background(if (selected) AIAccentBlue else Black.copy(alpha = 0f))
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
