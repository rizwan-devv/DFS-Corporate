package com.example.kycapp.biometrics.fingerprint

import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.DMatch
import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfPoint2f
import org.opencv.features2d.DescriptorMatcher

/**
 * Ports mod_fingerprint.py's _ratio_match / _ransac_inlier_ratio /
 * _one_way_score / _symmetric_score / _match_score. Constants kept
 * identical to the Python module so scores/threshold behave the same.
 *
 * Legacy CLAHE-only ORB fallback (_match_score's orb_score_leg branch) is
 * dropped -- see FingerprintPreprocessing.kt for why.
 */
private const val RATIO_TEST_THRESHOLD = 0.70
private const val MIN_GOOD_MATCHES_ORB = 8
private const val MIN_GOOD_MATCHES_AKAZE = 6
const val VERIFY_THRESHOLD = 0.20
private const val ORB_WEIGHT = 0.45
private const val AKAZE_WEIGHT = 0.55

data class FingerprintTemplate(
    val orbKeypoints: List<KeyPoint>,
    val orbDescriptors: Mat?,
    val akazeKeypoints: List<KeyPoint>,
    val akazeDescriptors: Mat?
)

private fun ratioMatch(desA: Mat?, desB: Mat?, minGood: Int): List<DMatch> {
    if (desA == null || desB == null) return emptyList()
    if (desA.rows() < 2 || desB.rows() < 2) return emptyList()

    val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
    val knnMatches = ArrayList<MatOfDMatch>()
    matcher.knnMatch(desA, desB, knnMatches, 2)

    val good = ArrayList<DMatch>()
    for (pair in knnMatches) {
        val arr = pair.toArray()
        if (arr.size == 2 && arr[0].distance < RATIO_TEST_THRESHOLD * arr[1].distance) {
            good.add(arr[0])
        }
    }
    return if (good.size >= minGood) good else emptyList()
}

private fun ransacInlierRatio(kpA: List<KeyPoint>, kpB: List<KeyPoint>, good: List<DMatch>): Double {
    if (good.size < 4) return 0.0
    val srcPts = good.map { kpA[it.queryIdx].pt }.toTypedArray()
    val dstPts = good.map { kpB[it.trainIdx].pt }.toTypedArray()
    val src = MatOfPoint2f(*srcPts)
    val dst = MatOfPoint2f(*dstPts)
    val mask = Mat()
    Calib3d.findHomography(src, dst, Calib3d.RANSAC, 5.0, mask)
    if (mask.empty()) return 0.0
    val inliers = Core.sumElems(mask).`val`[0]
    return inliers / good.size
}

private fun oneWayScore(
    desA: Mat?, kpA: List<KeyPoint>,
    desB: Mat?, kpB: List<KeyPoint>,
    minGood: Int
): Double {
    val good = ratioMatch(desA, desB, minGood)
    if (good.isEmpty()) return 0.0
    val inlierRatio = ransacInlierRatio(kpA, kpB, good)
    val strength = minOf(1.0, good.size * inlierRatio / maxOf(minGood, 1))
    return inlierRatio * strength
}

private fun symmetricScore(
    desA: Mat?, kpA: List<KeyPoint>,
    desB: Mat?, kpB: List<KeyPoint>,
    minGood: Int
): Double {
    val sAtoB = oneWayScore(desA, kpA, desB, kpB, minGood)
    val sBtoA = oneWayScore(desB, kpB, desA, kpA, minGood)
    return (sAtoB + sBtoA) / 2.0
}

fun matchScore(a: FingerprintTemplate, b: FingerprintTemplate): Double {
    val orbScore = symmetricScore(a.orbDescriptors, a.orbKeypoints, b.orbDescriptors, b.orbKeypoints, MIN_GOOD_MATCHES_ORB)

    return if (a.akazeDescriptors != null && b.akazeDescriptors != null) {
        val akazeScore = symmetricScore(a.akazeDescriptors, a.akazeKeypoints, b.akazeDescriptors, b.akazeKeypoints, MIN_GOOD_MATCHES_AKAZE)
        ORB_WEIGHT * orbScore + AKAZE_WEIGHT * akazeScore
    } else {
        orbScore
    }
}
