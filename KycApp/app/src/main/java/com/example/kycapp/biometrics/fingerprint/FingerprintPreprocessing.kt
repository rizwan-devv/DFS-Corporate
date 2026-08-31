package com.example.kycapp.biometrics.fingerprint

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI

/**
 * Grayscale -> CLAHE -> 8-orientation Gabor ridge-enhancement bank ->
 * Gaussian blur. Ports mod_fingerprint.py's preprocess_fingerprint().
 *
 * Note: the Python module's legacy CLAHE-only pipeline (kept there only to
 * keep matching against pre-Gabor pickle templates) is intentionally not
 * ported -- this is a brand-new on-device database with no such legacy
 * templates, so it would be dead weight.
 */
private const val GABOR_ORIENTATIONS = 8
private const val GABOR_KSIZE = 15
private const val GABOR_SIGMA = 3.0
private const val GABOR_LAMBDA = 8.0
private const val GABOR_GAMMA = 0.5

private val gaborKernels: List<Mat> by lazy {
    (0 until GABOR_ORIENTATIONS).map { i ->
        val theta = i * PI / GABOR_ORIENTATIONS
        Imgproc.getGaborKernel(
            Size(GABOR_KSIZE.toDouble(), GABOR_KSIZE.toDouble()),
            GABOR_SIGMA, theta, GABOR_LAMBDA, GABOR_GAMMA, 0.0, CvType.CV_32F
        )
    }
}

private fun gaborEnhance(gray: Mat): Mat {
    val grayF = Mat()
    gray.convertTo(grayF, CvType.CV_32F)

    val acc = Mat.zeros(gray.size(), CvType.CV_32F)
    val resp = Mat()
    val absResp = Mat()
    for (kernel in gaborKernels) {
        Imgproc.filter2D(grayF, resp, CvType.CV_32F, kernel)
        Core.absdiff(resp, Scalar(0.0), absResp)
        Core.max(acc, absResp, acc)
    }

    Core.normalize(acc, acc, 0.0, 255.0, Core.NORM_MINMAX)
    val enhanced = Mat()
    acc.convertTo(enhanced, CvType.CV_8U)
    return enhanced
}

fun preprocessFingerprint(bgr: Mat): Mat {
    val gray = Mat()
    Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)

    val equalized = Mat()
    Imgproc.createCLAHE(3.0, Size(8.0, 8.0)).apply(gray, equalized)

    val enhanced = gaborEnhance(equalized)

    val blurred = Mat()
    Imgproc.GaussianBlur(enhanced, blurred, Size(3.0, 3.0), 0.0)
    return blurred
}
