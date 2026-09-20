package com.example.kycapp.navigation

import android.net.Uri

object Routes {
    const val LOGIN = "login"
    const val MAIN_MENU = "main_menu"

    const val ID_CARD_INTRO = "id_card_intro"
    const val OCR_INTRO = "ocr_intro"
    const val FACE_MATCH_INTRO = "face_match_intro"
    const val FINGERPRINT_INTRO = "fingerprint_intro"

    // Name entry shown once, before the left-hand enroll tutorial. Match doesn't
    // need a name -- it identifies the person from the enrolled templates.
    const val FINGERPRINT_NAME_ENTRY = "fingerprint_name_entry"

    // Placeholder for the {name} path segment when there's no name to carry --
    // an actually-empty segment doesn't reliably match NavHost's required-argument
    // pattern, so a non-empty sentinel is used instead and decoded back to "".
    private const val NO_NAME = "_"
    private fun encodeName(name: String) = if (name.isBlank()) NO_NAME else Uri.encode(name)
    fun decodeName(encoded: String?): String =
        if (encoded.isNullOrEmpty() || encoded == NO_NAME) "" else Uri.decode(encoded)

    // Instruction/tutorial screen shown before a fingerprint scan. `name` is only
    // meaningful for the enroll flow; match passes the NO_NAME placeholder.
    const val FINGERPRINT_TUTORIAL = "fingerprint_tutorial/{mode}/{hand}/{name}"
    fun fingerprintTutorial(mode: String, hand: String, name: String = "") =
        "fingerprint_tutorial/$mode/$hand/${encodeName(name)}"

    // Generic camera route, parameterized by capture mode + which hand + person
    // name (fingerprint enroll only -- every other mode passes the NO_NAME placeholder).
    const val CAMERA = "camera/{mode}/{hand}/{name}"
    fun camera(mode: String, hand: String = HandSide.NONE.name, name: String = "") =
        "camera/$mode/$hand/${encodeName(name)}"

    // Fingerprint record viewer (WSQ/Base64/ISO export formats). An empty
    // name browses every enrolled record; a specific name (used right after
    // an enroll session finishes) filters to just that person's records.
    const val FINGERPRINT_RECORDS = "fingerprint_records/{name}"
    fun fingerprintRecords(name: String = "") = "fingerprint_records/${encodeName(name)}"

    const val SIGNATURE = "signature"
}

enum class CaptureMode(val label: String) {
    ID_CARD("ID Card Scan"),
    OCR("OCR"),
    FACE_MATCH("Face Matching"),
    FINGERPRINT_ENROLL("Fingerprint Enroll"),
    FINGERPRINT_MATCH("Fingerprint Match");

    companion object {
        fun fromRoute(value: String): CaptureMode =
            entries.firstOrNull { it.name == value } ?: ID_CARD
    }
}

/** Which hand is being scanned. NONE is used for non-fingerprint flows. */
enum class HandSide(val label: String) {
    LEFT("Left Hand"),
    RIGHT("Right Hand"),
    NONE("");

    companion object {
        fun fromRoute(value: String): HandSide =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}