package com.example.kycapp.biometrics.face

import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

private const val TAG = "FaceEmbedExtractor"

data class FaceEmbeddingResult(val embedding: FloatArray, val alignedFace: Mat)

/**
 * Runs face detection (via a still-image-mode MediaPipe FaceLandmarker) +
 * alignment + embedding on a single BGR frame. Used for both the ID-card
 * photo step and the live-verify step of on-device face matching. Returns
 * null when no face is found -- mirrors mod_ocr.py's _extract_id_photo()
 * and mod_face.py's id_photo_embedding() both returning None/no-face.
 */
fun extractFaceEmbedding(
    landmarker: FaceLandmarker,
    embedder: FaceEmbedder,
    bgr: Mat
): FaceEmbeddingResult? {
    Log.d(TAG, "extractFaceEmbedding: input bgr ${bgr.cols()}x${bgr.rows()}")
    val rgba = Mat()
    Imgproc.cvtColor(bgr, rgba, Imgproc.COLOR_BGR2RGBA)
    val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(rgba, bitmap)

    val mpImage = BitmapImageBuilder(bitmap).build()
    val result = landmarker.detect(mpImage)
    Log.d(TAG, "detect() returned ${result.faceLandmarks().size} face(s)")
    val landmarks = result.faceLandmarks().firstOrNull() ?: run {
        Log.w(TAG, "No face landmarks found in ${bgr.cols()}x${bgr.rows()} image")
        return null
    }

    val points = extractFivePoints(landmarks, bgr.cols(), bgr.rows())
    val aligned = alignFace(bgr, points)
    val embedding = embedder.embed(aligned)
    Log.d(TAG, "extractFaceEmbedding: success, aligned=${aligned.cols()}x${aligned.rows()}")
    return FaceEmbeddingResult(embedding, aligned)
}
