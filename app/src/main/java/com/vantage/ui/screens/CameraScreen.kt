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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vantage.ai.SceneAnalysis
import com.vantage.camera.standard.AspectRatioManager
import com.vantage.models.CameraUiState
import com.vantage.models.FlashMode
import com.vantage.ui.components.InspoCard
import com.vantage.ui.pro.FilterSelectorStrip
import com.vantage.ui.pro.ProShutterButton
import com.vantage.ui.theme.Black
import com.vantage.ui.theme.White
import com.vantage.viewmodel.CameraViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraScreen(viewModel: CameraViewModel, uiState: CameraUiState, onGalleryTapped: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    var camera: Camera? by remember { mutableStateOf(null) }
    var filterStripVisible by remember { mutableStateOf(false) }
    var showAiCard by remember { mutableStateOf(false) }
    val currentZoomRef = remember { mutableFloatStateOf(1f) }
    // Hold the original bitmap in memory so enhanced = original + software post-processing
    var originalBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val aiCaptureSignal by viewModel.aiCaptureSignal.collectAsState()
    val photoSignal by viewModel.photoSignal.collectAsState()
    val pendingAnalysis by viewModel.pendingAnalysis.collectAsState()
    val saveOriginal by viewModel.saveOriginal.collectAsState()
    val captureTimestamp by viewModel.captureTimestamp.collectAsState()

    // Dedup guards: track last processed signal values to prevent re-firing
    var lastProcessedAiSignal by remember { mutableIntStateOf(0) }
    var lastProcessedPhotoSignal by remember { mutableIntStateOf(0) }

    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    viewModel.onVoiceResult(text)
                }
                override fun onError(error: Int) {
                    Log.e("Vantage", "Speech recognition error: $error")
                    viewModel.onVoiceCancelled()
                }
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

    LaunchedEffect(uiState.currentZoom) { currentZoomRef.floatValue = uiState.currentZoom }

    // Inspiration tool fires snapshot requests through the ViewModel; the screen captures
    // a fresh preview frame and reports the path back so Gemma can craft a scene-specific
    // Unsplash query.
    LaunchedEffect(Unit) {
        viewModel.snapshotRequests.collect {
            imageCapture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val file = File(context.cacheDir, "inspo_frame.jpg")
                            FileOutputStream(file).use { it.write(bytes) }
                            Log.d("Vantage", "Inspo frame: ${file.absolutePath} (${bytes.size} bytes)")
                            viewModel.onInspoSnapshotCaptured(file.absolutePath)
                        } finally {
                            image.close()
                        }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Log.e("Vantage", "Inspo snapshot capture failed", exception)
                    }
                }
            )
        }
    }

    LaunchedEffect(uiState.flashMode) {
        imageCapture.flashMode = when (uiState.flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    LaunchedEffect(uiState.currentZoom) {
        val cam = camera ?: run {
            Log.w("Vantage", "Zoom: camera is null")
            return@LaunchedEffect
        }
        val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 10f
        val minZoom = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
        Log.d("Vantage", "Zoom applying: requested=${uiState.currentZoom} min=$minZoom max=$maxZoom")
        if (maxZoom <= minZoom) return@LaunchedEffect
        val clamped = uiState.currentZoom.coerceIn(minZoom, maxZoom)
        val linear = ((clamped - minZoom) / (maxZoom - minZoom)).coerceIn(0f, 1f)
        cam.cameraControl.setLinearZoom(linear)
    }

    LaunchedEffect(pendingAnalysis) {
        val cam = camera ?: return@LaunchedEffect
        val analysis = pendingAnalysis ?: return@LaunchedEffect
        if (uiState.isFrontCamera) {
            applyCamera2SettingsFrontCamera(cam, analysis)
        } else {
            applyCamera2Settings(cam, analysis)
        }
    }

    // Capture frame for AI analysis. On the FIRST capture, also save as "Original" to gallery
    // and keep the bitmap in memory for later post-processing.
    LaunchedEffect(aiCaptureSignal) {
        if (aiCaptureSignal == 0 || aiCaptureSignal == lastProcessedAiSignal) return@LaunchedEffect
        lastProcessedAiSignal = aiCaptureSignal
        val shouldSaveOriginal = saveOriginal
        val ts = captureTimestamp
        Log.d("Vantage", "Capturing frame for AI (signal=$aiCaptureSignal, saveOriginal=$shouldSaveOriginal)")
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        val rotation = image.imageInfo.rotationDegrees

                        if (shouldSaveOriginal) {
                            var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (rotation != 0) {
                                val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                                bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            }
                            originalBitmap = bitmap
                            saveBitmapToMediaStore(context, bitmap, "Vantage_$ts")
                            viewModel.onOriginalSaved()
                            Log.d("Vantage", "Saved original: Vantage_$ts")
                        }

                        val file = File(context.cacheDir, "ai_frame.jpg")
                        FileOutputStream(file).use { it.write(bytes) }
                        image.close()
                        viewModel.onPreviewFrameCaptured(file.absolutePath)
                    } catch (e: Exception) {
                        image.close()
                        Log.e("Vantage", "AI frame capture failed", e)
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e("Vantage", "AI frame capture failed", e)
                }
            }
        )
    }

    // After AI analysis is complete, apply software post-processing to the ORIGINAL bitmap
    // and save as "Enhanced". No second capture needed.
    LaunchedEffect(photoSignal) {
        if (photoSignal == 0 || photoSignal == lastProcessedPhotoSignal) return@LaunchedEffect
        lastProcessedPhotoSignal = photoSignal
        val analysisSnapshot = pendingAnalysis
        val ts = captureTimestamp
        val srcBitmap = originalBitmap
        Log.d("Vantage", "Creating enhanced from original (signal=$photoSignal, ts=$ts)")
        if (srcBitmap != null && analysisSnapshot != null) {
            try {
                val enhancedBitmap = com.vantage.ai.ImageProcessor.process(srcBitmap, analysisSnapshot, context)
                val enhancedUri = saveBitmapToMediaStore(context, enhancedBitmap, "Vantage_${ts}_enhanced")
                if (enhancedUri != null) {
                    viewModel.onPhotoCaptured(enhancedUri)
                }
                Log.d("Vantage", "Saved enhanced: Vantage_${ts}_enhanced")
            } catch (e: Exception) {
                Log.e("Vantage", "Enhanced processing failed", e)
            }
        } else {
            Log.w("Vantage", "No original bitmap or analysis, skipping enhanced")
        }
        originalBitmap = null
        camera?.let { resetCamera2ToAuto(it) }
    }

    // Show AI card during analysis and for a few seconds after
    LaunchedEffect(uiState.isAiActive, uiState.aiReasoning) {
        if (uiState.isAiActive) {
            showAiCard = true
        } else if (uiState.aiReasoning.isNotBlank()) {
            showAiCard = true
            delay(5000)
            showAiCard = false
        }
    }

    // ========== LAYOUT ==========
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        // === VIEWFINDER ===
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
            val cameraProviderForCheck = remember { cameraProviderFuture.get() }
            // Fall back to whichever lens the device actually has if the requested one
            // isn't available (e.g. AVD with only one webcam wired to the back lens).
            val cameraSelector = run {
                val preferred = if (uiState.isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                    else CameraSelector.DEFAULT_BACK_CAMERA
                if (cameraProviderForCheck.hasCamera(preferred)) preferred
                else if (cameraProviderForCheck.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                    CameraSelector.DEFAULT_BACK_CAMERA
                else CameraSelector.DEFAULT_FRONT_CAMERA
            }

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


            // Grid lines only - clean, no bounding boxes
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val lineColor = Color.White.copy(alpha = 0.15f)
                val sw = 0.5.dp.toPx()
                drawLine(lineColor, Offset(w / 3, 0f), Offset(w / 3, h), sw)
                drawLine(lineColor, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), sw)
                drawLine(lineColor, Offset(0f, h / 3), Offset(w, h / 3), sw)
                drawLine(lineColor, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), sw)
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

            IconButton(
                onClick = { filterStripVisible = !filterStripVisible },
                modifier = Modifier
                    .size(40.dp)
                    .background(Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Tune, contentDescription = "Filters", tint = White)
            }
        }


        // === VOICE PROMPT PILL (top right, below settings) ===
        if (uiState.voicePrompt.isNotBlank() || uiState.isListening) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 68.dp, end = 16.dp, start = 80.dp)
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

        val infiniteTransition = rememberInfiniteTransition(label = "aiBadge")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "pulse"
        )

        // === AI THINKING CARD (single clean card, no clutter) ===
        var dotCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(uiState.isAiActive) {
            if (uiState.isAiActive) {
                dotCount = 0
                while (true) { delay(500); dotCount = (dotCount + 1) % 4 }
            }
        }

        val statusLabel = when {
            uiState.isAiActive -> when (uiState.analysisIteration) {
                0 -> "Analyzing scene"
                1 -> "Optimizing"
                else -> "Fine-tuning"
            }
            else -> null
        }

        AnimatedVisibility(
            visible = showAiCard,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
            exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 220.dp, start = 20.dp, end = 20.dp)
                .zIndex(10f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE6181818))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status row with pulsing indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.isAiActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    Color(0xFF4CAF50).copy(alpha = pulseAlpha),
                                    CircleShape
                                )
                        )
                    }
                    Text(
                        text = if (uiState.isAiActive) {
                            "${statusLabel}${".".repeat(dotCount + 1)}"
                        } else {
                            "Analysis complete"
                        },
                        color = if (uiState.isAiActive) Color(0xFF81C784) else White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (uiState.isAiActive) {
                    // Scene description
                    if (uiState.sceneDescription.isNotBlank()) {
                        Text(
                            text = uiState.sceneDescription,
                            color = White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 3
                        )
                    }

                    // Composition tip
                    if (uiState.compositionTip.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(6.dp)
                                    .background(
                                        if (uiState.compositionOk) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                        CircleShape
                                    )
                            )
                            Text(
                                text = uiState.compositionTip,
                                color = if (uiState.compositionOk) Color(0xFFA5D6A7) else Color(0xFFFFCC80),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 2
                            )
                        }
                    }
                }

                // Photography tip
                if (uiState.photographyTip.isNotBlank()) {
                    Text(
                        text = "Tip: ${uiState.photographyTip}",
                        color = Color(0xFF90CAF9),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2
                    )
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

        // === FILTER STRIP ===
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
                    .background(
                        if (uiState.isListening) Color(0xFFFF4444) else White.copy(alpha = 0.1f),
                        CircleShape
                    )
                    .combinedClickable(
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
                        onLongClick = { viewModel.triggerPoseInspiration() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice (long-press for pose inspo)",
                    tint = White,
                    modifier = Modifier.size(28.dp)
                )
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

        // Floating top-right inspiration card. Sits below the AI badge + voice pill so
        // the existing top-right column isn't disturbed. Hidden until the inspiration
        // tool returns photos.
        InspoCard(
            photos = uiState.inspoPhotos,
            selected = uiState.selectedInspoPhoto,
            onSelect = { viewModel.onInspoPhotoSelected(it) },
            onDismiss = { viewModel.onInspoCardDismissed() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 148.dp, end = 12.dp)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }
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

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
private fun applyCamera2SettingsFrontCamera(camera: Camera, analysis: SceneAnalysis) {
    try {
        val cam2 = Camera2CameraControl.from(camera.cameraControl)
        val opts = CaptureRequestOptions.Builder().apply {
            setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, analysis.wbMode)
            setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, analysis.noiseReductionMode)
            setCaptureRequestOption(CaptureRequest.EDGE_MODE, analysis.sharpnessMode)
        }.build()
        cam2.setCaptureRequestOptions(opts)
        Log.d("Vantage", "Front camera: applied WB/NR/Edge only (auto exposure kept)")
    } catch (e: Exception) {
        Log.e("Vantage", "Failed to apply front camera settings", e)
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
