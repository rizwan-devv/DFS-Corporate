package com.example.kycapp.biometrics.fingerprint

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

// Ports mod_fingerprint.py's enroll quality-gate constants, unchanged.
const val MIN_ENROLL_FEATURES = 20
const val ENROLL_SHARPNESS_MIN = 50.0
const val ENROLL_DUPLICATE_THRESHOLD = 0.85
const val MAX_ENROLL_SAMPLES = 3

private const val QUALITY_FAIR = 300
private const val QUALITY_GOOD = 500

enum class QualityTier(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair — try pressing finger more firmly"),
    POOR("Poor — not enough detail")
}

fun qualityTier(orbCount: Int): QualityTier = when {
    orbCount >= QUALITY_GOOD -> QualityTier.EXCELLENT
    orbCount >= QUALITY_FAIR -> QualityTier.GOOD
    orbCount >= MIN_ENROLL_FEATURES -> QualityTier.FAIR
    else -> QualityTier.POOR
}

/** Laplacian variance of a grayscale image -- a fast, reliable blur metric. */
fun sharpness(gray: Mat): Double {
    val laplacian = Mat()
    Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
    val mean = MatOfDouble()
    val stddev = MatOfDouble()
    org.opencv.core.Core.meanStdDev(laplacian, mean, stddev)
    val std = stddev.toArray()[0]
    return std * std
}
