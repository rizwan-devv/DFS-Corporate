package com.example.kycapp.biometrics.liveness

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

private const val MODEL_ASSET_PATH = "face_landmarker.task"

/**
 * Builds a FaceLandmarker in LIVE_STREAM mode, the mode designed for a
 * continuous camera feed (vs. Python's synchronous IMAGE mode). Results
 * arrive asynchronously on [onResult] after detectAsync() is called with a
 * strictly increasing timestamp per frame.
 */
object FaceLandmarkerProvider {
    fun create(
        context: Context,
        onResult: (FaceLandmarkerResult, MPImage) -> Unit,
        onError: (RuntimeException) -> Unit = {}
    ): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setResultListener(onResult)
            .setErrorListener(onError)
            .build()
        return FaceLandmarker.createFromOptions(context, options)
    }

    /**
     * Synchronous IMAGE-mode landmarker for one-off still photos (e.g. the
     * printed photo on a captured ID card) -- LIVE_STREAM instances can only
     * be driven via detectAsync() with monotonically increasing timestamps,
     * which doesn't fit a single independent still capture.
     *
     * Confidence thresholds are lowered from MediaPipe's defaults (~0.5) --
     * a CNIC's printed photo is a harder detection target than a live selfie
     * (security-pattern background bleeding into the photo, lamination
     * glare, print/scan quality loss), so the default threshold was
     * rejecting real, valid printed photos outright.
     */
    fun createForStillImages(context: Context): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.3f)
            .setMinFacePresenceConfidence(0.3f)
            .build()
        return FaceLandmarker.createFromOptions(context, options)
    }
}
