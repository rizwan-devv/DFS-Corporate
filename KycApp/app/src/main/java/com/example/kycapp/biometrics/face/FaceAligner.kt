package com.example.kycapp.biometrics.face

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.opencv.calib3d.Calib3d
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Face alignment to InsightFace/ArcFace's standard 112x112 template, ported
 * from insightface.utils.face_align.norm_crop()/estimate_norm(). The Python
 * side gets its 5 landmarks (2 eyes, nose, 2 mouth corners) from RetinaFace;
 * we derive the same 5 points from MediaPipe FaceLandmarker's 478-point mesh
 * instead (already integrated for blink liveness), then fit the identical
 * similarity-transform template via OpenCV rather than porting RetinaFace's
 * detection decoding.
 */
private const val ALIGNED_SIZE = 112.0

// Landmark indices from the classic 468-point mesh topology (MediaPipe's 478
// points keep the same first-468 indices). Eye "centers" are the midpoint of
// each eye's two horizontal corner landmarks (reusing the same corner
// indices as the blink-liveness EAR calculation); nose tip and mouth corners
// are single stable landmarks widely used across MediaPipe face-alignment
// implementations.
private const val EYE_A_OUTER = 33
private const val EYE_A_INNER = 133
private const val EYE_B_OUTER = 362
private const val EYE_B_INNER = 263
private const val NOSE_TIP = 1
private const val MOUTH_CORNER_A = 61
private const val MOUTH_CORNER_B = 291

// insightface.utils.face_align.arcface_dst -- the standard 5-point template
// for a 112x112 aligned crop: [left eye, right eye, nose, left mouth, right
// mouth], "left"/"right" meaning image-left/image-right (smaller/larger x),
// confirmed empirically against InsightFace's own RetinaFace keypoint output.
private val ARCFACE_TEMPLATE = arrayOf(
    Point(38.2946, 51.6963),
    Point(73.5318, 51.5014),
    Point(56.0252, 71.7366),
    Point(41.5493, 92.3655),
    Point(70.7299, 92.2041)
)

private fun midpoint(a: Point, b: Point) = Point((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)

/**
 * Extracts the 5 alignment points in pixel space, ordered [leftEye,
 * rightEye, nose, leftMouth, rightMouth] by image-x (not assuming which
 * MediaPipe landmark group is anatomically which side).
 */
fun extractFivePoints(landmarks: List<NormalizedLandmark>, width: Int, height: Int): Array<Point> {
    fun px(index: Int) = Point(
        (landmarks[index].x() * width).toDouble(),
        (landmarks[index].y() * height).toDouble()
    )

    val eyeA = midpoint(px(EYE_A_OUTER), px(EYE_A_INNER))
    val eyeB = midpoint(px(EYE_B_OUTER), px(EYE_B_INNER))
    val nose = px(NOSE_TIP)
    val mouthA = px(MOUTH_CORNER_A)
    val mouthB = px(MOUTH_CORNER_B)

    val (leftEye, rightEye) = if (eyeA.x <= eyeB.x) eyeA to eyeB else eyeB to eyeA
    val (leftMouth, rightMouth) = if (mouthA.x <= mouthB.x) mouthA to mouthB else mouthB to mouthA

    return arrayOf(leftEye, rightEye, nose, leftMouth, rightMouth)
}

/**
 * Fits a similarity transform (uniform scale + rotation + translation, no
 * shear) from [points] to the ArcFace template and warps [bgr] to a 112x112
 * aligned crop -- mirrors estimate_norm()+norm_crop()'s cv2.warpAffine call.
 */
fun alignFace(bgr: Mat, points: Array<Point>): Mat {
    val src = MatOfPoint2f(*points)
    val dst = MatOfPoint2f(*ARCFACE_TEMPLATE)
    val transform = Calib3d.estimateAffinePartial2D(src, dst)
    val aligned = Mat()
    Imgproc.warpAffine(bgr, aligned, transform, Size(ALIGNED_SIZE, ALIGNED_SIZE))
    return aligned
}
