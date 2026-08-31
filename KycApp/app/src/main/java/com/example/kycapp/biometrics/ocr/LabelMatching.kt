package com.example.kycapp.biometrics.ocr

// Ports mod_ocr.py's label keyword sets + _label_kind/_strip_label.

val LBL_FATHER = setOf(
    "father name", "father", "fther", "fathr", "fathe",
    "ffather", "father neme", "fathername", "f name"
)
val LBL_NAME = setOf("name", "neme", "nane", "nam")
val LBL_DOB = setOf("date of birth", "date of blrth", "dateofbirth", "dob", "d.o.b", "birth")
val LBL_ISSUE = setOf("date of issue", "date of lssue", "dateofissue")
val LBL_EXPIRY = setOf("date of expiry", "date of explry", "dateofexpiry", "expiry date", "expiry")

private val CANON_LABELS: Map<String, List<String>> = mapOf(
    "father" to listOf("father name"),
    "name" to listOf("name"),
    "dob" to listOf("date of birth", "birth"),
    "issue" to listOf("date of issue", "issue"),
    "expiry" to listOf("date of expiry", "expiry"),
    "cnic" to listOf("identity number", "cnic")
)
private const val LABEL_FUZZ = 0.72

private fun has(s: String, keywords: Set<String>): Boolean = keywords.any { it in s }

/** Which field label (if any) this line carries. */
fun labelKind(line: String): String? {
    val lo = line.lowercase().trim()
    if (has(lo, LBL_FATHER)) return "father"
    if (has(lo, LBL_DOB)) return "dob"
    if (has(lo, LBL_ISSUE)) return "issue"
    if (has(lo, LBL_EXPIRY)) return "expiry"
    if (has(lo, LBL_NAME)) return "name"

    val head = lo.filter { it.isLetter() || it == ' ' }.trim()
    if (head.isEmpty()) return null
    var best = 0.0
    var bestKind: String? = null
    for ((kind, phrases) in CANON_LABELS) {
        for (phrase in phrases) {
            val window = head.take(maxOf(phrase.length + 4, 8))
            val score = maxOf(similar(window, phrase), similar(head, phrase))
            if (score > best) {
                best = score; bestKind = kind
            }
        }
    }
    return if (best >= LABEL_FUZZ) bestKind else null
}

fun stripLabel(line: String, keywords: Set<String>): String? {
    val lower = line.lowercase()
    for (kw in keywords.sortedByDescending { it.length }) {
        val idx = lower.indexOf(kw)
        if (idx != -1) {
            val rest = line.substring(idx + kw.length).trim().trimStart(':').trim()
            if (rest.isNotEmpty()) return rest
        }
    }
    return null
}
