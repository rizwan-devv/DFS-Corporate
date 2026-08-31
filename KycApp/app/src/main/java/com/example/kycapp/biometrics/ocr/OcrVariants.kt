package com.example.kycapp.biometrics.ocr

import com.googlecode.tesseract.android.TessBaseAPI
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class TessConfig(val pageSegMode: Int, val variables: Map<String, String> = emptyMap())

private val TESS_BLOCK = TessConfig(
    TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
    mapOf("preserve_interword_spaces" to "1")
)
private val TESS_SPARSE = TessConfig(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
private val TESS_COLUMN = TessConfig(
    TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN,
    mapOf("preserve_interword_spaces" to "1")
)
val TESS_DIGITS_LINE = TessConfig(
    TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
    mapOf("tessedit_char_whitelist" to "0123456789-", "classify_bln_numeric_mode" to "1")
)
val TESS_DIGITS_WORD = TessConfig(
    TessBaseAPI.PageSegMode.PSM_SINGLE_WORD,
    mapOf("tessedit_char_whitelist" to "0123456789-", "classify_bln_numeric_mode" to "1")
)

data class OcrVariant(val name: String, val image: Mat, val configs: List<TessConfig>)

/**
 * Several renderings of the same card, merged (not chosen between) -- ports
 * mod_ocr.py's _ocr_variants. See that function's docstring for why each
 * rendering exists; the OpenCV calls here are a direct 1:1 port.
 */
fun ocrVariants(colorUpscaled: Mat): List<OcrVariant> {
    val gray = Mat()
    Imgproc.cvtColor(colorUpscaled, gray, Imgproc.COLOR_BGR2GRAY)
    val clahe = Mat()
    Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(gray, clahe)
    val smooth = Mat()
    Imgproc.bilateralFilter(clahe, smooth, 7, 45.0, 45.0)

    val adaptive = Mat()
    Imgproc.adaptiveThreshold(
        smooth, adaptive, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 31, 10.0
    )
    val otsu = Mat()
    Imgproc.threshold(smooth, otsu, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
    val blur = Mat()
    Imgproc.GaussianBlur(clahe, blur, Size(0.0, 0.0), 2.0)
    val sharp = Mat()
    Core.addWeighted(clahe, 1.7, blur, -0.7, 0.0, sharp)

    return listOf(
        OcrVariant("adaptive", adaptive, listOf(TESS_BLOCK, TESS_SPARSE)),
        OcrVariant("sharp", sharp, listOf(TESS_BLOCK, TESS_COLUMN)),
        OcrVariant("otsu", otsu, listOf(TESS_BLOCK))
    )
}
