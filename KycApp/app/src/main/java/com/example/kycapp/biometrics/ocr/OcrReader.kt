package com.example.kycapp.biometrics.ocr

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.opencv.android.Utils
import org.opencv.core.Mat

private const val TAG = "OcrReader"
private const val MIN_LINE_CONFIDENCE = 0.30

data class OcrReadResult(val lines: List<OcrLine>, val colorUpscaled: Mat)

fun matToBitmap(mat: Mat): Bitmap {
    val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(mat, bitmap)
    return bitmap
}

/**
 * OCRs the card with every variant/PSM combination and returns the merged,
 * deduplicated set of full reconstructed text lines -- ports mod_ocr.py's
 * _read_text(). Line reconstruction itself comes directly from Tesseract's
 * native TEXTLINE iterator (see TesseractEngine.recognizeLines) rather than
 * re-grouping word-level rows the way pytesseract's image_to_data forces on
 * the Python side -- same resulting data (text/confidence/bbox per printed
 * line), simpler path once you're calling the native API directly instead
 * of through the CLI-wrapping TSV interface pytesseract provides.
 *
 * Every (variant, config) pass runs concurrently on its own short-lived
 * TesseractEngine from [engineFactory], instead of one engine working
 * through all passes sequentially -- exactly the same recognition work as
 * before (nothing is skipped or downscaled), just running in parallel
 * across CPU cores, since on a phone these passes were the dominant cost of
 * a single OCR capture (one pass alone routinely took ~13s).
 */
suspend fun readText(engineFactory: () -> TesseractEngine, roi: Mat): OcrReadResult {
    val colorUpscaled = warpAndUpscale(roi)
    val heightPx = colorUpscaled.rows()
    Log.d(TAG, "warpAndUpscale: ${roi.cols()}x${roi.rows()} -> ${colorUpscaled.cols()}x${colorUpscaled.rows()}")

    val startMs = System.currentTimeMillis()
    val passes = ocrVariants(colorUpscaled).flatMap { variant -> variant.configs.map { variant to it } }
    val allLines = coroutineScope {
        passes.map { (variant, config) ->
            async(Dispatchers.Default) {
                val engine = engineFactory()
                try {
                    val bitmap = matToBitmap(variant.image)
                    val lines = engine.recognizeLines(
                        bitmap, config.pageSegMode, config.variables,
                        variant.name, variant.image, heightPx
                    )
                    Log.d(TAG, "variant=${variant.name} psm=${config.pageSegMode} -> ${lines.size} raw lines")
                    lines
                } catch (e: Exception) {
                    Log.w(TAG, "OCR pass failed: variant=${variant.name} psm=${config.pageSegMode}", e)
                    emptyList()
                } finally {
                    engine.close()
                }
            }
        }.awaitAll().flatten()
    }
    Log.d(TAG, "all OCR passes done in ${System.currentTimeMillis() - startMs}ms")

    val seen = HashSet<String>()
    val result = mutableListOf<OcrLine>()
    for (line in allLines.sortedBy { it.top }) {
        if (line.conf < MIN_LINE_CONFIDENCE) continue
        if (!seen.add(line.text)) continue
        result.add(line)
    }
    Log.d(TAG, "readText: ${allLines.size} raw lines total -> ${result.size} after confidence+dedup filter")
    for (line in result) {
        Log.d(TAG, "  [${"%.2f".format(line.conf)}] variant=${line.variant} yFrac=${"%.2f".format(line.yFrac)} text=${line.text}")
    }
    return OcrReadResult(result, colorUpscaled)
}
