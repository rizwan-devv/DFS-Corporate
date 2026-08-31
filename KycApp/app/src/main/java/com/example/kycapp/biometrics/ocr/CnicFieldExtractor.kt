package com.example.kycapp.biometrics.ocr

import android.util.Log

private const val TAG = "CnicFieldExtractor"

const val STRONG = 2
const val WEAK = 1
data class Cand(val value: String, val weight: Int)

val FIELDS = listOf("Name", "Father Name", "Identity Number", "Date of Birth", "Date of Issue", "Date of Expiry")

/**
 * Re-OCRs just the line containing the CNIC number with a digit-only
 * whitelist -- ports mod_ocr.py's _refine_cnic_digits().
 */
fun refineCnicDigits(engine: TesseractEngine, line: OcrLine): String? {
    val img = line.image
    val pad = 8
    val x0 = maxOf(0, line.left - pad)
    val y0 = maxOf(0, line.top - pad)
    val x1 = minOf(img.cols(), line.left + line.width + pad)
    val y1 = minOf(img.rows(), line.top + line.height + pad)
    if (x1 <= x0 || y1 <= y0) return null

    val crop = org.opencv.core.Mat(img, org.opencv.core.Rect(x0, y0, x1 - x0, y1 - y0))
    val resized = org.opencv.core.Mat()
    org.opencv.imgproc.Imgproc.resize(
        crop, resized, org.opencv.core.Size(), 2.0, 2.0, org.opencv.imgproc.Imgproc.INTER_CUBIC
    )
    val bitmap = matToBitmap(resized)

    for (config in listOf(TESS_DIGITS_LINE, TESS_DIGITS_WORD)) {
        val raw = try {
            engine.recognizeText(bitmap, config.pageSegMode, config.variables)
        } catch (e: Exception) {
            continue
        }
        val digits = raw.filter { it.isDigit() }
        if (digits.length == 13) {
            val formatted = "${digits.substring(0, 5)}-${digits.substring(5, 12)}-${digits.substring(12)}"
            if (validateCnic(formatted)) return formatted
        }
    }
    return null
}

/**
 * Extracts the six CNIC fields from one photo's OCR lines -- ports
 * mod_ocr.py's _extract_cnic_info() faithfully. See that function's
 * docstring/comments for *why* each rule exists; this is a 1:1 translation,
 * not a redesign.
 */
fun extractCnicInfo(engine: TesseractEngine, ocrLines: List<OcrLine>): Map<String, Cand> {
    val out = LinkedHashMap<String, Cand>()
    fun offer(field: String, value: String?, weight: Int) {
        if (value.isNullOrEmpty()) return
        val prev = out[field]
        if (prev == null || weight > prev.weight) out[field] = Cand(value, weight)
    }

    val lines = ocrLines.filter { it.text.isNotBlank() }
    if (lines.isEmpty()) return out

    val texts = lines.map { it.text }
    val fullText = texts.joinToString(" ")
    val labels = texts.map { labelKind(it) }

    // ── Identity Number ──────────────────────────────────────────────────
    var cnicLineIdx: Int? = null
    for (i in texts.indices) {
        val cands = findCnics(texts[i])
        if (cands.isEmpty()) continue
        cnicLineIdx = i
        val coarse = cands[0]
        val refined = refineCnicDigits(engine, lines[i])
        if (refined != null && refined == coarse) {
            offer("Identity Number", coarse, STRONG)
        } else {
            offer("Identity Number", refined ?: coarse, WEAK)
        }
        break
    }
    if (out["Identity Number"] == null) {
        val cands = findCnics(fullText)
        if (cands.isNotEmpty()) offer("Identity Number", cands[0], WEAK)
    }

    // ── Dates ─────────────────────────────────────────────────────────────
    data class Dated(val date: String, val lineIdx: Int, val label: String?)
    val dated = mutableListOf<Dated>()
    for (i in texts.indices) {
        for (d in findDates(texts[i])) dated.add(Dated(d, i, labels[i]))
    }
    val allDates = mutableListOf<String>()
    for (d in dated) if (d.date !in allDates) allDates.add(d.date)
    Log.d(TAG, "dates: allDates=$allDates dated=${dated.map { "${it.date}@${it.lineIdx}(${it.label})" }}")

    // 1. The date on the Identity-Number row is the date of birth.
    if (cnicLineIdx != null) {
        for (d in dated) {
            if (d.lineIdx == cnicLineIdx && plausibleDob(d.date)) {
                offer("Date of Birth", d.date, STRONG)
                break
            }
        }
    }
    Log.d(TAG, "after DOB-from-cnic-row: out=$out")

    // 2a. Label anchoring for DOB. (Issue/Expiry are handled after term
    // structure below -- see the comment there for why.)
    for (d in dated) {
        if (!plausibleDob(d.date, java.util.GregorianCalendar())) continue
        if (d.label == "dob") {
            offer("Date of Birth", d.date, STRONG)
            break
        }
        if (d.lineIdx > 0 && labels[d.lineIdx - 1] == "dob") {
            offer("Date of Birth", d.date, STRONG)
            break
        }
    }

    Log.d(TAG, "after dob label-anchoring: out=$out")

    // 3. Term structure: two dates a printed validity term apart are issue/expiry.
    // Deliberately runs BEFORE issue/expiry label anchoring: "Date of Issue"
    // and "Date of Expiry" are printed side by side on the same row on a
    // CNIC, with their two date values also side by side on the row below.
    // The label-anchoring check below only looks at vertical (line-index)
    // adjacency in the OCR's Y-sorted line list, with no notion of left vs
    // right column, so it can just as easily pair a value with the WRONG
    // same-row label as the right one, depending on how each OCR variant's
    // bounding boxes happened to sort that run -- pairing by "one date, N
    // years before another" is a far more reliable signal than "the line
    // above this value said Issue/Expiry" for exactly this side-by-side
    // layout, so it gets first say.
    var bestPair: Triple<Pair<Int, Int>, String, String>? = null
    for (a in allDates) {
        for (b in allDates) {
            if (a == b) continue
            val years = termYears(a, b) ?: continue
            if (!plausibleIssue(a) || !plausibleExpiry(b)) continue
            val score = years to (if (out["Date of Issue"]?.value == a) 1 else 0)
            if (bestPair == null || score.first > bestPair!!.first.first ||
                (score.first == bestPair!!.first.first && score.second > bestPair!!.first.second)
            ) {
                bestPair = Triple(score, a, b)
            }
        }
    }
    Log.d(TAG, "term-structure bestPair=$bestPair")
    bestPair?.let { (_, issue, expiry) ->
        offer("Date of Issue", issue, STRONG)
        offer("Date of Expiry", expiry, STRONG)
    }
    Log.d(TAG, "after term-structure: out=$out")

    // 2b. Label anchoring for Issue/Expiry -- only as a fallback when term
    // structure above couldn't find a valid pair (e.g. only one of the two
    // dates came through legibly), since it's the less reliable signal here.
    for ((kind, field, check) in listOf(
        Triple("issue", "Date of Issue", ::plausibleIssue),
        Triple("expiry", "Date of Expiry", ::plausibleExpiry)
    )) {
        if (out[field] != null) continue
        for (d in dated) {
            if (!check(d.date, java.util.GregorianCalendar())) continue
            if (d.label == kind) {
                offer(field, d.date, STRONG)
                break
            }
            if (d.lineIdx > 0 && labels[d.lineIdx - 1] == kind) {
                offer(field, d.date, STRONG)
                break
            }
        }
    }
    Log.d(TAG, "after issue/expiry label-anchoring fallback: out=$out")

    // 4. Ordering fallback for anything still missing: birth < issue < expiry.
    val known = out.values.map { it.value }.toSet()
    var spare = allDates.filter { it !in known }

    if (out["Date of Birth"] == null) {
        val births = spare.filter { plausibleDob(it) }
        if (births.isNotEmpty()) {
            val earliest = births.minByOrNull { asDate(it)!!.timeInMillis }!!
            if (allDates.all { asDate(earliest)!!.timeInMillis <= asDate(it)!!.timeInMillis }) {
                offer("Date of Birth", earliest, WEAK)
                spare = spare.filter { it != earliest }
            }
        }
    }

    if (out["Date of Issue"] == null && out["Date of Expiry"] != null) {
        val expiryD = asDate(out["Date of Expiry"]!!.value)!!.timeInMillis
        val earlier = spare.filter { plausibleIssue(it) && asDate(it)!!.timeInMillis < expiryD }
        if (earlier.isNotEmpty()) offer("Date of Issue", earlier.maxByOrNull { asDate(it)!!.timeInMillis }, WEAK)
    } else if (out["Date of Expiry"] == null && out["Date of Issue"] != null) {
        val issueD = asDate(out["Date of Issue"]!!.value)!!.timeInMillis
        val later = spare.filter { plausibleExpiry(it) && asDate(it)!!.timeInMillis > issueD }
        if (later.isNotEmpty()) offer("Date of Expiry", later.minByOrNull { asDate(it)!!.timeInMillis }, WEAK)
    } else if (out["Date of Issue"] == null && out["Date of Expiry"] == null && spare.size >= 2) {
        val ordered = spare.sortedBy { asDate(it)!!.timeInMillis }
        if (plausibleIssue(ordered.first()) && plausibleExpiry(ordered.last())) {
            offer("Date of Issue", ordered.first(), WEAK)
            offer("Date of Expiry", ordered.last(), WEAK)
        }
    }

    // Never emit an inconsistent pair -- one of the two was misread.
    val issueCand = out["Date of Issue"]
    val expiryCand = out["Date of Expiry"]
    if (issueCand != null && expiryCand != null && !dateBefore(issueCand.value, expiryCand.value)) {
        Log.d(TAG, "removing inconsistent issue/expiry pair: issue=${issueCand.value} expiry=${expiryCand.value}")
        out.remove("Date of Issue")
        out.remove("Date of Expiry")
    }
    val dobCand = out["Date of Birth"]
    val issueCand2 = out["Date of Issue"]
    if (dobCand != null && issueCand2 != null && !dateBefore(dobCand.value, issueCand2.value)) {
        Log.d(TAG, "removing inconsistent dob/issue: dob=${dobCand.value} issue=${issueCand2.value}")
        out.remove("Date of Birth")
    }
    Log.d(TAG, "after ordering-fallback+consistency: out=$out")

    // ── Names ─────────────────────────────────────────────────────────────
    val clusters = nameClusters(lines)
    Log.d(TAG, "nameClusters: ${clusters.map { "${it.value}@y=${it.y} variants=${it.variants.size} reads=${it.reads}" }}")
    var corroborated = clusters.filter { it.variants.size >= 2 || it.reads >= 2 }
    // scan_single_image (the phone-photo, single-shot entry point) has no
    // second capture cycle to fall back on -- see mod_ocr.py's comment at
    // the equivalent point. Fall back to every syntactically-valid cluster
    // instead of leaving the fields empty.
    if (corroborated.isEmpty()) corroborated = clusters

    // Computed once, ahead of sameRowCluster's fallback below, since a
    // dominated (substring-of-a-longer-read) cluster shouldn't win there
    // either.
    val dominated = clusters.filter { candidate ->
        clusters.any { other ->
            other !== candidate &&
                other.value.length > candidate.value.length &&
                other.value.contains(candidate.value, ignoreCase = true)
        }
    }.toSet()

    val fatherRowsAll = labels.indices.filter { labels[it] == "father" }
    val nameRowsAll = labels.indices.filter { labels[it] == "name" }
    Log.d(TAG, "labels: fatherRowsAll=${fatherRowsAll.map { texts[it] }} nameRowsAll=${nameRowsAll.map { texts[it] }}")

    var fatherRows = fatherRowsAll
    if (fatherRowsAll.isNotEmpty() && nameRowsAll.isNotEmpty()) {
        val topmostNameY = nameRowsAll.minOf { lines[it].yFrac }
        fatherRows = fatherRowsAll.filter { lines[it].yFrac >= topmostNameY - 0.01 }
    }
    val nameRows = nameRowsAll

    val labelPositionMinConf = 0.40
    val fatherWindowRows = fatherRows.filter { lines[it].conf >= labelPositionMinConf }
    val nameWindowRows = nameRows.filter { lines[it].conf >= labelPositionMinConf }

    fun sameRowCluster(i: Int, keywords: Set<String>): NameCluster? {
        val rest = stripLabel(texts[i], keywords) ?: return null
        if (!isValidName(rest)) return null
        val norm = normName(rest)
        // Prefer a multiply-confirmed cluster, but a single-read cluster
        // sitting directly on the same line as the "Name"/"Father Name"
        // label is still strong evidence -- the label itself corroborates
        // it, so don't require a second independent OCR read too.
        return corroborated.firstOrNull { similar(norm.lowercase(), it.value.lowercase()) >= NAME_CLUSTER_SIM }
            ?: clusters.filterNot { it in dominated }
                .firstOrNull { similar(norm.lowercase(), it.value.lowercase()) >= NAME_CLUSTER_SIM }
    }

    data class Candidate(val priority: Double, val kind: String, val cluster: NameCluster)
    val candidates = mutableListOf<Candidate>()

    for ((kind, rows, keywords) in listOf(
        Triple("father", fatherRows, LBL_FATHER),
        Triple("name", nameRows, LBL_NAME)
    )) {
        for (i in rows) {
            sameRowCluster(i, keywords)?.let { candidates.add(Candidate(0.0, kind, it)) }
        }
    }

    // Every non-dominated cluster is eligible here, not just corroborated
    // ones -- a name that only one OCR pass happened to read cleanly this
    // capture is still real if it sits right under a "Father Name"/"Name"
    // label; requiring a second independent read on top of that positional
    // evidence was silently dropping genuine father names some captures
    // caught only once. Corroborated clusters still win when both are
    // candidates for the same slot (priority penalty below), and an
    // uncorroborated single read needs a real confidence margin, not just
    // a lucky Y-position, since it's the only piece of evidence for it.
    val singleReadMinConf = 0.55
    for (c in clusters) {
        if (c in dominated) continue
        val corroborated2plus = c.variants.size >= 2 || c.reads >= 2
        if (!corroborated2plus && c.conf < singleReadMinConf) continue
        val corroborationPenalty = if (corroborated2plus) 0.0 else 0.5
        // A cleaner OCR read is more likely to be the right one than a
        // garbled misread of the same row when both are otherwise similar
        // candidates (position, corroboration) -- e.g. "Muhammad Nadeem
        // Akhtar" (conf ~0.8) vs "Nuhar Nelad Deem Akhtar" (conf ~0.4) for
        // the same father-name row.
        val confBonus = 0.05 * c.conf
        for ((kind, rows) in listOf("father" to fatherWindowRows, "name" to nameWindowRows)) {
            for (i in rows) {
                val dy = c.y - lines[i].yFrac
                if (dy in -0.01..NAME_Y_WINDOW) candidates.add(Candidate(1.0 + dy + corroborationPenalty - confBonus, kind, c))
            }
        }
    }

    // Tracked by reference identity (like Python's id()), not value equality --
    // two distinct rows could coincidentally carry identical field values.
    val usedKinds = mutableSetOf<String>()
    val usedClusters = mutableListOf<NameCluster>()
    var nameCluster: NameCluster? = null
    var fatherCluster: NameCluster? = null
    for (cand in candidates.sortedBy { it.priority }) {
        if (cand.kind in usedKinds || usedClusters.any { it === cand.cluster }) continue
        offer(if (cand.kind == "father") "Father Name" else "Name", cand.cluster.value, WEAK)
        if (cand.kind == "father") fatherCluster = cand.cluster else nameCluster = cand.cluster
        usedKinds.add(cand.kind)
        usedClusters.add(cand.cluster)
    }

    // The holder's row always prints above the father's on a CNIC -- if the
    // two got assigned the other way round (can happen when noisy OCR
    // duplicates put a label line's detected Y in the wrong place), swap
    // them back rather than keep an inverted result.
    if (nameCluster != null && fatherCluster != null && nameCluster.y > fatherCluster.y) {
        Log.d(TAG, "swapping inverted Name/Father Name: name@y=${nameCluster.y} father@y=${fatherCluster.y}")
        val nameWeight = out["Name"]!!.weight
        val fatherWeight = out["Father Name"]!!.weight
        out["Name"] = Cand(fatherCluster.value, fatherWeight)
        out["Father Name"] = Cand(nameCluster.value, nameWeight)
    }

    // 2. Printed order fallback: holder's row sits directly above father's.
    // Draws from every non-dominated cluster (not just corroborated ones)
    // since this is already the last-resort path for a field the steps
    // above missed.
    fun freshAfter(assigned: List<String>): List<NameCluster> =
        clusters.filterNot { it in dominated }
            .filter { c -> assigned.all { similar(c.value.lowercase(), it.lowercase()) < NAME_CLUSTER_SIM } }

    val assigned = out.entries.filter { it.key == "Name" || it.key == "Father Name" }.map { it.value.value }
    val fresh = freshAfter(assigned)

    when {
        out["Name"] == null && out["Father Name"] == null && fresh.size >= 2 -> {
            offer("Name", fresh[0].value, WEAK)
            offer("Father Name", fresh[1].value, WEAK)
        }
        out["Name"] != null && out["Father Name"] == null -> {
            val below = fresh.filter { it.y > 0.2 }
            if (below.isNotEmpty()) offer("Father Name", below.last().value, WEAK)
        }
        out["Father Name"] != null && out["Name"] == null && fresh.isNotEmpty() -> {
            offer("Name", fresh[0].value, WEAK)
        }
        fresh.size == 1 && out["Name"] == null && out["Father Name"] == null -> {
            val c = fresh[0]
            offer(if (c.y < 0.38) "Name" else "Father Name", c.value, WEAK)
        }
    }
    Log.d(TAG, "final: out=$out")

    return out
}
