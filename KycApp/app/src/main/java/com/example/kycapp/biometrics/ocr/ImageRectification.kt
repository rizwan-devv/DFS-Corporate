package com.example.kycapp.biometrics.ocr

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

// Real CNIC card ratio (85.6mm x 54mm) -- ports mod_ocr.py's _perspective_warp.
private const val CNIC_ASPECT = 85.6 / 54.0
private const val ASPECT_TOLERANCE = 0.25

private fun orderCorners(pts: Array<Point>): Array<Point> {
    val sums = pts.map { it.x + it.y }
    val diffs = pts.map { it.y - it.x }
    val topLeft = pts[sums.indices.minByOrNull { sums[it] }!!]
    val topRight = pts[diffs.indices.minByOrNull { diffs[it] }!!]
    val bottomRight = pts[sums.indices.maxByOrNull { sums[it] }!!]
    val bottomLeft = pts[diffs.indices.maxByOrNull { diffs[it] }!!]
    return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
}

/** Returns (image, rectified). rectified=false means no card-shaped quad was found. */
fun perspectiveWarp(roi: Mat): Pair<Mat, Boolean> {
    val gray = Mat()
    Imgproc.cvtColor(roi, gray, Imgproc.COLOR_BGR2GRAY)
    val blur = Mat()
    Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
    val edged = Mat()
    Imgproc.Canny(blur, edged, 30.0, 120.0)
    val kernel = Mat.ones(Size(3.0, 3.0), CvType.CV_8U)
    Imgproc.dilate(edged, edged, kernel)

    val contours = mutableListOf<MatOfPoint>()
    Imgproc.findContours(edged, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
    if (contours.isEmpty()) return roi to false

    val roiArea = roi.rows().toDouble() * roi.cols().toDouble()
    for (cnt in contours.sortedByDescending { Imgproc.contourArea(it) }) {
        val cnt2f = MatOfPoint2f(*cnt.toArray())
        val peri = Imgproc.arcLength(cnt2f, true)
        val approx2f = MatOfPoint2f()
        Imgproc.approxPolyDP(cnt2f, approx2f, 0.02 * peri, true)
        val approxPoints = approx2f.toArray()
        if (approxPoints.size != 4) continue

        val approxMat = MatOfPoint(*approxPoints)
        if (Imgproc.contourArea(approxMat) < 0.55 * roiArea) continue

        val ordered = orderCorners(approxPoints)
        val (tl, tr, br, bl) = ordered
        val avgW = (hypot(tr.x - tl.x, tr.y - tl.y) + hypot(br.x - bl.x, br.y - bl.y)) / 2.0
        val avgH = (hypot(bl.x - tl.x, bl.y - tl.y) + hypot(br.x - tr.x, br.y - tr.y)) / 2.0
        if (avgH == 0.0 || abs(avgW / avgH - CNIC_ASPECT) > ASPECT_TOLERANCE) continue

        val h = roi.rows(); val w = roi.cols()
        val src = MatOfPoint2f(tl, tr, br, bl)
        val dst = MatOfPoint2f(Point(0.0, 0.0), Point(w.toDouble(), 0.0), Point(w.toDouble(), h.toDouble()), Point(0.0, h.toDouble()))
        val transform = Imgproc.getPerspectiveTransform(src, dst)
        val warped = Mat()
        Imgproc.warpPerspective(roi, warped, transform, Size(w.toDouble(), h.toDouble()))
        return warped to true
    }
    return roi to false
}

// Ports mod_ocr.py's _deskew: rotate so text rows sit horizontal, no-op when unsure.
private const val DESKEW_MAX_DEG = 12.0
private const val DESKEW_MIN_DEG = 0.4

fun deskew(img: Mat): Mat {
    val gray = Mat()
    Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY)
    val edges = Mat()
    Imgproc.Canny(gray, edges, 60.0, 180.0)
    val lines = Mat()
    Imgproc.HoughLinesP(
        edges, lines, 1.0, Math.PI / 360, 60,
        maxOf(30.0, gray.cols() / 6.0), 12.0
    )
    if (lines.empty()) return img

    val angles = mutableListOf<Double>()
    for (i in 0 until lines.rows()) {
        val v = lines.get(i, 0)
        val x1 = v[0]; val y1 = v[1]; val x2 = v[2]; val y2 = v[3]
        val angle = Math.toDegrees(atan2(y2 - y1, x2 - x1))
        if (abs(angle) <= DESKEW_MAX_DEG) angles.add(angle)
    }
    if (angles.size < 4) return img

    val sorted = angles.sorted()
    val mid = sorted.size / 2
    val angle = if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    if (abs(angle) < DESKEW_MIN_DEG) return img

    val h = img.rows(); val w = img.cols()
    val rotMatrix = Imgproc.getRotationMatrix2D(Point(w / 2.0, h / 2.0), angle, 1.0)
    val rotated = Mat()
    Imgproc.warpAffine(
        img, rotated, rotMatrix, Size(w.toDouble(), h.toDouble()),
        Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE
    )
    return rotated
}

// Ports mod_ocr.py's _warp_and_upscale.
private const val UPSCALE_TARGET_W = 1800.0
private const val UPSCALE_MIN = 2.0
private const val UPSCALE_MAX = 5.0
private const val DOWNSCALE_MIN = 0.5

fun warpAndUpscale(roiIn: Mat): Mat {
    val (warped, rectified) = perspectiveWarp(roiIn)
    val roi = if (rectified) warped else deskew(roiIn)

    val h = roi.rows(); val w = roi.cols()
    val rawScale = UPSCALE_TARGET_W / w.toDouble()
    val scale = if (w < UPSCALE_TARGET_W) {
        rawScale.coerceIn(UPSCALE_MIN, UPSCALE_MAX)
    } else {
        maxOf(DOWNSCALE_MIN, rawScale)
    }
    val interp = if (scale >= 1.0) Imgproc.INTER_LANCZOS4 else Imgproc.INTER_AREA
    val resized = Mat()
    Imgproc.resize(roi, resized, Size(w * scale, h * scale), 0.0, 0.0, interp)
    return resized
}
