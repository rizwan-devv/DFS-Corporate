package com.example.kycapp.biometrics.fingerprint

import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.AKAZE
import org.opencv.features2d.ORB

// Params copied 1:1 from mod_fingerprint.py's _make_orb()/_make_akaze().
private const val ORB_FEATURES = 800
private const val AKAZE_THRESHOLD = 0.0008f

private fun makeOrb(): ORB = ORB.create(
    ORB_FEATURES, 1.2f, 8, 15, 0, 2, ORB.HARRIS_SCORE, 31, 10
)

private fun makeAkaze(): AKAZE = AKAZE.create(
    AKAZE.DESCRIPTOR_MLDB, 0, 3, AKAZE_THRESHOLD, 4, 4
)

data class FeatureSet(
    val kpOrb: MatOfKeyPoint,
    val desOrb: Mat,
    val kpAkaze: MatOfKeyPoint,
    val desAkaze: Mat,
    val processed: Mat
)

fun extractFeatures(bgr: Mat): FeatureSet {
    val processed = preprocessFingerprint(bgr)
    val emptyMask = Mat()

    val kpOrb = MatOfKeyPoint()
    val desOrb = Mat()
    makeOrb().detectAndCompute(processed, emptyMask, kpOrb, desOrb)

    val kpAkaze = MatOfKeyPoint()
    val desAkaze = Mat()
    makeAkaze().detectAndCompute(processed, emptyMask, kpAkaze, desAkaze)

    return FeatureSet(kpOrb, desOrb, kpAkaze, desAkaze, processed)
}
