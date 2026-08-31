package com.example.kycapp.biometrics.ocr

import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

private const val TAG = "CnicIdPhotoExtractor"
private const val FACE_PADDING_RATIO = 0.3

/**
 * Generous candidate regions of the perspective-corrected card to search, in
 * order -- narrowing the search area first helps MediaPipe's face detector a
 * lot, since it's far more reliable when the face fills a meaningful
 * fraction of the input frame than when hunting for a small face somewhere
 * inside a huge (e.g. 1800x2400) full-card image, which is what made
 * detection on the full card unreliable. Each fraction is (left, top, width,
 * height); every region is generous enough to contain the printed photo on
 * essentially any CNIC-style layout, front-facing or mirrored. The full card
 * is tried last as a final fallback so an unusual layout still gets a shot.
 *
 * The first ("tight") region is sized/positioned from where successful
 * detections have actually landed the photo on real captures -- a smaller,
 * more face-proportionate window than the broad quadrants below it, so the
 * detector's easiest, highest-success-rate shot goes first.
 */
private val CANDIDATE_REGIONS = listOf(
    doubleArrayOf(0.55, 0.28, 0.42, 0.40), // tight right-middle window (empirically where the photo lands)
    doubleArrayOf(0.5, 0.0, 0.5, 0.65),    // top-right quadrant
    doubleArrayOf(0.0, 0.0, 0.5, 0.65),    // top-left quadrant
    doubleArrayOf(0.0, 0.0, 1.0, 1.0)      // full card, last resort
)

// A tight region's own detection failure is retried once, upscaled -- cheap
// (it's already the smallest candidate), and MediaPipe's detector sometimes
// misses a face at native scale but catches it once it's a bit larger.
private const val TIGHT_REGION_INDEX = 0
private const val TIGHT_REGION_RETRY_SCALE = 1.6

/**
 * Detects+crops the CNIC's printed photo out of the (perspective-warped,
 * upscaled) color card image -- ports mod_ocr.py's _extract_id_photo().
 * Face *detection* here comes from MediaPipe's landmark extent (min/max of
 * all mesh points) rather than InsightFace/RetinaFace's own detector box,
 * same substitution already made for phase 2's face-match alignment --
 * returns null if no face is found in any candidate region.
 */
fun extractIdPhoto(landmarker: FaceLandmarker, colorUpscaled: Mat): Mat? {
    val w = colorUpscaled.cols()
    val h = colorUpscaled.rows()
    for ((index, fractions) in CANDIDATE_REGIONS.withIndex()) {
        val (leftF, topF, widthF, heightF) = fractions
        val rect = Rect((w * leftF).toInt(), (h * topF).toInt(), (w * widthF).toInt(), (h * heightF).toInt())
        val region = Mat(colorUpscaled, rect)
        val cropped = detectAndCropPhoto(landmarker, region, "region#$index ${rect.width}x${rect.height}")
        if (cropped != null) return cropped

        if (index == TIGHT_REGION_INDEX) {
            val upscaled = Mat()
            Imgproc.resize(
                region, upscaled,
                Size(region.cols() * TIGHT_REGION_RETRY_SCALE, region.rows() * TIGHT_REGION_RETRY_SCALE),
                0.0, 0.0, Imgproc.INTER_CUBIC
            )
            val retryCrop = detectAndCropPhoto(landmarker, upscaled, "region#$index upscaled ${upscaled.cols()}x${upscaled.rows()}")
            if (retryCrop != null) return retryCrop
        }
    }
    Log.w(TAG, "extractIdPhoto: no face found in any candidate region")
    return null
}

private operator fun DoubleArray.component1() = this[0]
private operator fun DoubleArray.component2() = this[1]
private operator fun DoubleArray.component3() = this[2]
private operator fun DoubleArray.component4() = this[3]

private fun detectAndCropPhoto(landmarker: FaceLandmarker, region: Mat, label: String): Mat? {
    val bitmap = matToBitmap(region)
    val mpImage = BitmapImageBuilder(bitmap).build()
    val result = landmarker.detect(mpImage)
    Log.d(TAG, "detectAndCropPhoto: $label -> ${result.faceLandmarks().size} face(s)")
    val landmarks = result.faceLandmarks().firstOrNull() ?: return null

    val w = region.cols()
    val h = region.rows()
    val xs = landmarks.map { it.x() * w }
    val ys = landmarks.map { it.y() * h }
    val x1 = xs.min(); val x2 = xs.max()
    val y1 = ys.min(); val y2 = ys.max()
    val padX = ((x2 - x1) * FACE_PADDING_RATIO).toInt()
    val padY = ((y2 - y1) * FACE_PADDING_RATIO).toInt()
    val x1p = maxOf(0, (x1 - padX).toInt())
    val y1p = maxOf(0, (y1 - padY).toInt())
    val x2p = minOf(w, (x2 + padX).toInt())
    val y2p = minOf(h, (y2 + padY).toInt())
    if (x2p <= x1p || y2p <= y1p) {
        Log.w(TAG, "detectAndCropPhoto: $label degenerate crop rect ($x1p,$y1p)-($x2p,$y2p)")
        return null
    }
    Log.d(TAG, "detectAndCropPhoto: $label cropped photo ${x2p - x1p}x${y2p - y1p} at ($x1p,$y1p)")
    return Mat(region, Rect(x1p, y1p, x2p - x1p, y2p - y1p))
}
