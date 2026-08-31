package com.example.kycapp.biometrics.liveness

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

// Eye Aspect Ratio (EAR) landmark indices, following the Soukupová & Čech
// (2016) EAR method. MediaPipe's FaceLandmarker outputs 478 points: the
// first 468 keep the same indices/topology as the original 468-point face
// mesh, so these indices (ported from mod_liveness.py) still apply.
private val RIGHT_EYE_IDX = intArrayOf(33, 159, 158, 133, 153, 145)
private val LEFT_EYE_IDX = intArrayOf(362, 380, 374, 263, 386, 385)

const val EAR_BLINK_THRESHOLD = 0.21
const val BLINK_WINDOW_SEC = 8.0

// A real blink spans several video frames (~100-300ms at typical analysis
// frame rates); requiring the EAR to stay under threshold for at least this
// many consecutive frames before counting the eyes as "closed" filters out a
// single noisy/jittery landmark frame from being mistaken for a blink --
// without this, one bad frame right after reset() could flip [passed] to
// true almost instantly, capturing an embedding from an arbitrary frame the
// user wasn't ready for (and likely to mismatch the ID photo).
private const val MIN_CONSECUTIVE_CLOSED_FRAMES = 2

private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Double {
    val dx = (ax - bx).toDouble()
    val dy = (ay - by).toDouble()
    return sqrt(dx * dx + dy * dy)
}

private fun eyeAspectRatio(points: List<NormalizedLandmark>, idx: IntArray, width: Int, height: Int): Double {
    fun px(i: Int) = points[idx[i]].x() * width
    fun py(i: Int) = points[idx[i]].y() * height

    val verticalA = dist(px(1), py(1), px(5), py(5))
    val verticalB = dist(px(2), py(2), px(4), py(4))
    val horizontal = dist(px(0), py(0), px(3), py(3))
    return if (horizontal != 0.0) (verticalA + verticalB) / (2.0 * horizontal) else 0.0
}

fun meanEar(points: List<NormalizedLandmark>, width: Int, height: Int): Double =
    (eyeAspectRatio(points, RIGHT_EYE_IDX, width, height) + eyeAspectRatio(points, LEFT_EYE_IDX, width, height)) / 2.0

/**
 * Ports mod_liveness.py's BlinkLivenessCheck: confirms a live person -- not a
 * static printed photo or a frozen frame -- is in front of the camera by
 * requiring a genuine blink (EAR dipping below [EAR_BLINK_THRESHOLD] and then
 * recovering above it) within [windowSec]. A photo held up to the camera has
 * a constant EAR and can never produce that dip/recover pattern.
 *
 * Call [update] once per analyzed frame; check [passed].
 */
class BlinkLivenessChecker(
    private val windowSec: Double = BLINK_WINDOW_SEC,
    private val threshold: Double = EAR_BLINK_THRESHOLD
) {
    private var startTimeMs: Long = 0
    private var eyesWereClosed = false
    private var closedFrameStreak = 0

    var passed: Boolean = false
        private set
    var lastEar: Double? = null
        private set
    var faceSeen: Boolean = false
        private set

    init {
        reset()
    }

    fun reset() {
        startTimeMs = System.currentTimeMillis()
        eyesWereClosed = false
        closedFrameStreak = 0
        passed = false
        lastEar = null
        faceSeen = false
    }

    val elapsedSec: Double
        get() = (System.currentTimeMillis() - startTimeMs) / 1000.0

    val timedOut: Boolean
        get() = elapsedSec > windowSec

    /** [landmarks] null means no face was detected in this frame. */
    fun update(landmarks: List<NormalizedLandmark>?, width: Int, height: Int) {
        if (passed) return

        faceSeen = landmarks != null
        if (landmarks == null) {
            closedFrameStreak = 0
            return
        }

        val ear = meanEar(landmarks, width, height)
        lastEar = ear

        if (ear < threshold) {
            closedFrameStreak++
            if (closedFrameStreak >= MIN_CONSECUTIVE_CLOSED_FRAMES) eyesWereClosed = true
        } else {
            closedFrameStreak = 0
            if (eyesWereClosed) passed = true
        }
    }
}
