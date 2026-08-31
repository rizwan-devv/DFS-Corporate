package com.example.kycapp.biometrics.fingerprint

import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mat/MatOfKeyPoint <-> ByteArray conversion for Room BLOB storage. Mirrors
 * mod_fingerprint.py's _serialize_keypoints/_deserialize_keypoints field
 * order: (x, y, size, angle, response, octave, class_id).
 */
data class MatBlob(val bytes: ByteArray, val rows: Int, val cols: Int, val type: Int)

private const val KEYPOINT_FIELD_COUNT = 7
private const val KEYPOINT_RECORD_BYTES = KEYPOINT_FIELD_COUNT * 4 // 5 floats + 2 ints, all 4 bytes

fun serializeMat(mat: Mat): MatBlob {
    val byteCount = (mat.total() * mat.elemSize()).toInt()
    val bytes = ByteArray(byteCount)
    if (byteCount > 0) mat.get(0, 0, bytes)
    return MatBlob(bytes, mat.rows(), mat.cols(), mat.type())
}

fun deserializeMat(blob: MatBlob): Mat {
    val mat = Mat(blob.rows, blob.cols, blob.type)
    if (blob.bytes.isNotEmpty()) mat.put(0, 0, blob.bytes)
    return mat
}

fun serializeKeypoints(keypoints: List<KeyPoint>): ByteArray {
    val buffer = ByteBuffer.allocate(keypoints.size * KEYPOINT_RECORD_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    for (kp in keypoints) {
        buffer.putFloat(kp.pt.x.toFloat())
        buffer.putFloat(kp.pt.y.toFloat())
        buffer.putFloat(kp.size)
        buffer.putFloat(kp.angle)
        buffer.putFloat(kp.response)
        buffer.putInt(kp.octave)
        buffer.putInt(kp.class_id)
    }
    return buffer.array()
}

fun deserializeKeypoints(bytes: ByteArray): MatOfKeyPoint {
    val count = bytes.size / KEYPOINT_RECORD_BYTES
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val keypoints = ArrayList<KeyPoint>(count)
    repeat(count) {
        val x = buffer.float
        val y = buffer.float
        val size = buffer.float
        val angle = buffer.float
        val response = buffer.float
        val octave = buffer.int
        val classId = buffer.int
        keypoints.add(KeyPoint(x, y, size, angle, response, octave, classId))
    }
    return MatOfKeyPoint().apply { fromList(keypoints) }
}
