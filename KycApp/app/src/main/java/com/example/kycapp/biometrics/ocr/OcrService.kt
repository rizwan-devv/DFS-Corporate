package com.example.kycapp.biometrics.ocr

import android.util.Log
import com.example.kycapp.biometrics.idcard.IdCardClassifier
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.opencv.core.Mat

private const val TAG = "OcrService"

data class OcrResult(
    val ok: Boolean,
    val message: String,
    val fields: Map<String, String>,
    // Fields whose value came from a single, unconfirmed read (e.g. the
    // CNIC-digit whitelist re-OCR pass disagreeing with the general text
    // pass) rather than two independent readings agreeing -- worth a
    // "double-check this" flag in the UI, since there's no capture-quality
    // signal that catches a single-shot misread on its own.
    val lowConfidenceFields: Set<String>,
    val allFieldsFound: Boolean,
    val idPhoto: Mat?
)

private const val ID_SAVE_CONFIDENCE = 0.80f

/**
 * Single-shot CNIC OCR for one phone-captured photo -- ports mod_ocr.py's
 * scan_single_image(). A phone camera already autofocuses and produces one
 * sharp, deliberately-framed photo per tap, so (like the Python function
 * this mirrors) there's no multi-cycle cross-capture voting here -- that's
 * OcrSession's job for the live-webcam Streamlit flow, which this app has
 * no equivalent of.
 */
suspend fun scanSingleImage(
    idCardClassifier: IdCardClassifier,
    tesseractEngineFactory: () -> TesseractEngine,
    idPhotoLandmarker: FaceLandmarker,
    frame: Mat
): OcrResult {
    Log.d(TAG, "scanSingleImage: input frame ${frame.cols()}x${frame.rows()}")
    val idCardResult = idCardClassifier.classify(frame)
    Log.d(TAG, "idCardResult: label=${idCardResult.label} confidence=${idCardResult.confidence} isIdCard=${idCardResult.isIdCard}")
    if (!idCardResult.isIdCard || idCardResult.confidence < ID_SAVE_CONFIDENCE) {
        val pct = (idCardResult.confidence * 100).toInt()
        return OcrResult(
            ok = false,
            message = "Doesn't look like a CNIC (confidence $pct%). " +
                "Try again with the card filling the frame and good lighting.",
            fields = emptyMap(),
            lowConfidenceFields = emptySet(),
            allFieldsFound = false,
            idPhoto = null
        )
    }

    val (ocrLines, colorImg) = readText(tesseractEngineFactory, frame)
    // The targeted CNIC-digit re-OCR pass (refineCnicDigits, called from
    // within extractCnicInfo) is a single small-region pass, not worth
    // parallelizing -- one fresh engine for it.
    val digitEngine = tesseractEngineFactory()
    val extracted = try {
        extractCnicInfo(digitEngine, ocrLines)
    } finally {
        digitEngine.close()
    }
    val fields = extracted.mapValues { it.value.value }
    val lowConfidenceFields = extracted.filterValues { it.weight == WEAK }.keys
    val allFound = fields.size == FIELDS.size
    Log.d(TAG, "extractCnicInfo: ${ocrLines.size} lines in -> fields=$fields lowConfidence=$lowConfidenceFields")
    val idPhoto = extractIdPhoto(idPhotoLandmarker, colorImg)

    return OcrResult(
        ok = true,
        message = if (allFound) "All fields found" else "Found ${fields.size}/${FIELDS.size} fields",
        fields = fields,
        lowConfidenceFields = lowConfidenceFields,
        allFieldsFound = allFound,
        idPhoto = idPhoto
    )
}
