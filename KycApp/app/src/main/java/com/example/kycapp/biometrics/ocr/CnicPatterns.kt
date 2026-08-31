package com.example.kycapp.biometrics.ocr

import java.util.Calendar
import java.util.GregorianCalendar

// Ports mod_ocr.py's pattern/validation section verbatim -- same character
// classes work identically in Java/Kotlin regex as in Python's re module.
private const val DIGITISH = "0-9OoQDIl|!ijSsZzBGgTbAe"

val DATE_RE = Regex("""\b(\d{2}[.\-/]\d{2}[.\-/]\d{4}|\d{4}[.\-/]\d{2}[.\-/]\d{2})\b""")
val DATE_LOOSE = Regex(
    "(?<![0-9])([$DIGITISH]{1,2})\\s*[.,\\-/:_ ]\\s*([$DIGITISH]{1,2})\\s*[.,\\-/:_ ]\\s*([$DIGITISH]{4})(?![0-9])"
)
val DATE_COMPACT = Regex("(?<![0-9])([$DIGITISH]{8})(?![0-9])")

val CNIC_RE = Regex("""\b(\d{5})[\s\-]?(\d{7})[\s\-]?(\d)\b""")
val CNIC_LOOSE = Regex(
    "(?<![0-9])([$DIGITISH]{5})\\s*[\\s\\-.,:_~]?\\s*([$DIGITISH]{7})\\s*[\\s\\-.,:_~]?\\s*([$DIGITISH])(?![0-9])"
)

val NAME_RE = Regex("""^[A-Za-z][A-Za-z .'\-]{2,50}$""")
val NAME_TOKEN_RE = Regex("[A-Za-z][A-Za-z\\-]*")

private val DIGIT_LOOKALIKES: Map<Char, Char> = buildMap {
    put('O', '0'); put('o', '0'); put('Q', '0'); put('D', '0'); put('e', '0')
    put('I', '1'); put('l', '1'); put('|', '1'); put('!', '1'); put('i', '1'); put('j', '1')
    put('Z', '2'); put('z', '2')
    put('A', '4')
    put('S', '5'); put('s', '5')
    put('G', '6'); put('b', '6')
    put('T', '7')
    put('B', '8')
    put('g', '9')
}

/** Maps digit-lookalike letters to digits, then keeps digits only. */
fun digitize(text: String): String {
    val translated = text.map { DIGIT_LOOKALIKES[it] ?: it }.joinToString("")
    return translated.filter { it.isDigit() }
}

/** 13 digits, leading province code 1-8, and not a run of one repeated digit. */
fun validateCnic(cnicStr: String): Boolean {
    val digits = cnicStr.replace("-", "")
    if (digits.length != 13 || !digits.all { it.isDigit() }) return false
    if (digits[0] !in '1'..'8') return false
    if (digits.toSet().size <= 2) return false
    return true
}

/** Every CNIC-shaped run in [text], strict matches first then digit-lookalike ones. */
fun findCnics(text: String): List<String> {
    val found = mutableListOf<String>()
    for (m in CNIC_RE.findAll(text)) {
        val (g1, g2, g3) = m.destructured
        val cand = "$g1-$g2-$g3"
        if (validateCnic(cand) && cand !in found) found.add(cand)
    }
    for (m in CNIC_LOOSE.findAll(text)) {
        val digits = digitize(m.groupValues[1] + m.groupValues[2] + m.groupValues[3])
        if (digits.length != 13) continue
        val cand = "${digits.substring(0, 5)}-${digits.substring(5, 12)}-${digits.substring(12)}"
        if (validateCnic(cand) && cand !in found) found.add(cand)
    }
    return found
}

/** (year, month, day) from a DD.MM.YYYY or YYYY.MM.DD string, or null. */
fun dateToYmd(dateStr: String): Triple<Int, Int, Int>? {
    Regex("""^(\d{2})[.\-/](\d{2})[.\-/](\d{4})$""").matchEntire(dateStr)?.let { m ->
        val (d, mo, y) = m.destructured
        return Triple(y.toInt(), mo.toInt(), d.toInt())
    }
    Regex("""^(\d{4})[.\-/](\d{2})[.\-/](\d{2})$""").matchEntire(dateStr)?.let { m ->
        val (y, mo, d) = m.destructured
        return Triple(y.toInt(), mo.toInt(), d.toInt())
    }
    return null
}

fun validateDate(dateStr: String): Boolean {
    val (y, mo, d) = dateToYmd(dateStr) ?: return false
    if (mo !in 1..12 || d !in 1..31 || y !in 1900..2100) return false
    return try {
        val cal = GregorianCalendar()
        cal.isLenient = false
        cal.set(y, mo - 1, d)
        cal.time // throws IllegalArgumentException on e.g. Feb 31 when non-lenient
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}

/** Every date in [text], normalised to DD.MM.YYYY. */
fun findDates(text: String): List<String> {
    val found = mutableListOf<String>()
    fun add(d: String, mo: String, y: String) {
        val di = d.toIntOrNull() ?: return
        val moi = mo.toIntOrNull() ?: return
        val yi = y.toIntOrNull() ?: return
        val cand = "%02d.%02d.%04d".format(di, moi, yi)
        if (validateDate(cand) && cand !in found) found.add(cand)
    }

    for (raw in DATE_RE.findAll(text).map { it.groupValues[1] }) {
        dateToYmd(raw)?.let { (y, mo, d) -> add(d.toString(), mo.toString(), y.toString()) }
    }
    for (m in DATE_LOOSE.findAll(text)) {
        val d = digitize(m.groupValues[1]); val mo = digitize(m.groupValues[2]); val y = digitize(m.groupValues[3])
        if (d.isNotEmpty() && mo.isNotEmpty() && y.length == 4) add(d, mo, y)
    }
    for (m in DATE_COMPACT.findAll(text)) {
        val digits = digitize(m.groupValues[1])
        if (digits.length == 8) add(digits.substring(0, 2), digits.substring(2, 4), digits.substring(4, 8))
    }
    return found
}

private fun asCalendar(dateStr: String): Calendar? {
    val (y, mo, d) = dateToYmd(dateStr) ?: return null
    return try {
        GregorianCalendar(y, mo - 1, d)
    } catch (e: Exception) {
        null
    }
}

fun asDate(dateStr: String): Calendar? = asCalendar(dateStr)

private fun daysBetween(a: Calendar, b: Calendar): Long {
    val msPerDay = 24L * 60 * 60 * 1000
    return (b.timeInMillis - a.timeInMillis) / msPerDay
}

/** Nobody is born in the future, or more than 120 years ago. */
fun plausibleDob(dateStr: String, today: Calendar = GregorianCalendar()): Boolean {
    val d = asDate(dateStr) ?: return false
    val days = daysBetween(d, today)
    return days in 0..(120L * 366)
}

/** CNICs have been issued since 2000; a card cannot be issued in the future. */
fun plausibleIssue(dateStr: String, today: Calendar = GregorianCalendar()): Boolean {
    val d = asDate(dateStr) ?: return false
    return d.get(Calendar.YEAR) >= 2000 && daysBetween(d, today) >= -1
}

/** Expiry is in the future or the recent past, never decades away. */
fun plausibleExpiry(dateStr: String, today: Calendar = GregorianCalendar()): Boolean {
    val d = asDate(dateStr) ?: return false
    val year = d.get(Calendar.YEAR)
    return year in 2005..(today.get(Calendar.YEAR) + 25)
}

private const val TERM_SLACK_DAYS = 45

/** The printed validity term (years) if issue/expiry are exactly one apart, else null. */
fun termYears(issueStr: String, expiryStr: String): Int? {
    val a = asDate(issueStr) ?: return null
    val b = asDate(expiryStr) ?: return null
    if (!b.after(a)) return null
    val days = daysBetween(a, b)
    for (years in intArrayOf(5, 7, 10, 15, 20)) {
        if (kotlin.math.abs(days - years * 365.2425) <= TERM_SLACK_DAYS) return years
    }
    return null
}

/** True if date a precedes date b -- or if either fails to parse (don't block). */
fun dateBefore(aStr: String, bStr: String): Boolean {
    val a = dateToYmd(aStr) ?: return true
    val b = dateToYmd(bStr) ?: return true
    if (a.first != b.first) return a.first < b.first
    if (a.second != b.second) return a.second < b.second
    return a.third < b.third
}
