package com.example.kycapp.biometrics.ocr

import kotlin.math.max

// Ports mod_ocr.py's name-shape/cluster/consensus section. See that file's
// comments for the *why* behind each rule -- this is a faithful 1:1 port,
// not a redesign, since these rules exist because of real bugs they fixed
// (OCR debris like "A B"/"Le Ed" being accepted as names, the father's name
// being committed as the holder's, etc).

const val NAME_MIN_LETTERS = 8
const val NAME_MIN_LONG_TOK = 4
const val NAME_MAX_NOISE = 0.34
const val NAME_MIN_CONF = 0.45
const val NAME_CLUSTER_SIM = 0.82
const val NAME_Y_WINDOW = 0.05

val SKIP_WORDS = setOf(
    "pakistan", "islamic", "republic", "national", "identity",
    "card", "gender", "country", "stay", "holder", "signature",
    "date", "birth", "issue", "expiry", "father", "number", "cnic", "of",
    "male", "female", "nadra", "authority", "registration", "database",
    "sustenance", "valid", "unless", "name"
)
private const val SKIP_FUZZ = 0.82

fun similar(a: String, b: String): Double = stringSimilarity(a, b)

/** Whether a token could be a printed word rather than OCR debris. */
fun wordlike(token: String): Boolean {
    val letters = token.filter { it.isLetter() }.lowercase()
    if (letters.length < 3) return false
    if (Regex("(.)\\1\\1").containsMatchIn(letters)) return false // "Uuuutoc", "Meee"
    if (letters.none { it in "aeiouy" }) return false // "Cscg"
    if (Regex("[bcdfghjklmnpqrstvwxz]{4,}").containsMatchIn(letters)) return false // "Wdontlty"
    return true
}

/** Reduces a raw OCR line to just its name-shaped tokens. */
fun cleanNameCandidate(text: String): String {
    val kept = mutableListOf<String>()
    for (tok in NAME_TOKEN_RE.findAll(text).map { it.value }) {
        val letters = tok.filter { it.isLetter() }
        if (letters.length < 3) continue
        val low = letters.lowercase()
        if (low in SKIP_WORDS) continue
        if (SKIP_WORDS.any { similar(low, it) >= SKIP_FUZZ }) continue
        kept.add(tok.trim('-'))
    }
    return kept.joinToString(" ").trim()
}

fun isValidName(text: String): Boolean {
    if (text.any { it.isDigit() }) return false

    val raw = text.trim()
    if (raw.isNotEmpty()) {
        val nonSpace = raw.filter { !it.isWhitespace() }
        val noise = nonSpace.count { it !in "'-" && !it.isLetter() }
        if (nonSpace.isNotEmpty() && noise.toDouble() / nonSpace.length > NAME_MAX_NOISE) return false
    }

    val cleaned = cleanNameCandidate(text)
    if (cleaned.isEmpty() || !NAME_RE.matches(cleaned)) return false

    val words = cleaned.split(" ").filter { it.isNotEmpty() }
    if (words.size < 2) return false
    val letters = words.map { it.filter { c -> c.isLetter() } }
    if (letters.sumOf { it.length } < NAME_MIN_LETTERS) return false
    if (letters.none { it.length >= NAME_MIN_LONG_TOK }) return false
    if (!words.all { wordlike(it) }) return false
    return true
}

fun normName(text: String): String = cleanNameCandidate(text).titleCaseWords()

private fun String.titleCaseWords(): String =
    split(" ").joinToString(" ") { w ->
        if (w.isEmpty()) w else w[0].uppercaseChar() + w.substring(1).lowercase()
    }

data class NameCluster(
    var value: String,
    var y: Double,
    var conf: Double,
    val variants: MutableSet<String> = mutableSetOf(),
    var reads: Int = 0,
    val members: MutableList<Pair<String, Double>> = mutableListOf()
)

/** Picks the spelling that best represents everyone in a name cluster (majority vote). */
fun clusterRepresentative(members: List<Pair<String, Double>>): String {
    val votes = LinkedHashMap<String, Int>()
    val bestConf = HashMap<String, Double>()
    for ((value, conf) in members) {
        votes[value] = (votes[value] ?: 0) + 1
        if (conf > (bestConf[value] ?: -1.0)) bestConf[value] = conf
    }
    return votes.keys.maxWithOrNull(
        compareBy({ votes[it]!! }, { -it.length }, { bestConf[it]!! })
    )!!
}

/** Groups the name-shaped rows into clusters, one cluster per printed row. */
fun nameClusters(lines: List<OcrLine>): List<NameCluster> {
    val candidates = lines.filter { it.conf >= NAME_MIN_CONF && isValidName(it.text) }
        .sortedByDescending { it.conf }

    val clusters = mutableListOf<NameCluster>()
    for (l in candidates) {
        val norm = normName(l.text)
        val match = clusters.firstOrNull { similar(norm.lowercase(), it.value.lowercase()) >= NAME_CLUSTER_SIM }
        if (match != null) {
            match.variants.add(l.variant)
            match.reads += 1
            match.y = minOf(match.y, l.yFrac)
            match.members.add(norm to l.conf)
        } else {
            clusters.add(
                NameCluster(
                    value = norm, y = l.yFrac, conf = l.conf,
                    variants = mutableSetOf(l.variant), reads = 1,
                    members = mutableListOf(norm to l.conf)
                )
            )
        }
    }

    for (c in clusters) c.value = clusterRepresentative(c.members)
    return clusters.sortedBy { it.y }
}

/** Java/Kotlin has no SequenceMatcher; this ratio-of-matches approximation
 * mirrors difflib.SequenceMatcher.ratio() closely enough for the similarity
 * thresholds used throughout this module (0.72-0.85): 2*M / (len(a)+len(b))
 * where M is the total length of matching blocks found greedily, same
 * algorithm difflib itself uses (longest matching block, recursed on the
 * unmatched left/right remainders). */
private fun stringSimilarity(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    val matches = matchingBlockLength(a, 0, a.length, b, 0, b.length)
    return 2.0 * matches / (a.length + b.length)
}

private fun matchingBlockLength(a: String, aLo: Int, aHi: Int, b: String, bLo: Int, bHi: Int): Int {
    if (aLo >= aHi || bLo >= bHi) return 0
    var bestLen = 0
    var bestAi = aLo
    var bestBj = bLo
    for (i in aLo until aHi) {
        var j = bLo
        while (j < bHi) {
            if (a[i] == b[j]) {
                var len = 0
                while (i + len < aHi && j + len < bHi && a[i + len] == b[j + len]) len++
                if (len > bestLen) {
                    bestLen = len; bestAi = i; bestBj = j
                }
            }
            j++
        }
    }
    if (bestLen == 0) return 0
    val left = matchingBlockLength(a, aLo, bestAi, b, bLo, bestBj)
    val right = matchingBlockLength(a, bestAi + bestLen, aHi, b, bestBj + bestLen, bHi)
    return bestLen + left + right
}
