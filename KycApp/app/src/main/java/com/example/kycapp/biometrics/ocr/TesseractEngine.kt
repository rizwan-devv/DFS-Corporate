package com.example.kycapp.biometrics.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

private const val TAG = "TesseractEngine"
private const val LANGUAGE = "eng"
private const val TRAINEDDATA_ASSET = "eng.traineddata"

/**
 * One reconstructed text line, mirroring mod_ocr.py's `Line` namedtuple:
 * text/conf/bbox/y_frac plus which image+variant it was read from (needed
 * to re-OCR the same pixels at a tighter crop -- see refineCnicDigits).
 */
data class OcrLine(
    val text: String,
    val conf: Double,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val image: org.opencv.core.Mat,
    val variant: String,
    val yFrac: Double
)

/**
 * Wraps TessBaseAPI. Tesseract requires trained data on a real filesystem
 * path (a "tessdata" subdirectory it can read directly, not an APK asset
 * stream), so the bundled .traineddata is copied out to internal storage
 * once on first use.
 */
class TesseractEngine(context: Context) {
    private val api = TessBaseAPI()

    init {
        val tessdataDir = File(context.filesDir, "tesseract/tessdata").apply { mkdirs() }
        val dataFile = File(tessdataDir, "$LANGUAGE.traineddata")
        if (!dataFile.exists()) {
            context.assets.open(TRAINEDDATA_ASSET).use { input ->
                dataFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val ok = api.init(dataFile.parentFile!!.parentFile!!.absolutePath, LANGUAGE, TessBaseAPI.OEM_DEFAULT)
        check(ok) { "Failed to initialize Tesseract with $LANGUAGE data" }
    }

    /**
     * Runs one recognition pass with the given PSM + Tesseract variables,
     * returning full reconstructed text lines (Tesseract's own TEXTLINE
     * iterator level -- equivalent to pytesseract's block/par/line-grouped
     * word registry, just read directly from the native line iterator
     * instead of re-grouping word-level TSV rows).
     */
    fun recognizeLines(
        bitmap: Bitmap,
        pageSegMode: Int,
        variables: Map<String, String> = emptyMap(),
        variant: String,
        variantImage: org.opencv.core.Mat,
        cardHeightPx: Int
    ): List<OcrLine> {
        api.pageSegMode = pageSegMode
        variables.forEach { (key, value) -> api.setVariable(key, value) }
        api.setImage(bitmap)
        Log.d(TAG, "recognizeLines: image=${bitmap.width}x${bitmap.height} psm=$pageSegMode meanConfidence=${api.meanConfidence()}")

        val lines = mutableListOf<OcrLine>()
        val iterator = api.resultIterator
        if (iterator == null) {
            Log.w(TAG, "recognizeLines: resultIterator was null (no text recognized at all)")
        } else {
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)?.trim()
                val conf = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                val box = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                if (!text.isNullOrEmpty() && conf >= 0f && box != null) {
                    val left = box[0]; val top = box[1]; val right = box[2]; val bottom = box[3]
                    lines.add(
                        OcrLine(
                            text = text,
                            conf = conf / 100.0,
                            left = left, top = top, width = right - left, height = bottom - top,
                            image = variantImage,
                            variant = variant,
                            yFrac = (top + bottom) / 2.0 / cardHeightPx.toDouble()
                        )
                    )
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
            iterator.delete()
        }
        return lines
    }

    /** Single best-effort text read (used for the targeted CNIC-digit re-OCR pass). */
    fun recognizeText(bitmap: Bitmap, pageSegMode: Int, variables: Map<String, String> = emptyMap()): String {
        api.pageSegMode = pageSegMode
        variables.forEach { (key, value) -> api.setVariable(key, value) }
        api.setImage(bitmap)
        return api.getUTF8Text() ?: ""
    }

    fun close() {
        api.recycle()
    }
}
