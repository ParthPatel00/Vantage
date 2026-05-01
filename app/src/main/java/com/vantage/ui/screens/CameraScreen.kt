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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.vantage.ui.components.InspoCard
import com.vantage.ui.theme.AIAccentBlue
import com.vantage.ui.theme.Black
import com.vantage.ui.theme.ChatBubbleBg
import com.vantage.ui.theme.White
import com.vantage.viewmodel.CameraViewModel
import java.io.File
import java.io.FileOutputStream

private val BottomControlsHeight = 172.dp

@Composable
fun CameraScreen(viewModel: CameraViewModel, uiState: CameraUiState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    LaunchedEffect(uiState.flashMode) {
        imageCapture.flashMode = when (uiState.flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    // Listen for ViewModel-initiated snapshot requests (fired by the inspiration tool)
    // and capture a fresh preview frame to disk, reporting the path back via the VM.
    // This is what lets Gemma both craft a scene-specific Unsplash query AND coach the
    // user toward recreating a selected reference, without requiring the user to tap
    // the manual capture button first.
    LaunchedEffect(Unit) {
        viewModel.snapshotRequests.collect {
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val file = File(context.cacheDir, "inspo_frame.jpg")
                        FileOutputStream(file).use { out -> out.write(bytes) }
                        image.close()
                        Log.d("Vantage", "Inspo snapshot: ${file.absolutePath} (${bytes.size} bytes)")
                        viewModel.onInspoSnapshotCaptured(file.absolutePath)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Log.e("Vantage", "Inspo snapshot capture failed", exception)
                    }
                }
            )
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind / rebind the camera whenever the front-facing flag flips. Pulled out of the
    // AndroidView factory so the flip button can drive a real rebind instead of having
    // to recreate the PreviewView.
    LaunchedEffect(uiState.isFrontCamera) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val selector = if (uiState.isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                       else CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll()
        try {
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        } catch (e: Exception) {
            Log.e("Vantage", "Camera bind failed for front=${uiState.isFrontCamera}", e)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top-left controls: flash + flip-camera, side by side.
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { viewModel.onFlashToggled() },
                modifier = Modifier.background(Black.copy(alpha = 0.3f), CircleShape)
            ) {
                val icon = when (uiState.flashMode) {
                    FlashMode.OFF -> Icons.Default.FlashOff
                    FlashMode.ON -> Icons.Default.FlashOn
                    FlashMode.AUTO -> Icons.Default.FlashAuto
                }
                Icon(imageVector = icon, contentDescription = "Flash Mode", tint = White)
            }
            IconButton(
                onClick = { viewModel.onFlipCamera() },
                modifier = Modifier.background(Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = if (uiState.isFrontCamera) "Switch to back camera" else "Switch to front camera",
                    tint = White
                )
            }
        }

        // Chat bubble overlay — shows last 3 AI messages
        // Coaching bubble + edge arrows — only in Coach Me mode.
        // Bottom safe area keeps the bubble clear of the toggle/capture row.
        CoachingOverlay(
            suggestion = uiState.pendingUserActions.firstOrNull()
                ?.takeIf { uiState.appMode == AppMode.COACH_ME },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BottomControlsHeight)
        )

        // Chat bubble overlay — last 3 AI messages, sits above the coaching bubble.
        ChatBubbleOverlay(
            messages = uiState.chatMessages.takeLast(3),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(bottom = BottomControlsHeight + 160.dp)
        )

        // Bottom controls — mode toggle above the capture button.
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
            onLongPress = { viewModel.triggerPoseInspiration() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .padding(bottom = 80.dp)
        )

        // Floating top-right inspiration card. Only renders when inspoPhotos isn't empty.
        // Sits opposite the flash button in the top-left, with statusBarsPadding so it
        // never tucks under the system bar.
        InspoCard(
            photos = uiState.inspoPhotos,
            selected = uiState.selectedInspoPhoto,
            onSelect = { viewModel.onInspoPhotoSelected(it) },
            onDismiss = { viewModel.onInspoCardDismissed() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 12.dp)
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
private fun CaptureButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(4.dp, White, CircleShape)
            .clickable { onClick() }
            .padding(6.dp)
            .clip(CircleShape)
            .background(White)
    )
}
