package com.example.kycapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kycapp.dfs.DfsApiClient
import com.example.kycapp.dfs.DfsApiException
import com.example.kycapp.dfs.DfsSession
import com.example.kycapp.ui.components.DfsPrimaryButton
import com.example.kycapp.ui.components.DfsScreen
import com.example.kycapp.ui.components.DfsSecondaryButton
import com.example.kycapp.ui.theme.DfsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * End-of-KYC signature capture: open camera, photograph the handwritten signature,
 * upload one image (backoffice shows it as four thumbnails).
 */
@Composable
fun SignatureCaptureScreen(
    session: DfsSession,
    onDone: (DfsSession) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Frame your signature, then capture.") }
    var uploading by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("SignatureCapture", "Camera bind failed", e)
                    status = "Camera failed: ${e.message}"
                }
            }, executor)
            onDispose {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    DfsScreen {
        Text(
            text = "Signature capture",
            color = DfsColors.OnBackground,
            fontSize = 22.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = status,
            color = DfsColors.MutedText,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        ) {
            if (hasPermission) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Camera permission required",
                    color = DfsColors.Danger,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        DfsPrimaryButton(
            text = if (uploading) "Uploading…" else "Capture & upload signature",
            enabled = hasPermission && !uploading,
            onClick = {
                uploading = true
                status = "Capturing…"
                val out = File(context.cacheDir, "signature_${System.currentTimeMillis()}.jpg")
                val opts = ImageCapture.OutputFileOptions.Builder(out).build()
                imageCapture.takePicture(
                    opts,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            scope.launch {
                                try {
                                    val updated = withContext(Dispatchers.IO) {
                                        DfsApiClient.uploadSignature(session.sessionToken, out)
                                    }
                                    status = "Signature uploaded."
                                    onDone(updated)
                                } catch (e: Exception) {
                                    status = (e as? DfsApiException)?.message
                                        ?: (e.message ?: "Upload failed")
                                } finally {
                                    uploading = false
                                    out.delete()
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            uploading = false
                            status = "Capture failed: ${exception.message}"
                        }
                    }
                )
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        DfsSecondaryButton(text = "Back", onClick = onBack)
        Spacer(modifier = Modifier.padding(bottom = 8.dp))
    }
}
