package com.example.kycapp.biometrics.fingerprint

import android.util.Base64
import org.jnbis.WSQDecoder
import org.jnbis.WSQEncoder
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Matches NBIS's own default fingerprint compression rate (cwsq -r 0.75) and
// the 500dpi assumption already baked into _write_iso19794_record's header.
private const val WSQ_BIT_RATE = 0.75
private const val FINGERPRINT_PPI = 500
private const val ISO_HEADER_BYTES = 44

data class FingerprintExports(val wsq: ByteArray, val base64: String, val iso: ByteArray)

/** Ports mod_fingerprint.py's export_fingerprint_formats() -- WSQ / Base64 / ISO. */
fun exportFingerprintFormats(processedGray: Mat): FingerprintExports {
    val gray = grayBytes(processedGray)
    val width = processedGray.cols()
    val height = processedGray.rows()
    return FingerprintExports(
        wsq = encodeWsq(gray, width, height),
        base64 = encodeBase64Png(processedGray),
        iso = encodeIso19794(gray, width, height)
    )
}

private fun grayBytes(mat: Mat): ByteArray {
    val bytes = ByteArray(mat.rows() * mat.cols())
    mat.get(0, 0, bytes)
    return bytes
}

private fun encodeWsq(gray: ByteArray, width: Int, height: Int): ByteArray {
    val bitmap = org.jnbis.Bitmap(gray, width, height, FINGERPRINT_PPI, 8, 1)
    val out = ByteArrayOutputStream()
    WSQEncoder.encode(out, bitmap, WSQ_BIT_RATE)
    return out.toByteArray()
}

private fun encodeBase64Png(gray: Mat): String {
    val buf = MatOfByte()
    Imgcodecs.imencode(".png", gray, buf)
    return Base64.encodeToString(buf.toArray(), Base64.NO_WRAP)
}

/**
 * Byte-for-byte port of mod_fingerprint.py's _write_iso19794_record: a
 * simplified ISO/IEC 19794-4-inspired finger image record -- 44-byte header
 * (format id, version, record length, fixed 500dpi resolution fields, image
 * width/height at bytes 39:41 / 41:43) followed by raw 8-bit grayscale pixels.
 */
private fun encodeIso19794(gray: ByteArray, width: Int, height: Int): ByteArray {
    val recordLength = ISO_HEADER_BYTES + gray.size
    val header = ByteBuffer.allocate(ISO_HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
    header.put(byteArrayOf('F'.code.toByte(), 'I'.code.toByte(), 'R'.code.toByte(), 0))
    header.put(byteArrayOf('0'.code.toByte(), '2'.code.toByte(), '0'.code.toByte(), 0))
    header.putInt(recordLength)
    header.putShort(0); header.putShort(0)
    header.putShort(0)
    header.putShort(0)
    header.put(1)
    header.put(1)
    header.putShort(500)
    header.putShort(500)
    header.putShort(500)
    header.putShort(500)
    header.put(8)
    header.put(0)
    header.putShort(0)
    header.put(0)
    header.put(1)
    header.put(1)
    header.put(0)
    header.put(0)
    header.putShort(width.toShort())
    header.putShort(height.toShort())
    header.put(0)
    check(header.position() == ISO_HEADER_BYTES)
    return header.array() + gray
}

/** Decodes a WSQ record back to raw grayscale pixels + dimensions, for preview/round-trip display. */
fun decodeWsq(bytes: ByteArray): Triple<ByteArray, Int, Int> {
    val decoded = WSQDecoder.decode(ByteArrayInputStream(bytes))
    return Triple(decoded.pixels, decoded.width, decoded.height)
}

/** Ports mod_fingerprint.py's read_iso19794_record(). */
fun decodeIso19794(bytes: ByteArray): Triple<ByteArray, Int, Int> {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    val width = buffer.getShort(39).toInt() and 0xFFFF
    val height = buffer.getShort(41).toInt() and 0xFFFF
    val pixels = bytes.copyOfRange(ISO_HEADER_BYTES, bytes.size)
    return Triple(pixels, width, height)
}

/** Decodes a Base64-encoded PNG export back to raw grayscale pixels + dimensions. */
fun decodeBase64Png(base64: String): Triple<ByteArray, Int, Int> {
    val png = Base64.decode(base64, Base64.NO_WRAP)
    val mat = Imgcodecs.imdecode(MatOfByte(*png), Imgcodecs.IMREAD_GRAYSCALE)
    val pixels = grayBytes(mat)
    return Triple(pixels, mat.cols(), mat.rows())
}
