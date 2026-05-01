package com.vantage.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.vantage.ai.SceneAnalysis
import com.vantage.camera.standard.AspectRatioManager
import com.vantage.models.CameraUiState
import com.vantage.models.FlashMode
import com.vantage.ui.pro.FilterSelectorStrip
import com.vantage.ui.pro.ProShutterButton
import com.vantage.ui.theme.Black
import com.vantage.ui.theme.White
import com.vantage.viewmodel.CameraViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

@Composable
fun CameraScreen(viewModel: CameraViewModel, uiState: CameraUiState, onGalleryTapped: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    var camera: Camera? by remember { mutableStateOf(null) }
    var filterStripVisible by remember { mutableStateOf(false) }
    var showReasoning by remember { mutableStateOf(false) }
    val currentZoomRef = remember { mutableFloatStateOf(1f) }

    val aiCaptureSignal by viewModel.aiCaptureSignal.collectAsState()
    val photoSignal by viewModel.photoSignal.collectAsState()
    val pendingAnalysis by viewModel.pendingAnalysis.collectAsState()

    // Speech recognizer
    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    viewModel.onVoiceResult(text)
                }
                override fun onError(error: Int) { viewModel.onVoiceCancelled() }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    DisposableEffect(Unit) { onDispose { speechRecognizer.destroy() } }

    // Keep zoom ref in sync for ScaleGestureDetector callback
    LaunchedEffect(uiState.currentZoom) { currentZoomRef.floatValue = uiState.currentZoom }

    // Apply flash mode
    LaunchedEffect(uiState.flashMode) {
        imageCapture.flashMode = when (uiState.flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    // Apply zoom via CameraX
    LaunchedEffect(uiState.currentZoom) {
        camera?.cameraControl?.setZoomRatio(uiState.currentZoom)
    }

    // Apply Camera2 parameters from AI analysis
    LaunchedEffect(pendingAnalysis) {
        val cam = camera ?: return@LaunchedEffect
        val analysis = pendingAnalysis ?: return@LaunchedEffect
        applyCamera2Settings(cam, analysis)
    }

    // Capture preview frame for AI analysis
    LaunchedEffect(aiCaptureSignal) {
        if (aiCaptureSignal == 0) return@LaunchedEffect
        Log.d("Vantage", "Capturing preview frame for AI (signal=$aiCaptureSignal)")
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val file = File(context.cacheDir, "ai_frame.jpg")
                    FileOutputStream(file).use { it.write(bytes) }
                    image.close()
                    viewModel.onPreviewFrameCaptured(file.absolutePath)
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e("Vantage", "AI frame capture failed", e)
                }
            }
        )
    }

    // Take final photo: save both original and enhanced
    LaunchedEffect(photoSignal) {
        if (photoSignal == 0) return@LaunchedEffect
        Log.d("Vantage", "Taking final photo (signal=$photoSignal)")
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        val rotation = image.imageInfo.rotationDegrees
                        image.close()

                        var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (rotation != 0) {
                            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                            bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        }

                        val ts = System.currentTimeMillis()
                        val originalUri = saveBitmapToMediaStore(context, bitmap, "Vantage_$ts")

                        val analysis = pendingAnalysis
                        val enhancedBitmap = if (analysis != null) {
                            com.vantage.ai.ImageProcessor.process(bitmap, analysis)
                        } else bitmap
                        val enhancedUri = saveBitmapToMediaStore(context, enhancedBitmap, "Vantage_${ts}_enhanced")

                        if (enhancedUri != null) {
                            viewModel.onPhotoCaptured(enhancedUri)
                        } else if (originalUri != null) {
                            viewModel.onPhotoCaptured(originalUri)
                        }
                        Log.d("Vantage", "Saved original=$originalUri enhanced=$enhancedUri")
                    } catch (e: Exception) {
                        Log.e("Vantage", "Photo processing failed", e)
                    }
                    camera?.let { resetCamera2ToAuto(it) }
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e("Vantage", "Photo capture failed", e)
                    camera?.let { resetCamera2ToAuto(it) }
                }
            }
        )
    }

    // Auto-hide reasoning after 4s
    LaunchedEffect(uiState.aiReasoning) {
        if (uiState.aiReasoning.isNotBlank()) {
            showReasoning = true
            delay(4000)
            showReasoning = false
        }
    }

    // ========== LAYOUT ==========
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        // === VIEWFINDER (aspect-ratio clipped, Samsung style) ===
        val viewfinderRatio = when (uiState.currentRatio) {
            AspectRatioManager.AspectRatio.RATIO_4_3 -> 3f / 4f
            AspectRatioManager.AspectRatio.RATIO_16_9 -> 9f / 16f
            AspectRatioManager.AspectRatio.RATIO_1_1 -> 1f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(viewfinderRatio)
                .align(Alignment.Center)
                .clip(RectangleShape)
        ) {
            val cameraSelector = if (uiState.isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA

            key(uiState.isFrontCamera) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER

                            val scaleDetector = ScaleGestureDetector(ctx,
                                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                                        val newZoom = (currentZoomRef.floatValue * detector.scaleFactor)
                                            .coerceIn(0.6f, 3f)
                                        viewModel.onZoomSelected(newZoom)
                                        return true
                                    }
                                }
                            )
                            setOnTouchListener { _, event ->
                                scaleDetector.onTouchEvent(event)
                                true
                            }

                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = surfaceProvider
                            }
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner, cameraSelector, preview, imageCapture
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Filter color overlay
            val filterOverlay = filterOverlayColor(uiState.currentFilter)
            if (filterOverlay != Color.Transparent) {
                Box(modifier = Modifier.fillMaxSize().background(filterOverlay))
            }

            // Grid lines + composition overlays
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val lineColor = Color.White.copy(alpha = 0.15f)
                val sw = 0.5.dp.toPx()
                drawLine(lineColor, Offset(w / 3, 0f), Offset(w / 3, h), sw)
                drawLine(lineColor, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), sw)
                drawLine(lineColor, Offset(0f, h / 3), Offset(w, h / 3), sw)
                drawLine(lineColor, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), sw)

                if (uiState.isAiActive && uiState.subjectBox.size == 4) {
                    val sb = uiState.subjectBox
                    val sLeft = sb[1] / 1000f * w
                    val sTop = sb[0] / 1000f * h
                    val sRight = sb[3] / 1000f * w
                    val sBottom = sb[2] / 1000f * h
                    drawRect(
                        color = Color(0xFF4CAF50),
                        topLeft = Offset(sLeft, sTop),
                        size = androidx.compose.ui.geometry.Size(sRight - sLeft, sBottom - sTop),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }

                if (uiState.isAiActive && uiState.suggestedBox.size == 4 && !uiState.compositionOk) {
                    val sg = uiState.suggestedBox
                    val gLeft = sg[1] / 1000f * w
                    val gTop = sg[0] / 1000f * h
                    val gRight = sg[3] / 1000f * w
                    val gBottom = sg[2] / 1000f * h
                    val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(10.dp.toPx(), 6.dp.toPx()), 0f
                    )
                    drawRect(
                        color = Color(0xFFFFD700),
                        topLeft = Offset(gLeft, gTop),
                        size = androidx.compose.ui.geometry.Size(gRight - gLeft, gBottom - gTop),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(), pathEffect = dashEffect
                        )
                    )
                }
            }
        }

        // === TOP BAR ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.onFlashToggled() },
                modifier = Modifier
                    .size(40.dp)
                    .background(Black.copy(alpha = 0.4f), CircleShape)
            ) {
                val icon = when (uiState.flashMode) {
                    FlashMode.OFF -> Icons.Default.FlashOff
                    FlashMode.ON -> Icons.Default.FlashOn
                    FlashMode.AUTO -> Icons.Default.FlashAuto
                }
                Icon(imageVector = icon, contentDescription = "Flash", tint = White)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Black.copy(alpha = 0.4f))
                    .clickable { viewModel.onRatioToggled() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                val ratioText = when (uiState.currentRatio) {
                    AspectRatioManager.AspectRatio.RATIO_4_3 -> "4:3"
                    AspectRatioManager.AspectRatio.RATIO_16_9 -> "16:9"
                    AspectRatioManager.AspectRatio.RATIO_1_1 -> "1:1"
                }
                Text(text = ratioText, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Voice button
            IconButton(
                onClick = {
                    if (uiState.isListening) {
                        speechRecognizer.stopListening()
                        viewModel.onVoiceCancelled()
                    } else {
                        viewModel.onListeningStarted()
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }
                        speechRecognizer.startListening(intent)
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (uiState.isListening) Color(0xFFFF4444).copy(alpha = 0.8f)
                        else Black.copy(alpha = 0.4f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (uiState.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice",
                    tint = White
                )
            }

            IconButton(
                onClick = { filterStripVisible = !filterStripVisible },
                modifier = Modifier
                    .size(40.dp)
                    .background(Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Tune, contentDescription = "Filters", tint = White)
            }
        }

        // === AI BADGE ===
        val infiniteTransition = rememberInfiniteTransition(label = "aiBadge")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.5f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "pulse"
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 68.dp, end = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Black.copy(alpha = if (uiState.isAiActive) 0.7f * pulseAlpha else 0.5f))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = "AI  ${uiState.currentFilter.displayName}",
                color = if (uiState.isAiActive) Color(0xFFFFE57F) else White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // === VOICE PROMPT DISPLAY (below AI badge) ===
        if (uiState.voicePrompt.isNotBlank() || uiState.isListening) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 100.dp, end = 16.dp, start = 80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (uiState.isListening) Color(0xFFFF4444).copy(alpha = 0.7f)
                        else Black.copy(alpha = 0.6f)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (uiState.isListening) "Listening..."
                           else uiState.voicePrompt,
                    color = White,
                    fontSize = 11.sp,
                    maxLines = 2,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (uiState.voicePrompt.isNotBlank() && !uiState.isListening) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { viewModel.onVoicePromptCleared() }
                    )
                }
            }
        }

        // === AI STATUS + COMPOSITION COACHING OVERLAY ===
        var dotCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(uiState.isAiActive) {
            if (uiState.isAiActive) {
                dotCount = 0
                while (true) { delay(500); dotCount = (dotCount + 1) % 4 }
            }
        }
        val statusText = when {
            uiState.isAiActive -> {
                val dots = ".".repeat(dotCount + 1)
                when (uiState.analysisIteration) {
                    0 -> "Analyzing scene$dots"
                    1 -> "Calibrating$dots"
                    else -> "Fine-tuning$dots"
                }
            }
            showReasoning && uiState.aiReasoning.isNotBlank() -> uiState.aiReasoning
            else -> null
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = statusText != null,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(500))
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Black.copy(alpha = 0.7f))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = statusText ?: "",
                        color = White, fontSize = 14.sp,
                        textAlign = TextAlign.Center, lineHeight = 20.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.isAiActive && uiState.compositionTip.isNotBlank(),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(500))
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (uiState.compositionOk) Color(0xCC1B5E20) else Color(0xCCE65100)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = uiState.compositionTip,
                        color = White, fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.isAiActive && uiState.sceneDescription.isNotBlank(),
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = uiState.sceneDescription,
                        color = White.copy(alpha = 0.8f), fontSize = 12.sp,
                        textAlign = TextAlign.Center, lineHeight = 16.sp,
                        maxLines = 3
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.photographyTip.isNotBlank() && (uiState.isAiActive || showReasoning),
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(500)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC1A237E))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Tip: ${uiState.photographyTip}",
                    color = Color(0xFFBBDEFB), fontSize = 12.sp,
                    textAlign = TextAlign.Center, lineHeight = 16.sp,
                    maxLines = 2
                )
            }
        }

        // === SETTINGS GRID (compact 3-row, 4-per-row) ===
        val analysis = pendingAnalysis
        if (analysis != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 230.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    SettingChip("ISO", analysis.iso.toString(), Modifier.weight(1f))
                    SettingChip("SS", "1/${analysis.shutter}", Modifier.weight(1f))
                    SettingChip("WB", wbLabel(analysis.wbMode), Modifier.weight(1f))
                    SettingChip("Focus", String.format("%.1f", analysis.focusDistance), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    SettingChip("Zoom", "${String.format("%.1f", analysis.zoom)}x", Modifier.weight(1f))
                    SettingChip("NR", nrLabel(analysis.noiseReductionMode), Modifier.weight(1f))
                    SettingChip("Sharp", sharpLabel(analysis.sharpnessMode), Modifier.weight(1f))
                    SettingChip("Flash", analysis.flash.replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    SettingChip("EV", String.format("%+.1f", analysis.brightness), Modifier.weight(1f))
                    SettingChip("Cont", String.format("%.1f", analysis.contrast), Modifier.weight(1f))
                    SettingChip("Sat", String.format("%.1f", analysis.saturation), Modifier.weight(1f))
                    SettingChip("Filter", analysis.filter.displayName, Modifier.weight(1f))
                }
            }
        }

        // === ZOOM PILLS ===
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Black.copy(alpha = 0.4f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(0.6f, 1f, 2f, 3f).forEach { zoom ->
                val selected = kotlin.math.abs(uiState.currentZoom - zoom) < 0.15f
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) White.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { viewModel.onZoomSelected(zoom) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (zoom < 1f) "0.6x" else "${zoom.toInt()}x",
                        color = if (selected) Color(0xFFFFE57F) else White,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // === FILTER STRIP (above zoom, high z-index) ===
        AnimatedVisibility(
            visible = filterStripVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 240.dp)
                .zIndex(50f)
        ) {
            FilterSelectorStrip(
                visible = true,
                currentFilter = uiState.currentFilter,
                onFilterSelected = { viewModel.onFilterSelected(it) }
            )
        }

        // === BOTTOM BAR ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(White.copy(alpha = 0.1f))
                    .clickable { onGalleryTapped() },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.lastCapturedUri != null) {
                    AsyncImage(
                        model = uiState.lastCapturedUri,
                        contentDescription = "Last photo",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProShutterButton(onClick = { viewModel.onAiShutterTapped() })
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "AI", color = White.copy(alpha = 0.7f),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                )
            }

            IconButton(
                onClick = { viewModel.onCameraFlipped() },
                modifier = Modifier.size(52.dp).background(Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Camera", tint = White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }
}

// === Setting chip (compact, for grid layout) ===
@Composable
private fun SettingChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Black.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = value, color = Color(0xFFFFE57F),
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 1
        )
        Text(
            text = label, color = White.copy(alpha = 0.45f),
            fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 1
        )
    }
}

private fun wbLabel(mode: Int): String = when (mode) {
    CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> "Tungsten"
    CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> "Fluor"
    CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> "Day"
    CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "Cloudy"
    CameraMetadata.CONTROL_AWB_MODE_TWILIGHT -> "Twilight"
    CameraMetadata.CONTROL_AWB_MODE_SHADE -> "Shade"
    else -> "Auto"
}

private fun nrLabel(mode: Int): String = when (mode) {
    CameraMetadata.NOISE_REDUCTION_MODE_OFF -> "Off"
    CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY -> "HQ"
    CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL -> "Min"
    else -> "Fast"
}

private fun sharpLabel(mode: Int): String = when (mode) {
    CameraMetadata.EDGE_MODE_OFF -> "Off"
    CameraMetadata.EDGE_MODE_HIGH_QUALITY -> "HQ"
    else -> "Fast"
}

private fun filterOverlayColor(filter: com.vantage.models.FilterType): Color = when (filter) {
    com.vantage.models.FilterType.WARM -> Color(0x30FF8800)
    com.vantage.models.FilterType.COOL -> Color(0x300066CC)
    com.vantage.models.FilterType.NOIR -> Color(0x50000000)
    com.vantage.models.FilterType.VIVID -> Color(0x20FF2200)
    com.vantage.models.FilterType.DRAMATIC -> Color(0x38101030)
    com.vantage.models.FilterType.CINEMATIC -> Color(0x30003040)
    com.vantage.models.FilterType.VINTAGE -> Color(0x30806040)
    com.vantage.models.FilterType.MUTED -> Color(0x28808080)
    com.vantage.models.FilterType.FADE -> Color(0x24AAAAAA)
    com.vantage.models.FilterType.MONO -> Color(0x50000000)
    com.vantage.models.FilterType.SILVERTONE -> Color(0x28A0A0B0)
    else -> Color.Transparent
}

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
private fun applyCamera2Settings(camera: Camera, analysis: SceneAnalysis) {
    try {
        val cam2 = Camera2CameraControl.from(camera.cameraControl)
        val opts = CaptureRequestOptions.Builder().apply {
            setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, analysis.iso)
            setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / analysis.shutter.toLong())
            setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, analysis.wbMode)
            setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, analysis.focusDistance)
            setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, analysis.noiseReductionMode)
            setCaptureRequestOption(CaptureRequest.EDGE_MODE, analysis.sharpnessMode)
        }.build()
        cam2.setCaptureRequestOptions(opts)
        Log.d("Vantage", "Camera2 settings applied: ISO=${analysis.iso} shutter=1/${analysis.shutter}")
    } catch (e: Exception) {
        Log.e("Vantage", "Failed to apply Camera2 settings", e)
    }
}

private fun saveBitmapToMediaStore(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    displayName: String
): android.net.Uri? {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Vantage")
    }
    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
    ) ?: return null
    context.contentResolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
    }
    return uri
}

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
private fun resetCamera2ToAuto(camera: Camera) {
    try {
        val cam2 = Camera2CameraControl.from(camera.cameraControl)
        val opts = CaptureRequestOptions.Builder().apply {
            setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
            setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
            setCaptureRequestOption(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_FAST)
        }.build()
        cam2.setCaptureRequestOptions(opts)
        Log.d("Vantage", "Camera2 reset to auto AE/AWB/AF")
    } catch (e: Exception) {
        Log.e("Vantage", "Failed to reset Camera2 settings", e)
    }
}
