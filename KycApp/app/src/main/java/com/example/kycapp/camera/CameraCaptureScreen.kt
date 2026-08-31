package com.example.kycapp.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.kycapp.biometrics.face.FaceEmbedder
import com.example.kycapp.biometrics.face.FaceEmbeddingResult
import com.example.kycapp.biometrics.face.extractFaceEmbedding
import com.example.kycapp.biometrics.face.extractFivePoints
import com.example.kycapp.biometrics.face.alignFace
import com.example.kycapp.biometrics.face.matchFaces
import com.example.kycapp.biometrics.fingerprint.FingerprintEnrollmentService
import com.example.kycapp.biometrics.fingerprint.FingerprintVerificationService
import com.example.kycapp.biometrics.idcard.IdCardClassifier
import com.example.kycapp.biometrics.liveness.BlinkLivenessAnalyzer
import com.example.kycapp.biometrics.liveness.BlinkLivenessChecker
import com.example.kycapp.biometrics.liveness.FaceLandmarkerProvider
import com.example.kycapp.biometrics.ocr.TesseractEngine
import com.example.kycapp.biometrics.ocr.scanSingleImage
import com.example.kycapp.data.AppDatabase
import com.example.kycapp.data.FingerprintTemplateRepository
import com.example.kycapp.data.IdCardPhotoRepository
import com.example.kycapp.navigation.CaptureMode
import com.example.kycapp.navigation.HandSide
import com.example.kycapp.ui.components.DfsPrimaryButton
import com.example.kycapp.ui.components.DfsStatusBadge
import com.example.kycapp.ui.components.DfsTextButton
import com.example.kycapp.ui.theme.DfsColors
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

private const val TAG = "CameraCaptureScreen"

/** Face-match verifies a live liveness-gated selfie against the ID card
 * photo OCR already extracted and saved (see IdCardPhotoRepository) -- this
 * mirrors the original Python backend's flow (mod_face.py's
 * latest_id_photo_path()) exactly: run OCR once, then verify against it any
 * number of times, rather than re-photographing the card for every
 * face-match attempt. There's no separate "capture ID photo" step here
 * anymore; run OCR first if no card is on file yet. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    mode: CaptureMode,
    hand: HandSide = HandSide.NONE,
    personName: String = "",
    onBack: () -> Unit,
    onScanSuccess: () -> Unit = onBack
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var statusText by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var ocrResultText by remember { mutableStateOf("") } // <-- Added to hold OCR review text
    // Face photo OCR already crops out of the ID card (scanSingleImage's
    // idPhoto) -- shown alongside the OCR field results instead of being
    // discarded, since OCR's own ID-card gate means this crop comes from a
    // capture already confirmed to be a good, in-frame card photo.
    var ocrIdPhoto by remember { mutableStateOf<Bitmap?>(null) }

    // "View Match" for Face Matching: the two 112x112 aligned crops the match
    // was actually decided from, shown side by side when the user asks.
    var faceMatchImages by remember { mutableStateOf<Pair<Bitmap, Bitmap>?>(null) }
    var showFaceCompareDialog by remember { mutableStateOf(false) }

    var idEmbeddingResult by remember { mutableStateOf<FaceEmbeddingResult?>(null) }
    var idCardPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var liveEmbeddingResult by remember { mutableStateOf<FaceEmbeddingResult?>(null) }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Face-match only ever does the live selfie via the camera now (the ID
    // photo comes from storage, not a fresh capture), so it starts on the
    // front camera immediately rather than switching mid-flow.
    var lensFacing by remember {
        mutableStateOf(
            if (mode == CaptureMode.FACE_MATCH) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }
    var torchOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var hasFlashUnit by remember { mutableStateOf(false) }

    val isFingerprintMode = mode == CaptureMode.FINGERPRINT_ENROLL || mode == CaptureMode.FINGERPRINT_MATCH
    val isOnDeviceMode = isFingerprintMode || mode == CaptureMode.ID_CARD ||
        mode == CaptureMode.FACE_MATCH || mode == CaptureMode.OCR
    val fingerprintRepository = remember {
        FingerprintTemplateRepository(AppDatabase.getInstance(context).fingerprintTemplateDao())
    }
    val idCardPhotoRepository = remember {
        IdCardPhotoRepository(AppDatabase.getInstance(context).idCardPhotoDao())
    }

    // OCR needs the ID-card classifier as its capture gate (same as the
    // Python backend's scan_single_image), and a still-image face landmarker
    // to crop the CNIC's printed photo. Face-match no longer captures/
    // classifies an ID card of its own -- it loads the photo OCR already
    // saved -- so it doesn't need the classifier, only the landmarker (to
    // re-embed the stored photo) and the embedder.
    val idCardClassifier = remember(mode) {
        if (mode == CaptureMode.ID_CARD || mode == CaptureMode.OCR) IdCardClassifier(context) else null
    }
    DisposableEffect(idCardClassifier) { onDispose { idCardClassifier?.close() } }

    val faceEmbedder = remember(mode) {
        if (mode == CaptureMode.FACE_MATCH) FaceEmbedder(context) else null
    }
    DisposableEffect(faceEmbedder) { onDispose { faceEmbedder?.close() } }

    val stillFaceLandmarker = remember(mode) {
        if (mode == CaptureMode.FACE_MATCH || mode == CaptureMode.OCR) {
            FaceLandmarkerProvider.createForStillImages(context)
        } else null
    }
    DisposableEffect(stillFaceLandmarker) { onDispose { stillFaceLandmarker?.close() } }

    // Load the ID photo OCR already saved, and embed it immediately -- this
    // is what the live selfie gets compared against below. No camera
    // permission needed for this since it only reads from local storage.
    LaunchedEffect(mode) {
        if (mode != CaptureMode.FACE_MATCH) return@LaunchedEffect
        statusText = "Loading your ID card photo..."
        val loaded = withContext(Dispatchers.Default) {
            val entity = idCardPhotoRepository.loadLatest() ?: return@withContext null
            val photoMat = idCardPhotoRepository.decode(entity)
            val embedding = extractFaceEmbedding(stillFaceLandmarker!!, faceEmbedder!!, photoMat)
                ?: return@withContext null
            embedding to photoMat
        }
        if (loaded == null) {
            statusText = "No ID card on file — run \"Perform OCR\" first, then come back here to verify."
        } else {
            val (embedding, photoMat) = loaded
            idEmbeddingResult = embedding
            idCardPhotoBitmap = matToBitmap(photoMat)
            statusText = "Ready — look at the camera and blink naturally to verify."
        }
    }

    val livenessChecker = remember(mode) {
        if (mode == CaptureMode.FACE_MATCH) BlinkLivenessChecker() else null
    }
    val livenessAnalyzerRelay = remember { arrayOfNulls<BlinkLivenessAnalyzer>(1) }
    // The exact Bitmap handed to detectAsync() for the frame currently being
    // processed -- used instead of BitmapExtractor.extract(mpImage) below,
    // since that MediaPipe round-trip (Bitmap -> MPImage -> extracted
    // Bitmap) was coming back with visibly corrupted colors (channel
    // fringing/ghosting) on this device, badly distorting the embedding
    // input even though face *detection* on the same MPImage worked fine --
    // which is exactly why the standalone blink-liveness test (landmarks
    // only, never touches pixel color data) never surfaced this. Safe to
    // read here because BlinkLivenessAnalyzer's busy-flag guards against a
    // new frame being submitted before this one's onResult fires.
    val lastLiveBitmapRelay = remember { arrayOfNulls<Bitmap>(1) }
    val liveFaceLandmarker = remember(mode) {
        if (mode != CaptureMode.FACE_MATCH || livenessChecker == null || faceEmbedder == null) return@remember null
        FaceLandmarkerProvider.create(context, onResult = { result, mpImage ->
            val wasPassed = livenessChecker.passed
            livenessChecker.update(result.faceLandmarks().firstOrNull(), mpImage.width, mpImage.height)
            livenessAnalyzerRelay[0]?.notifyResultProcessed()
            Log.d(TAG, "liveness frame: faceSeen=${livenessChecker.faceSeen} ear=${livenessChecker.lastEar} elapsedSec=${livenessChecker.elapsedSec}")

            // The exact frame that flips passed=false -> true is the one we
            // embed -- mpImage is the same bitmap MediaPipe just processed,
            // so there's no separate "grab a frame" step or timestamp race.
            if (!wasPassed && livenessChecker.passed && liveEmbeddingResult == null) {
                Log.d(TAG, "liveness passed at elapsedSec=${livenessChecker.elapsedSec} lastEar=${livenessChecker.lastEar}")
                try {
                    val bitmap = lastLiveBitmapRelay[0] ?: BitmapExtractor.extract(mpImage)
                    val rgba = Mat()
                    Utils.bitmapToMat(bitmap, rgba)
                    val bgr = Mat()
                    Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                    val landmarks = result.faceLandmarks().first()
                    val points = extractFivePoints(landmarks, bgr.cols(), bgr.rows())
                    val aligned = alignFace(bgr, points)
                    val embedding = faceEmbedder.embed(aligned)
                    liveEmbeddingResult = FaceEmbeddingResult(embedding, aligned)
                } catch (e: Exception) {
                    // Leave liveEmbeddingResult null -- the wait loop below
                    // times out and reports failure rather than crashing.
                }
            }
        })
    }
    DisposableEffect(liveFaceLandmarker) { onDispose { liveFaceLandmarker?.close() } }

    val livenessAnalyzer = remember(livenessChecker, liveFaceLandmarker) {
        if (livenessChecker == null || liveFaceLandmarker == null) return@remember null
        BlinkLivenessAnalyzer(livenessChecker) { bitmap, timestampMs ->
            lastLiveBitmapRelay[0] = bitmap
            liveFaceLandmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
        }.also { livenessAnalyzerRelay[0] = it }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(cameraProvider, lensFacing, previewView) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val pv = previewView ?: return@LaunchedEffect

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(pv.surfaceProvider)
        }
        // Every mode is now either a single deliberate still shot (sharpness
        // matters -- ORB, the ID-card classifier, and face embedding all need
        // crisp detail) or, for face-match's live-verify step, a live
        // analysis stream that doesn't use ImageCapture at all.
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val useCases = mutableListOf<UseCase>(preview, capture)
        if (mode == CaptureMode.FACE_MATCH) {
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            livenessAnalyzer?.let { analysis.setAnalyzer(analysisExecutor, it) }
            useCases.add(analysis)
        }

        try {
            provider.unbindAll()
            val boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
            camera = boundCamera
            imageCapture = capture
            hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
            zoomRatio = 1f
            // Fingerprint capture wants consistent illumination on the hand
            // every time, so the torch turns on by itself rather than
            // requiring a manual toggle -- every other mode keeps the torch
            // off by default like before.
            torchOn = isFingerprintMode
        } catch (e: Exception) {
            statusText = "Camera init failed: ${e.message}"
        }
    }

    LaunchedEffect(torchOn, camera) {
        if (hasFlashUnit) {
            camera?.cameraControl?.enableTorch(torchOn)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DfsColors.Background)
    ) {
        TopAppBar(
            title = {
                Column {
                    DfsStatusBadge(
                        label = when {
                            mode == CaptureMode.FACE_MATCH -> "Live verify"
                            isFingerprintMode -> hand.label
                            else -> "Capture"
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val title = when {
                        mode == CaptureMode.FACE_MATCH -> "${mode.label} — Live Verify"
                        hand != HandSide.NONE -> "${mode.label} — ${hand.label}"
                        else -> mode.label
                    }
                    Text(title, color = DfsColors.OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            },
            navigationIcon = {
                DfsTextButton(text = "Back", onClick = onBack, color = DfsColors.Primary)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DfsColors.SurfaceElevated,
                titleContentColor = DfsColors.OnBackground,
                navigationIconContentColor = DfsColors.Primary
            )
        )

        if (hasCameraPermission) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(camera) {
                            detectTransformGestures { _, _, gestureZoom, _ ->
                                val cam = camera ?: return@detectTransformGestures
                                val currentRatio = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                                val minRatio = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                                val maxRatio = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                                val newRatio = min(max(currentRatio * gestureZoom, minRatio), maxRatio)
                                cam.cameraControl.setZoomRatio(newRatio)
                                zoomRatio = newRatio
                            }
                        }
                        .pointerInput(camera, previewView) {
                            detectTapGestures { offset ->
                                val cam = camera ?: return@detectTapGestures
                                val pv = previewView ?: return@detectTapGestures
                                val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                cam.cameraControl.startFocusAndMetering(action)
                            }
                        },
                    factory = { ctx ->
                        val pv = PreviewView(ctx)
                        previewView = pv
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            cameraProvider = cameraProviderFuture.get()
                        }, ContextCompat.getMainExecutor(ctx))
                        pv
                    }
                )

                if (isFingerprintMode) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val boxWidth = size.width * 0.92f
                        val boxHeight = size.height * 0.62f
                        val boxLeft = size.width * 0.02f
                        val boxTop = size.height * 0.14f

                        val drawGuide: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                            drawDShapeGuide(
                                color = Color.White,
                                strokeWidth = 3.dp.toPx(),
                                left = boxLeft,
                                top = boxTop,
                                width = boxWidth,
                                height = boxHeight
                            )

                            drawCrosshairReticle(
                                color = Color.White,
                                center = Offset(boxLeft + boxWidth * 0.42f, boxTop + boxHeight * 0.42f),
                                radius = 26.dp.toPx(),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        // The guide's flat edge sits where the wrist/palm connects and
                        // its rounded bulge is where fingertips curl -- that's mirrored
                        // between hands (thumb side flips), so the right hand needs the
                        // whole guide flipped horizontally rather than redrawn from scratch.
                        if (hand == HandSide.RIGHT) {
                            scale(
                                scaleX = -1f, scaleY = 1f,
                                pivot = Offset(boxLeft + boxWidth / 2f, boxTop + boxHeight / 2f)
                            ) { drawGuide() }
                        } else {
                            drawGuide()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Place your ${hand.label.lowercase()} in the box, fingers together, " +
                                    "thumb out of frame, and stay still",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Taking Picture",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                if (mode == CaptureMode.FACE_MATCH) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        idCardPhotoBitmap?.let { photo ->
                            Text(
                                text = "Matching against this ID card photo",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Image(
                                bitmap = photo.asImageBitmap(),
                                contentDescription = "ID card photo on file",
                                modifier = Modifier.height(72.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "Look at the camera and blink naturally",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (hasFlashUnit && !isFingerprintMode) {
                        IconButton(onClick = { torchOn = !torchOn }) {
                            Icon(
                                imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle flash",
                                tint = Color.White
                            )
                        }
                    }
                    if (zoomRatio > 1.01f) {
                        Text(
                            text = "%.1fx".format(zoomRatio),
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip camera",
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DfsColors.SurfaceElevated)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (statusText.isNotEmpty()) {
                    val statusColor = when {
                        statusText.contains("MATCH", ignoreCase = true) &&
                            !statusText.contains("NO MATCH", ignoreCase = true) -> DfsColors.Success
                        statusText.contains("NO MATCH", ignoreCase = true) ||
                            statusText.contains("failed", ignoreCase = true) ||
                            statusText.contains("Not an ID", ignoreCase = true) -> DfsColors.Danger
                        statusText.contains("Processing", ignoreCase = true) ||
                            statusText.contains("Blink", ignoreCase = true) -> DfsColors.Warning
                        else -> DfsColors.MutedText
                    }
                    Text(statusText, color = statusColor, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (mode == CaptureMode.FACE_MATCH && faceMatchImages != null) {
                    // Shown regardless of match/no-match -- lets you inspect
                    // the exact two aligned crops the comparison was decided
                    // from, useful for spotting alignment/orientation issues
                    // (e.g. a mirrored capture) even on a failed match.
                    DfsTextButton(
                        text = "View Compared Faces",
                        onClick = { showFaceCompareDialog = true }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val captureLabel = when {
                    mode == CaptureMode.FACE_MATCH -> "Start Face Verification"
                    else -> captureButtonLabel(mode)
                }
                DfsPrimaryButton(
                    text = captureLabel,
                    enabled = mode != CaptureMode.FACE_MATCH || idEmbeddingResult != null,
                    onClick = {
                        when {
                            mode == CaptureMode.FACE_MATCH -> {
                                scope.launch {
                                    try {
                                        if (idEmbeddingResult == null) {
                                            statusText = "No ID card on file — run \"Perform OCR\" first, then come back here to verify."
                                            return@launch
                                        }
                                        statusText = "Get ready..."
                                        delay(500)
                                        livenessChecker?.reset()
                                        liveEmbeddingResult = null
                                        statusText = "Blink naturally..."

                                        val deadline = System.currentTimeMillis() + 9000L
                                        while (liveEmbeddingResult == null &&
                                            livenessChecker?.timedOut != true &&
                                            System.currentTimeMillis() < deadline
                                        ) {
                                            delay(150)
                                        }

                                        val liveResult = liveEmbeddingResult
                                        val idResult = idEmbeddingResult
                                        if (liveResult == null || idResult == null) {
                                            statusText = "No blink detected — try again"
                                        } else {
                                            val match = matchFaces(idResult.embedding, liveResult.embedding)
                                            Log.d(TAG, "matchFaces: matched=${match.matched} confidencePercent=${match.confidencePercent}")
                                            statusText = if (match.matched) {
                                                "MATCH (%.1f%%)".format(match.confidencePercent)
                                            } else {
                                                "NO MATCH (%.1f%%)".format(match.confidencePercent)
                                            }
                                            faceMatchImages = matToBitmap(idResult.alignedFace) to
                                                matToBitmap(liveResult.alignedFace)
                                            if (match.matched) {
                                                showSuccessDialog = true
                                            }
                                        }
                                    } catch (e: Exception) {
                                        statusText = "Verification failed: ${e.message}"
                                    }
                                }
                            }
                            else -> {
                                captureImage(
                                    context = context,
                                    imageCapture = imageCapture,
                                    mode = mode,
                                    hand = hand,
                                    onSaved = { file ->
                                        statusText = if (isOnDeviceMode) "Processing..." else "Uploading..."
                                        scope.launch {
                                            try {
                                                when (mode) {
                                                    CaptureMode.ID_CARD -> {
                                                        val result = withContext(Dispatchers.Default) {
                                                            val mat = fileToMat(file)
                                                            idCardClassifier!!.classify(mat)
                                                        }
                                                        statusText = if (result.isIdCard) {
                                                            "ID Card detected (%.2f)".format(result.confidence)
                                                        } else {
                                                            "Not an ID card — try again"
                                                        }
                                                        if (result.isIdCard) showSuccessDialog = true
                                                    }
                                                    CaptureMode.OCR -> {
                                                        val result = withContext(Dispatchers.Default) {
                                                            val mat = fileToMat(file)
                                                            scanSingleImage(
                                                                idCardClassifier!!, { TesseractEngine(context) },
                                                                stillFaceLandmarker!!, mat
                                                            )
                                                        }
                                                        if (!result.ok) {
                                                            statusText = result.message
                                                            ocrIdPhoto = null
                                                        } else {
                                                            val found = result.fields.size
                                                            statusText = "${result.message} ($found/6)"
                                                            ocrResultText = result.fields.entries
                                                                .joinToString("\n\n") { (key, value) -> "$key:\n$value" }
                                                            ocrIdPhoto = result.idPhoto?.let { matToBitmap(it) }
                                                            // Saved for Face Matching to verify a live selfie
                                                            // against later, without re-capturing the card.
                                                            result.idPhoto?.let { photo ->
                                                                withContext(Dispatchers.Default) {
                                                                    idCardPhotoRepository.save(photo, System.currentTimeMillis())
                                                                }
                                                            }

                                                            // Show dialog for review even if some fields are missing
                                                            if (found > 0 || result.allFieldsFound) {
                                                                showSuccessDialog = true
                                                            }
                                                        }
                                                    }
                                                    CaptureMode.FINGERPRINT_ENROLL -> {
                                                        val result = withContext(Dispatchers.Default) {
                                                            val mat = fileToMat(file)
                                                            FingerprintEnrollmentService.enroll(
                                                                fingerprintRepository, personName, hand.name, mat
                                                            )
                                                        }
                                                        statusText = result.message
                                                        if (result.ok) showSuccessDialog = true
                                                    }
                                                    CaptureMode.FINGERPRINT_MATCH -> {
                                                        val result = withContext(Dispatchers.Default) {
                                                            val mat = fileToMat(file)
                                                            FingerprintVerificationService.verify(
                                                                fingerprintRepository, hand.name, mat
                                                            )
                                                        }
                                                        statusText = result.message
                                                        if (result.matched) showSuccessDialog = true
                                                    }
                                                    else -> {
                                                        statusText = "This capture mode isn't wired up yet"
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                statusText = if (isOnDeviceMode) {
                                                    "Processing failed: ${e.message}"
                                                } else {
                                                    "Upload failed: ${e.message}"
                                                }
                                            }
                                        }
                                    },
                                    onError = { msg -> statusText = "Capture failed: $msg" }
                                )
                            }
                        }
                    }
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission is required to continue.", color = DfsColors.MutedText)
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { /* force using the button */ },
            containerColor = DfsColors.SurfaceElevated,
            titleContentColor = DfsColors.OnBackground,
            textContentColor = DfsColors.MutedText,
            title = {
                Text(
                    text = if (mode == CaptureMode.OCR) "Review Details" else "SCAN SUCCESSFUL!",
                    fontWeight = FontWeight.Bold,
                    color = DfsColors.Success
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (mode == CaptureMode.OCR && ocrResultText.isNotEmpty()) {
                        Text("Please ensure the extracted details are correct before continuing:")
                        Spacer(modifier = Modifier.height(16.dp))
                        val photo = ocrIdPhoto
                        if (photo != null) {
                            Text("Face captured from ID card", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = DfsColors.OnBackground)
                            Spacer(modifier = Modifier.height(6.dp))
                            Image(
                                bitmap = photo.asImageBitmap(),
                                contentDescription = "Face captured from ID card",
                                modifier = Modifier.height(150.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text(text = ocrResultText, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = DfsColors.OnBackground)
                    } else if (mode == CaptureMode.FACE_MATCH) {
                        Text(text = statusText, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = DfsColors.OnBackground)
                        if (faceMatchImages != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            DfsTextButton(text = "View Match", onClick = { showFaceCompareDialog = true })
                        }
                    } else if (isFingerprintMode && statusText.isNotEmpty()) {
                        Text(text = statusText, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = DfsColors.OnBackground)
                    } else if (hand != HandSide.NONE) {
                        Text("${hand.label} captured successfully.", color = DfsColors.OnBackground)
                    } else {
                        Text("Capture completed successfully.", color = DfsColors.OnBackground)
                    }
                }
            },
            dismissButton = {
                if (mode == CaptureMode.OCR) {
                    TextButton(
                        onClick = {
                            showSuccessDialog = false
                            statusText = "Retrying capture..."
                        }
                    ) {
                        Text("Retake", color = DfsColors.Danger)
                    }
                }
            },
            confirmButton = {
                DfsPrimaryButton(
                    text = if (mode == CaptureMode.OCR) "Accept" else "Continue",
                    onClick = {
                        showSuccessDialog = false
                        onScanSuccess()
                    },
                    modifier = Modifier.fillMaxWidth(if (mode == CaptureMode.OCR) 0.5f else 1f)
                )
            }
        )
    }

    if (showFaceCompareDialog && faceMatchImages != null) {
        val (idBitmap, liveBitmap) = faceMatchImages!!
        AlertDialog(
            onDismissRequest = { showFaceCompareDialog = false },
            containerColor = DfsColors.SurfaceElevated,
            titleContentColor = DfsColors.OnBackground,
            textContentColor = DfsColors.MutedText,
            title = { Text("Face Match", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ID Photo", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = DfsColors.OnBackground)
                        Spacer(modifier = Modifier.height(6.dp))
                        Image(
                            bitmap = idBitmap.asImageBitmap(),
                            contentDescription = "ID card photo",
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Live Capture", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = DfsColors.OnBackground)
                        Spacer(modifier = Modifier.height(6.dp))
                        Image(
                            bitmap = liveBitmap.asImageBitmap(),
                            contentDescription = "Live captured face",
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            },
            confirmButton = {
                DfsPrimaryButton(
                    text = "Close",
                    onClick = { showFaceCompareDialog = false }
                )
            }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDShapeGuide(
    color: Color,
    strokeWidth: Float,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    val radius = min(width, height) / 2f
    val right = left + width
    val bottom = top + height
    val ovalLeft = right - radius * 2f

    val path = Path().apply {
        moveTo(left, top)
        lineTo(ovalLeft, top)
        arcTo(
            rect = Rect(ovalLeft, top, right, bottom),
            startAngleDegrees = -90f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false
        )
        lineTo(left, bottom)
        close()
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrosshairReticle(
    color: Color,
    center: Offset,
    radius: Float,
    strokeWidth: Float
) {
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth)
    )
    val tickLength = radius * 0.35f
    val gap = radius * 0.15f
    drawLine(color, Offset(center.x, center.y - radius - gap), Offset(center.x, center.y - radius - gap - tickLength), strokeWidth)
    drawLine(color, Offset(center.x, center.y + radius + gap), Offset(center.x, center.y + radius + gap + tickLength), strokeWidth)
    drawLine(color, Offset(center.x - radius * 0.4f, center.y), Offset(center.x + radius * 0.4f, center.y), strokeWidth)
    drawLine(color, Offset(center.x, center.y - radius * 0.4f), Offset(center.x, center.y + radius * 0.4f), strokeWidth)
}

private fun captureButtonLabel(mode: CaptureMode): String = when (mode) {
    CaptureMode.ID_CARD -> "Capture ID Card"
    CaptureMode.OCR -> "Capture for OCR"
    CaptureMode.FACE_MATCH -> "Capture Face"
    CaptureMode.FINGERPRINT_ENROLL -> "Capture Fingerprint (Enroll)"
    CaptureMode.FINGERPRINT_MATCH -> "Capture Fingerprint (Match)"
}

// Decodes a saved JPEG into an OpenCV BGR Mat for on-device processing. Must
// run off the main thread -- both the Bitmap decode and the OpenCV/TFLite/
// ONNX Runtime work that follows are CPU-bound.
private fun fileToMat(file: File): Mat {
    val bitmap = BitmapFactory.decodeFile(file.path)
        ?: throw IllegalStateException("Could not decode captured image")
    val upright = applyExifRotation(file, bitmap)
    val rgba = Mat()
    Utils.bitmapToMat(upright, rgba)
    val bgr = Mat()
    Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
    return bgr
}

// CameraX writes the JPEG's pixel data in raw sensor orientation and records
// how to display it upright via the EXIF orientation tag alone -- decodeFile()
// above ignores that tag entirely, so a phone photo taken in portrait (the
// normal way to hold it) decodes sideways. Every mode that does an
// orientation-sensitive crop (to_card_aspect's non-square aspect-ratio crop
// for ID_CARD/OCR, in particular) silently breaks without this.
private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
    val orientation = try {
        ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// Converts an aligned BGR Mat (e.g. a 112x112 face crop) to a Bitmap for the
// "View Match" comparison dialog.
private fun matToBitmap(bgr: Mat): Bitmap {
    val rgba = Mat()
    Imgproc.cvtColor(bgr, rgba, Imgproc.COLOR_BGR2RGBA)
    val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(rgba, bitmap)
    return bitmap
}

private fun captureImage(
    context: android.content.Context,
    imageCapture: ImageCapture?,
    mode: CaptureMode,
    hand: HandSide,
    onSaved: (File) -> Unit,
    onError: (String) -> Unit
) {
    val capture = imageCapture ?: run {
        onError("Camera not ready")
        return
    }

    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
    val outputDir = File(context.filesDir, "captures").apply { mkdirs() }
    val handSuffix = if (hand != HandSide.NONE) "_${hand.name}" else ""
    val outputFile = File(outputDir, "${mode.name}$handSuffix" + "_$timestamp.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSaved(outputFile)
            }
            override fun onError(exception: ImageCaptureException) {
                onError(exception.message ?: "unknown error")
            }
        }
    )
}
