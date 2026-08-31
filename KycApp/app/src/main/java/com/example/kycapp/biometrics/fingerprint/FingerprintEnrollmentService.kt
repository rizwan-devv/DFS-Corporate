package com.example.kycapp.biometrics.fingerprint

import com.example.kycapp.data.FingerprintTemplateRepository
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

data class EnrollResult(
    val ok: Boolean,
    val message: String,
    val sample: Int = 0,
    val max: Int = MAX_ENROLL_SAMPLES
)

/** Ports mod_fingerprint.py's enroll_hand(). */
object FingerprintEnrollmentService {

    suspend fun enroll(
        repository: FingerprintTemplateRepository,
        personName: String,
        hand: String,
        frame: Mat
    ): EnrollResult {
        if (personName.isBlank()) {
            return EnrollResult(false, "Please enter a name before enrolling.")
        }

        val cropped = cropRoi(frame)
            ?: return EnrollResult(false, "Invalid crop area — frame too small?")

        // Just the region framed by the on-screen D-shape guide (see
        // fingerprintRoi()), at native captured resolution and without the
        // CLAHE/Gabor ridge-enhancement matching uses -- is what gets stored
        // in the WSQ/Base64/ISO export records. That keeps the stored record
        // to only what the user actually framed as their fingerprint (not
        // the whole photo background), while still being the fingerprint as
        // captured -- full detail, no enhancement filtering.
        val fullDetailCrop = cropRoiRaw(frame) ?: cropped
        val fullDetailGray = Mat()
        Imgproc.cvtColor(fullDetailCrop, fullDetailGray, Imgproc.COLOR_BGR2GRAY)

        val gray = Mat()
        Imgproc.cvtColor(cropped, gray, Imgproc.COLOR_BGR2GRAY)
        val sharp = sharpness(gray)
        if (sharp < ENROLL_SHARPNESS_MIN) {
            return EnrollResult(
                false,
                "Image too blurry (sharpness ${sharp.toInt()}, need >= ${ENROLL_SHARPNESS_MIN.toInt()}). " +
                    "Hold still and ensure good lighting."
            )
        }

        val features = extractFeatures(cropped)
        val orbCount = features.desOrb.rows()
        val akazeCount = features.desAkaze.rows()
        val tier = qualityTier(orbCount)

        if (orbCount < MIN_ENROLL_FEATURES) {
            return EnrollResult(
                false,
                "${tier.label} — only $orbCount features found (need >= $MIN_ENROLL_FEATURES). " +
                    "Press the hand firmly and retry."
            )
        }

        val existingSamples = repository.samplesAsTemplates(personName, hand)
        if (existingSamples.isNotEmpty()) {
            val query = FingerprintTemplate(
                orbKeypoints = features.kpOrb.toList(),
                orbDescriptors = features.desOrb,
                akazeKeypoints = features.kpAkaze.toList(),
                akazeDescriptors = features.desAkaze
            )
            val isDuplicate = existingSamples.any { (_, stored) -> matchScore(query, stored) >= ENROLL_DUPLICATE_THRESHOLD }
            if (isDuplicate) {
                return EnrollResult(
                    false,
                    "Near-duplicate capture — too similar to an existing sample. " +
                        "Shift or re-angle the hand slightly, then retry."
                )
            }
        }

        val sampleCount = repository.saveSample(personName, hand, features, sharp.toFloat(), fullDetailGray)

        return EnrollResult(
            ok = true,
            message = "Enrolled $personName's ${hand.lowercase()} hand — sample $sampleCount/$MAX_ENROLL_SAMPLES · " +
                "${tier.label} ($orbCount ORB + $akazeCount AKAZE, sharpness ${sharp.toInt()}).",
            sample = sampleCount
        )
    }
}
