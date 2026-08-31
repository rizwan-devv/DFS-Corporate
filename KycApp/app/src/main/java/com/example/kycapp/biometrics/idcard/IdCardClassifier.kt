package com.example.kycapp.biometrics.idcard

import android.content.Context
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val MODEL_ASSET = "id_card_classifier.tflite"
private const val IMAGE_SIZE = 128

// The classifier was trained only on center_roi(frame)'s fixed ROI_WIDTH x
// ROI_HEIGHT crop (a live-webcam ROI), i.e. a consistent ~1.6:1 rectangle --
// see mod_id_card.py / mod_ocr.py's to_card_aspect(). A phone photo's aspect
// ratio is nothing like that, so it must be center-cropped to the same
// ratio before classification or confidence drops even on a good photo.
private const val ROI_WIDTH = 400
private const val ROI_HEIGHT = 250

data class IdCardResult(val label: String, val confidence: Float, val isIdCard: Boolean)

/** Ports mod_id_card.py's predict_id_card_roi() + mod_ocr.py's to_card_aspect(). */
class IdCardClassifier(context: Context) {
    private val interpreter = Interpreter(loadModelFile(context))

    fun classify(bgr: Mat): IdCardResult {
        val cropped = toCardAspect(bgr)
        val input = preprocess(cropped)

        val output = Array(1) { FloatArray(1) }
        interpreter.run(input, output)
        val logit = output[0][0]

        val label = if (logit > 0f) "ID Card" else "Not ID Card"
        val confidence = kotlin.math.abs(logit)
        return IdCardResult(label, confidence, label == "ID Card")
    }

    private fun toCardAspect(bgr: Mat): Mat {
        val w = bgr.cols()
        val h = bgr.rows()
        val targetRatio = ROI_WIDTH.toDouble() / ROI_HEIGHT
        val curRatio = w.toDouble() / h
        return if (curRatio > targetRatio) {
            val newW = (h * targetRatio).toInt()
            val x0 = (w - newW) / 2
            Mat(bgr, Rect(x0, 0, newW, h))
        } else {
            val newH = (w / targetRatio).toInt()
            val y0 = (h - newH) / 2
            Mat(bgr, Rect(0, y0, w, newH))
        }
    }

    private fun preprocess(bgr: Mat): Array<Array<Array<FloatArray>>> {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        val resized = Mat()
        Imgproc.resize(gray, resized, Size(IMAGE_SIZE.toDouble(), IMAGE_SIZE.toDouble()))

        val pixels = ByteArray(IMAGE_SIZE * IMAGE_SIZE)
        resized.get(0, 0, pixels)

        // [1][H][W][3] -- the Python side replicates the single grayscale
        // value into all 3 "channels" rather than using real RGB.
        val input = Array(1) { Array(IMAGE_SIZE) { Array(IMAGE_SIZE) { FloatArray(3) } } }
        for (row in 0 until IMAGE_SIZE) {
            for (col in 0 until IMAGE_SIZE) {
                val value = (pixels[row * IMAGE_SIZE + col].toInt() and 0xFF) / 255f
                input[0][row][col][0] = value
                input[0][row][col][1] = value
                input[0][row][col][2] = value
            }
        }
        return input
    }

    fun close() = interpreter.close()

    private companion object {
        fun loadModelFile(context: Context): MappedByteBuffer {
            val fd = context.assets.openFd(MODEL_ASSET)
            val stream = FileInputStream(fd.fileDescriptor)
            val channel = stream.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }
}
