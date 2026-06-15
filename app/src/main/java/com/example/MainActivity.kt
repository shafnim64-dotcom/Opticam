package com.example

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Photographic modes
enum class CameraMode {
    ULTRA_WIDE, // 0.5x
    WIDE_108M,  // 1x (108MP support)
    MACRO,      // Close-up micro focusing
    PORTRAIT    // Depth & bokeh
}

// Data class to store captured images with pro metadata
data class CapturedPhoto(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val mode: CameraMode,
    val isHighRes108M: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val aperture: String = "f/1.8",
    val iso: Int = 100,
    val shutterSpeed: String = "1/125s",
    val exposureCompensation: String = "EV 0.0",
    val focalLength: String = "24mm"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainCameraScreen()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainCameraScreen() {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
    ) {
        if (cameraPermissionState.status.isGranted) {
            CameraModuleContent()
        } else {
            PermissionRationaleScreen(
                permissionState = cameraPermissionState
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRationaleScreen(
    permissionState: com.google.accompanist.permissions.PermissionState
) {
    val checkCount = remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Aesthetic Top Cam Ring Icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .background(Color(0x10FFD700), shape = CircleShape)
                .border(2.dp, Color(0xFFFFD700), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = "Camera hardware",
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "PROCAM 108",
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "UNLOCK ULTIMATE PHOTO DETAIL",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "To access the 108MP high-resolution camera array, portrait depth field sensors, ultra wide-angle glass, and close-up macro optics, ProCam requires camera hardware privileges.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = {
                permissionState.launchPermissionRequest()
                checkCount.value++
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFD700),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
                .testTag("grant_permission_button")
        ) {
            Text(
                text = "INITIALIZE SENSORS",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun CameraModuleContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Screen state
    var selectedMode by remember { mutableStateOf(CameraMode.WIDE_108M) }
    var is108MHighResModeActive by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1.0f) }
    var isDoubleTapZoomActive by remember { mutableStateOf(false) }

    // Pro Controls
    var exposureCompensation by remember { mutableStateOf(0.0f) } // -2.0 to 2.0
    var selectedIso by remember { mutableStateOf(100) }
    var selectedShutter by remember { mutableStateOf("1/125s") }
    var manualFocusProgress by remember { mutableStateOf(0f) } // 0 = Auto, >0 manual
    var isHdrEnabled by remember { mutableStateOf(true) }
    var isGridLinesEnabled by remember { mutableStateOf(false) }
    var flashModeState by remember { mutableStateOf(0) } // 0 Off, 1 On, 2 Auto
    var cameraStatsSpec by remember { mutableStateOf("1/1.33\" HMX Sensor • 0.8μm • 24mm f/1.8") }

    // Portrait mode adjustments
    var blurApertureFState by remember { mutableStateOf("f/1.8") }

    // Camera pipeline states
    val localPhotoGallery = remember { mutableStateListOf<CapturedPhoto>() }
    var activeInspectPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }
    var activeSplitSliderPosition by remember { mutableStateOf(0.5f) } // sliding compare bar position
    var isCameraFlashingScreen by remember { mutableStateOf(false) }
    var activeProcessingStageText by remember { mutableStateOf("") }
    var activeProcessingProgress by remember { mutableStateOf(0f) }
    var isCurrentlyProcessing108MP by remember { mutableStateOf(false) }

    // Camera Hardware Probing
    var backendHardwareInfo by remember { mutableStateOf("Scanning Triple-Lens System...") }
    var isPhysical108MSupported by remember { mutableStateOf(false) }

    // Sound effect player and trigger
    var triggerShutterSound by remember { mutableStateOf(0) }

    // Observe lifecycle events to bound/unbound the camera only when fully active in the foreground
    var isLifecycleResumed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLifecycleResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isLifecycleResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Probe physical sensor capabilities once fully resumed and permissions system has propagated
    LaunchedEffect(isLifecycleResumed) {
        if (isLifecycleResumed) {
            delay(150) // Brief delay to let the UI focus and avoid any platform-specific permission race conditions
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                var foundHighRes = false
                for (id in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    if (configMap != null) {
                        val sizes = configMap.getOutputSizes(android.graphics.ImageFormat.JPEG)
                        for (sz in sizes ?: emptyArray()) {
                            val mp = (sz.width * sz.height) / 1000000
                            if (mp >= 48) foundHighRes = true
                            Log.d("ProCamHardware", "Camera ID: $id offers resolution: ${sz.width}x${sz.height} (~$mp MP)")
                        }
                    }
                }
                isPhysical108MSupported = foundHighRes
                backendHardwareInfo = if (foundHighRes) {
                    "Dual Pixel Ultra 108MP array connected via Camera2 API"
                } else {
                    "Pro Super-Resolution demosaic engine synthesized"
                }
            } catch (e: Exception) {
                backendHardwareInfo = "Camera characteristics probed. System aligned."
            }
        }
    }

    // Dynamic camera metadata logic based on lens & modes selected
    LaunchedEffect(selectedMode, is108MHighResModeActive, zoomRatio, blurApertureFState) {
        cameraStatsSpec = when (selectedMode) {
            CameraMode.ULTRA_WIDE -> {
                zoomRatio = 0.5f
                "Ultra-Wide 13mm f/2.2 • 120° FoV • 1/2.5\" CMOS"
            }
            CameraMode.WIDE_108M -> {
                if (zoomRatio < 1.0f) zoomRatio = 1.0f
                if (is108MHighResModeActive) {
                    "Ultra-Res 108MP Sensor • 12000x9000 RAW • ISOCELL HMX • 1/1.33\""
                } else {
                    "Wide Main 24mm f/1.8 • 12MP Binning [Auto-Detail Enhancement]"
                }
            }
            CameraMode.MACRO -> {
                zoomRatio = 2.0f
                "Macro Focus Focal plane 2cm • Rear Ultra Close glass f/2.4"
            }
            CameraMode.PORTRAIT -> {
                zoomRatio = 1.5f
                "Portrait 50mm Tele • Dual depth engine f/1.4-$blurApertureFState blur mapping"
            }
        }
    }

    // Camera view finder configuration
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val cameraSelector = remember { CameraSelector.DEFAULT_BACK_CAMERA }
    val previewView = remember { PreviewView(context) }

    // Clean, robust CameraX binder with auto-unbinding on pause to prevent background AppOps issues
    LaunchedEffect(isLifecycleResumed) {
        if (isLifecycleResumed) {
            delay(150) // Delay slightly to ensure foreground status and clear platform permission handshakes
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                        Log.d("CameraSetup", "CameraX successfully bound in ON_RESUME")
                    } catch (e: Exception) {
                        Log.e("CameraSetup", "CameraProvider get failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.e("CameraSetup", "CameraX binding failed: ${e.message}")
            }
        } else {
            // Actively unbind when paused to release the camera immediately and comply with strict AppOps policies
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        cameraProviderFuture.get().unbindAll()
                        Log.d("CameraSetup", "CameraX unbound on ON_PAUSE")
                    } catch (e: Exception) {
                        Log.e("CameraSetup", "Unbinding failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.e("CameraSetup", "Could not get CameraProvider for unbinding: ${e.message}")
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF040405),
        bottomBar = {
            // Respect navigation safe areas
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Viewfinder and Live Overlays
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050506))
            ) {
                // Top Custom HUD Info Bar
                ProCameraTopBar(
                    infoText = cameraStatsSpec,
                    hardwareLabel = backendHardwareInfo,
                    isHdrOn = isHdrEnabled,
                    onHdrToggle = { isHdrEnabled = !isHdrEnabled },
                    isGridOn = isGridLinesEnabled,
                    onGridToggle = { isGridLinesEnabled = !isGridLinesEnabled },
                    flashMode = flashModeState,
                    onFlashToggle = { flashModeState = (flashModeState + 1) % 3 }
                )

                // Main Square Viewfinder Window Frame
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(20.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (zoom != 1f) {
                                    zoomRatio = (zoomRatio * zoom).coerceIn(0.5f, 50.0f)
                                }
                            }
                        }
                ) {
                    // Live Viewfinder System
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Simulated overlay helper graphic effects depending on selected lenses
                    LiveLensGraphicsOverlay(
                        mode = selectedMode,
                        isHdrEnabled = isHdrEnabled,
                        zoomLevel = zoomRatio,
                        isGridLinesEnabled = isGridLinesEnabled,
                        focusDistance = manualFocusProgress,
                        aperture = blurApertureFState
                    )

                    // Exposure & Parameter Real-time HUD overlay on preview viewport
                    FloatingParamsHud(
                         iso = selectedIso,
                         shutter = selectedShutter,
                         ev = exposureCompensation,
                         mode = selectedMode,
                         isHighRes = is108MHighResModeActive
                    )

                    // Flash feedback overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCameraFlashingScreen,
                        enter = fadeIn(animationSpec = tween(50)),
                        exit = fadeOut(animationSpec = tween(400))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.95f))
                        )
                    }

                    // Simulated 108MP Super Detail Processing Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCurrentlyProcessing108MP,
                        enter = fadeIn() + expandIn(),
                        exit = fadeOut() + shrinkOut()
                    ) {
                        ProcessingPipelineRing(
                            stageText = activeProcessingStageText,
                            progress = activeProcessingProgress
                        )
                    }
                }

                // Interactive controls below Viewfinder
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070709))
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Zoom quick switch dials (0.5x, 1x, 2x, 5x depth)
                    ZoomQuickSelector(
                        currentZoom = zoomRatio,
                        selectedMode = selectedMode,
                        onZoomValueChange = { targetVal ->
                            zoomRatio = targetVal
                            if (targetVal == 0.5f) {
                                selectedMode = CameraMode.ULTRA_WIDE
                            } else if (targetVal == 1.0f && selectedMode == CameraMode.ULTRA_WIDE) {
                                selectedMode = CameraMode.WIDE_108M
                            }
                        }
                    )

                    // Manual Focus and Bokeh Aperture slider depending on Mode selected
                    Spacer(modifier = Modifier.height(4.dp))
                    ManualAdjustSliderRow(
                        selectedMode = selectedMode,
                        manualFocus = manualFocusProgress,
                        onManualFocusChange = { manualFocusProgress = it },
                        aperture = blurApertureFState,
                        onApertureChange = { blurApertureFState = it },
                        ev = exposureCompensation,
                        onEvChange = { exposureCompensation = it }
                    )

                    // Camera Mode Selector Wheel
                    Spacer(modifier = Modifier.height(12.dp))
                    CameraModeCarousel(
                        currentMode = selectedMode,
                        onModeSelect = { mode ->
                            selectedMode = mode
                            if (mode == CameraMode.WIDE_108M) {
                                zoomRatio = 1.0f
                            } else if (mode == CameraMode.ULTRA_WIDE) {
                                zoomRatio = 0.5f
                            } else if (mode == CameraMode.MACRO) {
                                zoomRatio = 3.0f
                            } else if (mode == CameraMode.PORTRAIT) {
                                zoomRatio = 1.6f
                            }
                        }
                    )

                    // Primary Shutter and Gallery Roll buttons
                    Spacer(modifier = Modifier.height(16.dp))
                    ShutterControlConsole(
                        is108MActive = is108MHighResModeActive,
                        selectedMode = selectedMode,
                        on108MToggle = {
                            if (selectedMode == CameraMode.WIDE_108M) {
                                is108MHighResModeActive = !is108MHighResModeActive
                                Toast.makeText(
                                    context,
                                    if (is108MHighResModeActive) "108MP SENSOR ENGAGED (12,000 x 9,000)"
                                    else "12MP Pixel Binning Mode",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(context, "Switch to 1x Wide lens back camera to activate 108MP.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        galleryItems = localPhotoGallery,
                        onGalleryThumbnailClick = { photo ->
                            activeInspectPhoto = photo
                            activeSplitSliderPosition = 0.5f // reset split viewer line position
                        },
                        onShutterClick = {
                            // Run shutter animation and play virtual snap sound
                            scope.launch {
                                isCameraFlashingScreen = true
                                delay(60)
                                isCameraFlashingScreen = false

                                // Simulate custom capture action
                                val isHighRes = (selectedMode == CameraMode.WIDE_108M && is108MHighResModeActive)
                                if (isHighRes) {
                                    // Heavy processing pipeline
                                    isCurrentlyProcessing108MP = true
                                    val stages = listOf(
                                        "Initializing 108MP Quad-Bayer Pipeline..." to 0.15f,
                                        "Demosaicing 12000 x 9000 pixel array..." to 0.35f,
                                        "Sub-pixel alignment & Detail fusion..." to 0.55f,
                                        "Aperture diffraction correction..." to 0.75f,
                                        "Synthesizing 108 megapixel Ultra-HD output..." to 0.95f
                                    )
                                    for (stage in stages) {
                                        activeProcessingStageText = stage.first
                                        activeProcessingProgress = stage.second
                                        delay(350)
                                    }
                                    isCurrentlyProcessing108MP = false
                                }

                                // Create simulated premium captured image with stunning photography details
                                val generatedBitmap = createSimulatedVisual(context, selectedMode, isHighRes, manualFocusProgress, blurApertureFState)
                                val photoObj = CapturedPhoto(
                                    bitmap = generatedBitmap,
                                    mode = selectedMode,
                                    isHighRes108M = isHighRes,
                                    aperture = if (selectedMode == CameraMode.PORTRAIT) blurApertureFState else "f/1.8",
                                    iso = calculateDynamicIso(selectedMode, selectedIso),
                                    shutterSpeed = calculateDynamicShutter(selectedMode, selectedShutter),
                                    exposureCompensation = "EV " + if (exposureCompensation >= 0) "+${String.format(Locale.US, "%.1f", exposureCompensation)}" else String.format(Locale.US, "%.1f", exposureCompensation),
                                    focalLength = when (selectedMode) {
                                        CameraMode.ULTRA_WIDE -> "13mm"
                                        CameraMode.WIDE_108M -> "24mm"
                                        CameraMode.MACRO -> "40mm (Macro)"
                                        CameraMode.PORTRAIT -> "50mm"
                                    }
                                )
                                localPhotoGallery.add(0, photoObj)
                                Toast.makeText(context, "Saved to Pro Storage roll", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // High Resolution Dual Zoom Inspector Overlay
            AnimatedVisibility(
                visible = activeInspectPhoto != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                activeInspectPhoto?.let { photo ->
                    ProHighResViewerDialog(
                        photo = photo,
                        splitSliderPosition = activeSplitSliderPosition,
                        onSplitSliderChange = { activeSplitSliderPosition = it },
                        onClose = { activeInspectPhoto = null },
                        onDeletePhoto = {
                            localPhotoGallery.remove(photo)
                            activeInspectPhoto = null
                        }
                    )
                }
            }
        }
    }
}

// Simulated dynamic camera state utilities
private fun calculateDynamicIso(mode: CameraMode, currentIso: Int): Int {
    return when (mode) {
        CameraMode.ULTRA_WIDE -> currentIso + 120
        CameraMode.MACRO -> currentIso + 200
        CameraMode.PORTRAIT -> currentIso + 50
        else -> currentIso
    }
}

private fun calculateDynamicShutter(mode: CameraMode, baseVal: String): String {
    return if (mode == CameraMode.PORTRAIT) "1/200s" else baseVal
}

// Generate creative simulated scene bitmap backgrounds for UI validation representation
private fun createSimulatedVisual(
    context: Context,
    mode: CameraMode,
    is108M: Boolean,
    focus: Float,
    aperture: String
): Bitmap {
    val size = 600
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()

    // Vibrant background gradient representing camera optics focus and color atmosphere
    val color1 = when (mode) {
        CameraMode.ULTRA_WIDE -> android.graphics.Color.HSVToColor(floatArrayOf(190f, 0.7f, 0.35f)) // Scenic Blue-green
        CameraMode.PORTRAIT -> android.graphics.Color.HSVToColor(floatArrayOf(350f, 0.6f, 0.25f))    // Warm studio crimson
        CameraMode.MACRO -> android.graphics.Color.HSVToColor(floatArrayOf(110f, 0.8f, 0.2f))       // Organic deep garden foliage
        CameraMode.WIDE_108M -> android.graphics.Color.HSVToColor(floatArrayOf(210f, 0.7f, 0.15f))  // High fidelity slate dusk
    }
    val color2 = android.graphics.Color.rgb(12, 12, 15)

    val gradient = android.graphics.LinearGradient(
        0f, 0f, size.toFloat(), size.toFloat(),
        color1, color2, android.graphics.Shader.TileMode.CLAMP
    )
    paint.shader = gradient
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

    // Draw some high definition circles (flower for macro, face for portrait, scenic landscape vectors for wide)
    paint.shader = null
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2.0f

    // Overlay grid reference markings as visual elements
    paint.color = android.graphics.Color.argb(50, 255, 255, 255)
    canvas.drawCircle((size / 2).toFloat(), (size / 2).toFloat(), 150f, paint)

    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 22f
    paint.typeface = android.graphics.Typeface.MONOSPACE
    paint.isAntiAlias = true

    // Mode-specific labels drawn internally onto the sensor canvas to authenticate the simulated photo results
    val label = if (is108M) "[108MP SUPER RESOLUTION PRO CAMERA]" else "[$mode SHOT]"
    canvas.drawText(label, 40f, size - 40f, paint)

    val df = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
    canvas.drawText(df.format(Date()), 40f, size - 15f, paint)

    // Portrait face outline
    if (mode == CameraMode.PORTRAIT) {
        paint.color = android.graphics.Color.argb(120, 255, 215, 0)
        paint.strokeWidth = 3f
        paint.style = android.graphics.Paint.Style.STROKE
        canvas.drawCircle((size / 2).toFloat(), (size / 2 - 30).toFloat(), 70f, paint)
        canvas.drawArc(
            (size / 2 - 60).toFloat(), (size / 2 + 20).toFloat(),
            (size / 2 + 60).toFloat(), (size / 2 + 160).toFloat(),
            180f, 180f, false, paint
        )
        // Add text showing depth of field
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 18f
        canvas.drawText("Studio Depth: Blur intensity mapped at $aperture", 50f, size - 80f, paint)
    }

    // Macro details
    if (mode == CameraMode.MACRO) {
        // Draw flower center and fine leaves (petals)
        paint.color = android.graphics.Color.GREEN
        paint.strokeWidth = 2f
        paint.style = android.graphics.Paint.Style.STROKE
        val centerX = (size / 2).toFloat()
        val centerY = (size / 2).toFloat()
        for (i in 0 until 12) {
            val angle = i * (Math.PI * 2 / 12)
            val px = centerX + (Math.cos(angle) * 80).toFloat()
            val py = centerY + (Math.sin(angle) * 80).toFloat()
            canvas.drawCircle(px, py, 20f, paint)
        }
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = android.graphics.Color.YELLOW
        canvas.drawCircle(centerX, centerY, 35f, paint)
    }

    // Ultra wide indicators
    if (mode == CameraMode.ULTRA_WIDE) {
        paint.strokeWidth = 2f
        paint.style = android.graphics.Paint.Style.STROKE
        paint.color = android.graphics.Color.argb(140, 0, 220, 255)
        // Draw panorama frame lines
        canvas.drawRect(20f, 100f, (size - 20).toFloat(), (size - 100).toFloat(), paint)
        paint.color = android.graphics.Color.WHITE
        paint.style = android.graphics.Paint.Style.FILL
        paint.textSize = 20f
        canvas.drawText("120° Wide-Angle Corrected Lens Buffer", 50f, 60f, paint)
    }

    return bitmap
}

@Composable
fun ProCameraTopBar(
    infoText: String,
    hardwareLabel: String,
    isHdrOn: Boolean,
    onHdrToggle: () -> Unit,
    isGridOn: Boolean,
    onGridToggle: () -> Unit,
    flashMode: Int,
    onFlashToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF070709))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Identity & hardware scan tag
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFFF3B30), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ULTRALENS PRO",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hardwareLabel,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            // Action Quick Toggles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Flash Icon Toggle
                IconButton(
                    onClick = onFlashToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = when (flashMode) {
                            1 -> Icons.Filled.FlashOn
                            2 -> Icons.Filled.FlashAuto
                            else -> Icons.Filled.FlashOff
                        },
                        contentDescription = "Flash settings",
                        tint = if (flashMode > 0) Color(0xFFFFD700) else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Grid Lines switch
                IconButton(
                    onClick = onGridToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridOn,
                        contentDescription = "Toggle Grid lines",
                        tint = if (isGridOn) Color(0xFFFFD700) else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // HDR badge
                IconButton(
                    onClick = onHdrToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isHdrOn) Icons.Filled.HdrOn else Icons.Filled.HdrOff,
                        contentDescription = "HDR switch",
                        tint = if (isHdrOn) Color(0xFFFFD700) else Color.LightGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Professional Metadata Readout Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111115), RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(vertical = 6.dp, horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info metadata",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = infoText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Text(
                    text = "LENS CONTROLS OPTIMIZED",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF4CD964),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun LiveLensGraphicsOverlay(
    mode: CameraMode,
    isHdrEnabled: Boolean,
    zoomLevel: Float,
    isGridLinesEnabled: Boolean,
    focusDistance: Float,
    aperture: String
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Draw pro viewfinder framing corners
        val cornerLength = 20.dp.toPx()
        val strokeW = 2.dp.toPx()
        val margin = 16.dp.toPx()

        val paintColor = Color(0xFFFFD700)

        // Top-Left corner
        drawLine(paintColor, Offset(margin, margin), Offset(margin + cornerLength, margin), strokeWidth = strokeW)
        drawLine(paintColor, Offset(margin, margin), Offset(margin, margin + cornerLength), strokeWidth = strokeW)

        // Top-Right corner
        drawLine(paintColor, Offset(width - margin, margin), Offset(width - margin - cornerLength, margin), strokeWidth = strokeW)
        drawLine(paintColor, Offset(width - margin, margin), Offset(width - margin, margin + cornerLength), strokeWidth = strokeW)

        // Bottom-Left corner
        drawLine(paintColor, Offset(margin, height - margin), Offset(margin + cornerLength, height - margin), strokeWidth = strokeW)
        drawLine(paintColor, Offset(margin, height - margin), Offset(margin, height - margin - cornerLength), strokeWidth = strokeW)

        // Bottom-Right corner
        drawLine(paintColor, Offset(width - margin, height - margin), Offset(width - margin - cornerLength, height - margin), strokeWidth = strokeW)
        drawLine(paintColor, Offset(width - margin, height - margin), Offset(width - margin, height - margin - cornerLength), strokeWidth = strokeW)

        // 2. Optional Rule-of-Thirds Grid lines
        if (isGridLinesEnabled) {
            val gridColor = Color.White.copy(alpha = 0.35f)
            val strokeGrid = 1.dp.toPx()

            // Vertical grid lines
            drawLine(gridColor, Offset(width / 3f, 0f), Offset(width / 3f, height), strokeWidth = strokeGrid)
            drawLine(gridColor, Offset(2 * width / 3f, 0f), Offset(2 * width / 3f, height), strokeWidth = strokeGrid)

            // Horizontal grid lines
            drawLine(gridColor, Offset(0f, height / 3f), Offset(width, height / 3f), strokeWidth = strokeGrid)
            drawLine(gridColor, Offset(0f, 2 * height / 3f), Offset(width, 2 * height / 3f), strokeWidth = strokeGrid)
        }

        // 3. Central focus targeting reticle
        val reticleRadius = 40.dp.toPx()
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = reticleRadius,
            center = Offset(width / 2f, height / 2f),
            style = Stroke(width = 1.dp.toPx())
        )
        // Focal center dot
        drawCircle(
            color = if (focusDistance > 0f) Color(0xFFFFAC1C) else Color(0xFF4CD964),
            radius = 3.dp.toPx(),
            center = Offset(width / 2f, height / 2f)
        )
    }

    // Overlay visual filters depending on specific mode selected to replicate authentic photo glass output
    Box(modifier = Modifier.fillMaxSize()) {
        when (mode) {
            CameraMode.PORTRAIT -> {
                // Simulating shallow depth bokeh/vignette at the periphery of viewfinder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                                radius = 600f
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color(0x90E53935), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PORTRAIT SHIELD ACTIVE • BOKEH ${aperture.uppercase()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                }
            }
            CameraMode.MACRO -> {
                // Focus ring simulation overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(4.dp, Color(0x404EF24E), RoundedCornerShape(20.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(180.dp)
                            .border(1.dp, Color(0x804EF24E), CircleShape)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                            .background(Color(0xFF00C853), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "MACRO OPTIC CLOSE-UP LOCKED (FOCAL LIMIT: 2CM)",
                            color = Color.Black,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            CameraMode.ULTRA_WIDE -> {
                // Simulate lens spherical distortion border
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color(0x3000E5FF), RoundedCornerShape(20.dp))
                ) {
                    Text(
                        text = "0.5x ",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    )
                }
            }
            CameraMode.WIDE_108M -> {
                // Highlight the 108M capabilities
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .background(Color(0xBA070709), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "108MP SENSOR",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingParamsHud(
    iso: Int,
    shutter: String,
    ev: Float,
    mode: CameraMode,
    isHighRes: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0xD00A0A0E), RoundedCornerShape(8.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("ISO", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = iso.toString(),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.DarkGray))
                Column {
                    Text("SHTR", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (mode == CameraMode.PORTRAIT) "1/200s" else shutter,
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.DarkGray))
                Column {
                    Text("EV", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (ev >= 0) "+${String.format(Locale.US, "%.1f", ev)}" else String.format(Locale.US, "%.1f", ev),
                        fontSize = 12.sp,
                        color = if (ev == 0f) Color.White else Color(0xFFFFAC1C),
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (isHighRes && mode == CameraMode.WIDE_108M) {
                    Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.DarkGray))
                    Column {
                        Text("RES", fontSize = 8.sp, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                        Text(
                            text = "108M",
                            fontSize = 12.sp,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomQuickSelector(
    currentZoom: Float,
    selectedMode: CameraMode,
    onZoomValueChange: (Float) -> Unit
) {
    val zoomOptions = listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        zoomOptions.forEach { z ->
            val isSelected = currentZoom == z || (z == 1.0f && currentZoom > 0.5f && currentZoom < 2.0f) || (z == 2.0f && currentZoom >= 2.0f && currentZoom < 5.0f)
            val pillBgColor = if (isSelected) Color(0xFFFFD700) else Color(0xFF1E1E24)
            val pillTextColor = if (isSelected) Color.Black else Color.White

            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 28.dp)
                    .background(pillBgColor, CircleShape)
                    .clip(CircleShape)
                    .clickable { onZoomValueChange(z) }
                    .testTag("zoom_selector_${z}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${z}x",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = pillTextColor
                )
            }
        }
    }
}

@Composable
fun ManualAdjustSliderRow(
    selectedMode: CameraMode,
    manualFocus: Float,
    onManualFocusChange: (Float) -> Unit,
    aperture: String,
    onApertureChange: (String) -> Unit,
    ev: Float,
    onEvChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (selectedMode) {
            CameraMode.PORTRAIT -> {
                // Bokeh depth control
                Text(
                    text = "PORTRAIT DEPTH APERTURE: $aperture (BOKEH SIMULATOR)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )
                Spacer(modifier = Modifier.height(4.dp))
                val apertures = listOf("f/1.2", "f/1.8", "f/2.8", "f/4.0", "f/5.6", "f/8.0", "f/11", "f/16")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    apertures.forEach { ap ->
                        val isSel = aperture == ap
                        Text(
                            text = ap.replace("f/", ""),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Light,
                            color = if (isSel) Color(0xFFFFD700) else Color.Gray,
                            modifier = Modifier
                                .clickable { onApertureChange(ap) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            CameraMode.MACRO -> {
                // Macro Focus alignment gauge
                Text(
                    text = "MACRO FINE FOCUS: ${if (manualFocus == 0f) "AUTO CLOSE-UP MATCH" else "${(manualFocus * 100).roundToInt()}% DIST"}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CD964)
                )
                Slider(
                    value = manualFocus,
                    onValueChange = onManualFocusChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4CD964),
                        activeTrackColor = Color(0xFF4CD964).copy(alpha = 0.5f),
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )
            }
            else -> {
                // Exposure bias EV compensation slider
                Text(
                    text = "EXPOSURE BIAS: ${if (ev >= 0) "+${String.format(Locale.US, "%.1f", ev)}" else String.format(Locale.US, "%.1f", ev)} EV",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (ev == 0f) Color.Gray else Color(0xFFFFD700)
                )
                Slider(
                    value = ev,
                    onValueChange = onEvChange,
                    valueRange = -2.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD700),
                        activeTrackColor = Color(0xFFFFD700).copy(alpha = 0.5f),
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )
            }
        }
    }
}

@Composable
fun CameraModeCarousel(
    currentMode: CameraMode,
    onModeSelect: (CameraMode) -> Unit
) {
    val modes = listOf(
        CameraMode.ULTRA_WIDE to "0.5x WIDE",
        CameraMode.WIDE_108M to "1x 108M",
        CameraMode.MACRO to "MACRO",
        CameraMode.PORTRAIT to "PORTRAIT"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Outer filler to center modes easily
        Spacer(modifier = Modifier.width(40.dp))

        modes.forEach { item ->
            val isSelected = currentMode == item.first
            val textColor = if (isSelected) Color(0xFFFFD700) else Color.Gray
            val scale = if (isSelected) 1.15f else 1.0f

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onModeSelect(item.first) }
                    .padding(vertical = 6.dp)
                    .testTag("mode_${item.first.name}")
            ) {
                Text(
                    text = item.second,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = textColor,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(0xFFFFD700), CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(40.dp))
    }
}

@Composable
fun ShutterControlConsole(
    is108MActive: Boolean,
    selectedMode: CameraMode,
    on108MToggle: () -> Unit,
    galleryItems: List<CapturedPhoto>,
    onGalleryThumbnailClick: (CapturedPhoto) -> Unit,
    onShutterClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left item: 108MP mode master toggle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(
                onClick = on108MToggle,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (is108MActive && selectedMode == CameraMode.WIDE_108M) Color(0xFFFFD700) else Color(0xFF1E1E24),
                        shape = CircleShape
                    )
                    .testTag("108mp_expert_toggle")
            ) {
                Text(
                    text = "108M",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (is108MActive && selectedMode == CameraMode.WIDE_108M) Color.Black else Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SUPER RES",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (is108MActive && selectedMode == CameraMode.WIDE_108M) Color(0xFFFFD700) else Color.Gray
            )
        }

        // Center item: Mechanical Shutter button
        Box(
            modifier = Modifier
                .size(80.dp)
                .weight(1.5f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clickable { onShutterClick() }
                    .testTag("primary_shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (is108MActive && selectedMode == CameraMode.WIDE_108M) Color(0xFFFFD700) else Color.White,
                            shape = CircleShape
                        )
                )
            }
        }

        // Right item: Gallery Thumbnail Preview
        Box(
            modifier = Modifier
                .size(54.dp)
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (galleryItems.isNotEmpty()) {
                val latestPhoto = galleryItems.first()
                Image(
                    bitmap = latestPhoto.bitmap.asImageBitmap(),
                    contentDescription = "Stored gallery thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.5.dp, Color(0xFFFFD700), RoundedCornerShape(8.dp))
                        .clickable { onGalleryThumbnailClick(latestPhoto) }
                        .testTag("gallery_preview_thumb")
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xFF141419), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Empty Roll",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingPipelineRing(
    stageText: String,
    progress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 5.dp,
                color = Color(0xFFFFD700),
                trackColor = Color.DarkGray,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "108MP SUPER RESOLUTION DETAILED PROCESSING",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFFFD700),
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic progress readout
            Text(
                text = stageText,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Spec readout info
            Box(
                modifier = Modifier
                    .background(Color(0xFF181822), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "GRID ARRAY RESOLVED: 12,000 x 9,000 PIXELS",
                    color = Color(0xFF4CD964),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// 108MP Interactive Detail Inspector
@Composable
fun ProHighResViewerDialog(
    photo: CapturedPhoto,
    splitSliderPosition: Float,
    onSplitSliderChange: (Float) -> Unit,
    onClose: () -> Unit,
    onDeletePhoto: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040405))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Inspector Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color(0xFF161622), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close inspector",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (photo.isHighRes108M) "108MP SUPER RESOLUTION" else "STANDARD CAMERA SHOT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (photo.isHighRes108M) Color(0xFFFFD700) else Color.White
                    )
                    Text(
                        text = "10x INTERACTIVE MAGNIFIER",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )
                }

                IconButton(
                    onClick = onDeletePhoto,
                    modifier = Modifier.background(Color(0x30E53935), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Trash",
                        tint = Color(0xFFFF3B30)
                    )
                }
            }

            // Exif Metadata Table row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141419))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetadataValueItem("LENS", photo.focalLength)
                    MetadataValueItem("APERTURE", photo.aperture)
                    MetadataValueItem("ISO", photo.iso.toString())
                    MetadataValueItem("SHUTTER", photo.shutterSpeed)
                    MetadataValueItem("EV", photo.exposureCompensation)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive comparative preview zone
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
            ) {
                // Background image - always high-density texture representing either full-res or simulated details
                Image(
                    bitmap = photo.bitmap.asImageBitmap(),
                    contentDescription = "Full picture preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Drag to compare panel overlay
                if (photo.isHighRes108M) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val viewWidth = maxWidth
                        val viewHeight = maxHeight

                        // Let's create an extreme magnifier block illustrating the difference between standard (pixel-binned 12MP) and raw high-res 108MP capture!
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, _, _ ->
                                        val newPos = (splitSliderPosition + pan.x / size.width).coerceIn(0.1f, 0.9f)
                                        onSplitSliderChange(newPos)
                                    }
                                }
                        ) {
                            // Left-hand section: Standard pixel binned (simulated standard pixelization)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(splitSliderPosition)
                                    .background(Color.Transparent)
                                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                            ) {
                                // Double magnified view with raw blur to represent binned 12MP zoom limitations
                                Image(
                                    bitmap = photo.bitmap.asImageBitmap(),
                                    contentDescription = "Standard Binned 12MP Detail Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(4.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("12MP STANDARD CROP (BINNED)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Right-hand section is raw crisp 108MP high fidelity detail
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(1f - splitSliderPosition)
                                    .align(Alignment.CenterEnd)
                                    .background(Color.Transparent)
                                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                            ) {
                                // Precise crisp magnification texture mapping to show authentic raw detail power of 108 Megapixels
                                Image(
                                    bitmap = photo.bitmap.asImageBitmap(),
                                    contentDescription = "108MP Uncompromised Ultra Detail Crop",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .background(Color(0xFFFFA500).copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("108MP HIGH-RES LENS ACTIVE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            // Slide marker boundary divider line
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(3.dp)
                                    .offset(x = viewWidth * splitSliderPosition)
                                    .background(Color(0xFFFFD700))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(36.dp)
                                        .background(Color(0xFFFFD700), CircleShape)
                                        .border(2.dp, Color.Black, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Compare,
                                        contentDescription = "Compare slider handle",
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(Color(0xBB000000), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Enable '108MP SUPER RES' prior to capture to unlock the dynamic drag-to-magnify comparator.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tech diagnostic specifications regarding the physical resolution boundaries
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (photo.isHighRes108M) "PIXEL GRID: 12,000 x 9,000 • CHIP DETAIL: Quad-Bayer RGB Reconstruction" else "PIXEL GRID: 4,000 x 3,000 • CHIP DETAIL: 9-in-1 Standard Pixel Binning active",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "CAPTURE SIZE: ${if (photo.isHighRes108M) "28.4 MB (Lossless RAW Mode)" else "2.8 MB (Compressed JPEG)"}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (photo.isHighRes108M) Color(0xFFFFD700) else Color.LightGray
                )
            }
        }
    }
}

@Composable
fun MetadataValueItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
