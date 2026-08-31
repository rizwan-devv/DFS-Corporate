package com.example.kycapp.biometrics.fingerprint

import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Crops exactly the region framed by the D-shaped on-screen capture guide --
 * matches CameraCaptureScreen.kt's guide box fractions (boxLeft/boxTop/
 * boxWidth/boxHeight: 2%/14%/92%/62% of the camera view) applied to the
 * captured frame's own width/height, so the area that gets cropped, matched,
 * and stored is exactly what the user saw framed during capture -- not an
 * unrelated arbitrary crop. Kept in sync manually with the guide's constants
 * since the guide is drawn in Compose (view-side) and this runs on the
 * captured Mat (image-side); if the guide box ever moves, update both.
 */
private const val GUIDE_LEFT_FRACTION = 0.02
private const val GUIDE_TOP_FRACTION = 0.14
private const val GUIDE_WIDTH_FRACTION = 0.92
private const val GUIDE_HEIGHT_FRACTION = 0.62

// Canonical width matching operates on; height is derived to preserve the
// guide box's own aspect ratio rather than forcing a square and distorting it.
private const val CANONICAL_WIDTH = 460

fun fingerprintRoi(mat: Mat): Rect {
    val w = mat.cols()
    val h = mat.rows()
    val x1 = (w * GUIDE_LEFT_FRACTION).toInt()
    val y1 = (h * GUIDE_TOP_FRACTION).toInt()
    val boxWidth = (w * GUIDE_WIDTH_FRACTION).toInt()
    val boxHeight = (h * GUIDE_HEIGHT_FRACTION).toInt()
    return Rect(x1, y1, boxWidth, boxHeight)
}

/**
 * The guide-box crop at its native captured resolution, before any resize --
 * used when the full captured detail needs to be preserved (e.g. the
 * WSQ/Base64/ISO export records), as opposed to [cropRoi]'s canonical size
 * used for feature-matching consistency.
 */
fun cropRoiRaw(mat: Mat): Mat? {
    val roi = fingerprintRoi(mat)
    if (roi.width <= 0 || roi.height <= 0) return null
    return Mat(mat, roi)
}

fun cropRoi(mat: Mat): Mat? {
    val cropped = cropRoiRaw(mat) ?: return null
    if (cropped.cols() == CANONICAL_WIDTH) return cropped

    val targetHeight = (CANONICAL_WIDTH.toDouble() * cropped.rows() / cropped.cols())
    val resized = Mat()
    val interp = if (cropped.cols() > CANONICAL_WIDTH) Imgproc.INTER_AREA else Imgproc.INTER_CUBIC
    Imgproc.resize(cropped, resized, Size(CANONICAL_WIDTH.toDouble(), targetHeight), 0.0, 0.0, interp)
    return resized
}
