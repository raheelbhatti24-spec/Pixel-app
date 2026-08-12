package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.ui.theme.CameraControlBg
import com.example.ui.theme.GlassBg
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.PixelBlue
import com.example.ui.theme.PixelYellow
import com.example.ui.theme.PureBlack
import com.example.ui.theme.ShutterRing
import com.example.ui.theme.ShutterWhite
import com.example.ui.theme.ToastBg
import com.example.ui.theme.ToastBorder
import com.example.ui.viewmodel.CameraViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraCaptureScreen(
    viewModel: CameraViewModel,
    onPhotoCaptured: (Long) -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        CameraContent(
            viewModel = viewModel,
            onPhotoCaptured = onPhotoCaptured,
            onOpenGallery = onOpenGallery,
            modifier = modifier
        )
    } else {
        CameraPermissionRequest(
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
            modifier = modifier
        )
    }
}

@Composable
fun CameraContent(
    viewModel: CameraViewModel,
    onPhotoCaptured: (Long) -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val flashMode by viewModel.flashMode.collectAsState()
    val lensFacing by viewModel.lensFacing.collectAsState()
    val processingStrength by viewModel.processingStrength.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val latestPhoto by viewModel.latestPhoto.collectAsState()

    var selectedMode by remember { mutableStateOf("Photo") }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    val isApiKeyMissing = remember {
        BuildConfig.GEMINI_API_KEY.isBlank() || BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY"
    }

    // Pulse animation for HUD active indicator
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // Viewfinder Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack)
        ) {
            // CameraX Preview View
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val executor = ContextCompat.getMainExecutor(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(flashMode)
                            .build()

                        imageCaptureUseCase = imageCapture

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )

                            // Tap to Focus handling
                            val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapUp(e: MotionEvent): Boolean {
                                    val factory = previewView.meteringPointFactory
                                    val point = factory.createPoint(e.x, e.y)
                                    val action = FocusMeteringAction.Builder(point).build()
                                    camera.cameraControl.startFocusAndMetering(action)
                                    focusPoint = Offset(e.x, e.y)
                                    return true
                                }
                            })

                            previewView.setOnTouchListener { view, event ->
                                gestureDetector.onTouchEvent(event)
                                view.performClick()
                                true
                            }

                        } catch (e: Exception) {
                            Log.e("CameraCaptureScreen", "Use case binding failed", e)
                        }
                    }, executor)

                    previewView
                },
                update = { previewView ->
                    imageCaptureUseCase?.flashMode = flashMode
                },
                modifier = Modifier.fillMaxSize()
            )

            // Viewfinder Corner Brackets Target (Minimalist Focus Ring Overlay)
            focusPoint?.let { point ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sizePx = 64.dp.toPx()
                    val bracketLen = 14.dp.toPx()
                    val stroke = 2.dp.toPx()
                    val left = point.x - sizePx / 2
                    val top = point.y - sizePx / 2
                    val right = point.x + sizePx / 2
                    val bottom = point.y + sizePx / 2

                    // Corner brackets
                    // Top-Left
                    drawLine(PixelYellow, Offset(left, top), Offset(left + bracketLen, top), strokeWidth = stroke)
                    drawLine(PixelYellow, Offset(left, top), Offset(left, top + bracketLen), strokeWidth = stroke)
                    // Top-Right
                    drawLine(PixelYellow, Offset(right, top), Offset(right - bracketLen, top), strokeWidth = stroke)
                    drawLine(PixelYellow, Offset(right, top), Offset(right, top + bracketLen), strokeWidth = stroke)
                    // Bottom-Left
                    drawLine(PixelYellow, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeWidth = stroke)
                    drawLine(PixelYellow, Offset(left, bottom), Offset(left, bottom - bracketLen), strokeWidth = stroke)
                    // Bottom-Right
                    drawLine(PixelYellow, Offset(right, bottom), Offset(right - bracketLen, bottom), strokeWidth = stroke)
                    drawLine(PixelYellow, Offset(right, bottom), Offset(right, bottom - bracketLen), strokeWidth = stroke)
                }
                LaunchedEffect(point) {
                    kotlinx.coroutines.delay(1200)
                    focusPoint = null
                }
            }

            // Top Navigation / Settings Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                    .padding(top = 44.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash Toggle Button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(GlassBg)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { viewModel.toggleFlash() }
                            .testTag("flash_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                                else -> Icons.Default.FlashOff
                            },
                            contentDescription = "Flash Toggle",
                            tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) PixelYellow else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Strength Selector Quick Pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GlassBg)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable {
                                val nextStrength = when (processingStrength) {
                                    "subtle" -> "standard"
                                    "standard" -> "strong"
                                    else -> "subtle"
                                }
                                viewModel.setProcessingStrength(nextStrength)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("quick_strength_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = PixelBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = processingStrength.uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // App Title / Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GlassBg)
                        .border(1.dp, GlassBorder, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PixelBlue)
                            .alpha(pulseAlpha)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PIXELSHOT AI",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            // AI Processing HUD Floating Pill Badge (Top Center below top bar)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 104.dp)
            ) {
                Surface(
                    color = GlassBg,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PixelBlue)
                                .alpha(pulseAlpha)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PIXELSHOT AI ACTIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }

            // API Key Notice Floating Banner
            if (isApiKeyMissing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 144.dp, start = 24.dp, end = 24.dp)
                ) {
                    Surface(
                        color = PixelYellow.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PixelYellow.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = PixelYellow,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Set GEMINI_API_KEY in Secrets panel for AI enhancement",
                                color = PixelYellow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Camera Controls Bottom Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(PureBlack)
                .padding(bottom = 8.dp)
        ) {
            // Camera Modes Selector Row (Clean Minimalism Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("NIGHT", "PORTRAIT", "PHOTO", "VIDEO", "PRO").forEach { mode ->
                    val isSelected = (mode == "PHOTO") // Active primary mode
                    Box(
                        modifier = Modifier
                            .clickable { selectedMode = mode }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = mode,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.5.sp
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(2.dp)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Action Row (Gallery, Shutter, Switch Camera)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Preview Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onOpenGallery() }
                        .testTag("gallery_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (latestPhoto != null) {
                        val file = latestPhoto?.editedFilePath?.let { File(it) }
                            ?: File(latestPhoto!!.rawFilePath)
                        AsyncImage(
                            model = file,
                            contentDescription = "Latest Photo Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Shutter Button (Clean Minimalist Design: Outer ring + Inner white button)
                val shutterScale by animateFloatAsState(if (isCapturing) 0.88f else 1.0f)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(ShutterWhite)
                        .clickable(enabled = !isCapturing) {
                            imageCaptureUseCase?.let { captureUseCase ->
                                val executor = ContextCompat.getMainExecutor(context)
                                viewModel.capturePhoto(
                                    imageCapture = captureUseCase,
                                    executor = executor,
                                    onPhotoCaptured = onPhotoCaptured
                                )
                            }
                        }
                        .testTag("shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape)
                    )
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = PixelBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(70.dp)
                        )
                    }
                }

                // Switch Camera Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(GlassBg)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { viewModel.switchCamera() }
                        .testTag("switch_camera_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Processing Status Toast (Floating Toast)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ToastBg,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ToastBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCapturing) "AI RECONSTRUCTING DYNAMIC RANGE..." else "HDR+ COMPUTATIONAL ENGINE READY",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // Android Gestural Bar Line Indicator
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun CameraPermissionRequest(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = PixelBlue,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Camera Permission Required",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "PixelShot AI needs camera access to capture photos and post-process them into a signature Google Pixel camera look.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = PixelBlue),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.testTag("grant_permission_button")
            ) {
                Text(
                    text = "Grant Camera Permission",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
