package com.example.kycapp.biometrics.liveness

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Feeds live CameraX frames into a FaceLandmarker (via [detectAsync], wired
 * to the landmarker's detectAsync() call by the caller) for blink-liveness
 * checking. LIVE_STREAM mode requires no overlapping detectAsync() calls, so
 * a busy flag guards against a new frame being submitted before the previous
 * one's result has been processed -- call [notifyResultProcessed] from the
 * FaceLandmarker's result listener once it has consumed a result.
 */
class BlinkLivenessAnalyzer(
    private val checker: BlinkLivenessChecker,
    private val detectAsync: (bitmap: Bitmap, timestampMs: Long) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    private var busy = false

    fun notifyResultProcessed() {
        busy = false
    }

    override fun analyze(image: ImageProxy) {
        if (busy || checker.passed || checker.timedOut) {
            image.close()
            return
        }
        busy = true
        try {
            val bitmap = image.toRotatedBitmap()
            detectAsync(bitmap, SystemClock.uptimeMillis())
        } catch (e: Exception) {
            busy = false
        } finally {
            image.close()
        }
    }
}

// YUV_420_888 -> NV21 -> JPEG -> Bitmap, then rotated upright per the sensor's
// reported rotation -- mirrors CameraCaptureScreen's toFaceJpegBytes() plane
// handling (explicit row/pixel strides, since stride padding is common on
// real devices), plus the rotation correction MediaPipe needs for an
// upright face.
private fun ImageProxy.toRotatedBitmap(): Bitmap {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val nv21 = ByteArray(width * height + width * height / 2)
    var pos = 0

    val yBuffer = yPlane.buffer
    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    for (row in 0 until height) {
        val rowStart = row * yRowStride
        for (col in 0 until width) {
            nv21[pos++] = yBuffer.get(rowStart + col * yPixelStride)
        }
    }

    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    for (row in 0 until chromaHeight) {
        val uRowStart = row * uRowStride
        val vRowStart = row * vRowStride
        for (col in 0 until chromaWidth) {
            nv21[pos++] = vBuffer.get(vRowStart + col * vPixelStride)
            nv21[pos++] = uBuffer.get(uRowStart + col * uPixelStride)
        }
    }

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 85, out)
    val bytes = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
