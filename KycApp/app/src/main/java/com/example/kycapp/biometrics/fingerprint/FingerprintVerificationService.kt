package com.example.kycapp.biometrics.fingerprint

import com.example.kycapp.data.FingerprintTemplateRepository
import org.opencv.core.Mat

data class VerifyResult(
    val ok: Boolean,
    val matched: Boolean = false,
    val name: String? = null,
    val message: String = ""
)

/** Ports mod_fingerprint.py's verify_hand(). */
object FingerprintVerificationService {

    suspend fun verify(
        repository: FingerprintTemplateRepository,
        hand: String,
        frame: Mat
    ): VerifyResult {
        val handTemplates = repository.allForHand(hand)
        if (handTemplates.isEmpty()) {
            return VerifyResult(false, message = "No enrolled fingerprints found for the ${hand.lowercase()} hand.")
        }

        val cropped = cropRoi(frame) ?: return VerifyResult(false, message = "Invalid crop area.")
        val features = extractFeatures(cropped)
        val orbCount = features.desOrb.rows()
        if (orbCount < MIN_ENROLL_FEATURES) {
            return VerifyResult(
                false,
                message = "Poor capture — only $orbCount features found (need $MIN_ENROLL_FEATURES). Adjust your hand and retry."
            )
        }

        val query = FingerprintTemplate(
            orbKeypoints = features.kpOrb.toList(),
            orbDescriptors = features.desOrb,
            akazeKeypoints = features.kpAkaze.toList(),
            akazeDescriptors = features.desAkaze
        )

        val personScores = HashMap<String, Double>()
        for ((person, template) in handTemplates) {
            val score = matchScore(query, template)
            val best = personScores[person]
            if (best == null || score > best) personScores[person] = score
        }

        val bestEntry = personScores.maxByOrNull { it.value }
            ?: return VerifyResult(false, message = "Could not compute any match scores.")

        val matched = bestEntry.value >= VERIFY_THRESHOLD
        return VerifyResult(
            ok = true,
            matched = matched,
            name = if (matched) bestEntry.key else null,
            message = if (matched) {
                "Verified: ${bestEntry.key} (score %.3f)".format(bestEntry.value)
            } else {
                "No match — closest was ${bestEntry.key} at %.3f, below the %.2f threshold.".format(bestEntry.value, VERIFY_THRESHOLD)
            }
        )
    }
}
