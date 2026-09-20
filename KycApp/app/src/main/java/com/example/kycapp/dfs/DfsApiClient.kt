package com.example.kycapp.dfs

import com.example.kycapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DfsApiException(message: String) : Exception(message)

object DfsApiClient {

    private val baseUrl: String
        get() = BuildConfig.DFS_API_BASE.trimEnd('/')

    suspend fun openInvite(token: String): DfsSession = withContext(Dispatchers.IO) {
        request("GET", "/api/public/app-kyc/invite/${token.trim()}")
    }

    suspend fun login(phone: String, pin: String): DfsSession = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/api/public/app-kyc/login",
            body = JSONObject().put("phone", phone.trim()).put("pin", pin.trim())
        )
    }

    suspend fun me(sessionToken: String): DfsSession = withContext(Dispatchers.IO) {
        request("GET", "/api/public/app-kyc/me", sessionToken = sessionToken)
    }

    /** Native path: mark partner KYC complete after on-device biometrics. */
    suspend fun complete(sessionToken: String): DfsSession = withContext(Dispatchers.IO) {
        request("POST", "/api/public/app-kyc/complete", sessionToken = sessionToken)
    }

    /** Report phone KYC failure (face mismatch etc). After 3 fails → bank visit. */
    suspend fun fail(sessionToken: String, reason: String): DfsSession = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/api/public/app-kyc/fail",
            sessionToken = sessionToken,
            body = JSONObject().put("reason", reason)
        )
    }

    /** Upload one signature photo (multipart). */
    suspend fun uploadSignature(sessionToken: String, file: java.io.File): DfsSession =
        withContext(Dispatchers.IO) {
            multipart(
                path = "/api/public/app-kyc/signature",
                sessionToken = sessionToken,
                fieldName = "signature",
                file = file,
                mimeType = "image/jpeg"
            )
        }

    private fun multipart(
        path: String,
        sessionToken: String,
        fieldName: String,
        file: java.io.File,
        mimeType: String
    ): DfsSession {
        val boundary = "----DfsKyc${System.currentTimeMillis()}"
        val url = URL("$baseUrl$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 60000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $sessionToken")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            conn.outputStream.use { out ->
                val writer = java.io.DataOutputStream(out)
                writer.writeBytes("--$boundary\r\n")
                writer.writeBytes(
                    "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"${file.name}\"\r\n"
                )
                writer.writeBytes("Content-Type: $mimeType\r\n\r\n")
                writer.flush()
                file.inputStream().use { it.copyTo(out) }
                writer.writeBytes("\r\n--$boundary--\r\n")
                writer.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw DfsApiException(if (text.isBlank()) "Empty response ($code)" else text.take(200))
            }
            if (code !in 200..299) {
                val err = json.optString("error").ifBlank { json.optString("message") }
                throw DfsApiException(err.ifBlank { "Request failed ($code)" })
            }
            return parseSession(json)
        } finally {
            conn.disconnect()
        }
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        sessionToken: String? = null
    ): DfsSession {
        val url = URL("$baseUrl$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
            if (sessionToken != null) {
                setRequestProperty("Authorization", "Bearer $sessionToken")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
            val ct = conn.contentType.orEmpty()
            if (!ct.contains("json", ignoreCase = true) && text.trimStart().startsWith("<")) {
                throw DfsApiException(
                    "API returned HTML instead of JSON. Check DFS_API_BASE=$baseUrl (backend on 8090? same Wi‑Fi?)"
                )
            }
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw DfsApiException(if (text.isBlank()) "Empty response ($code)" else text.take(200))
            }
            if (code !in 200..299) {
                val err = json.optString("error").ifBlank { json.optString("message") }
                throw DfsApiException(err.ifBlank { "Request failed ($code)" })
            }
            return parseSession(json)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseSession(o: JSONObject): DfsSession = DfsSession(
        sessionToken = o.getString("sessionToken"),
        appUserId = o.optLong("appUserId"),
        phone = o.optString("phone"),
        fullName = o.optString("fullName"),
        email = o.optString("email").takeIf { it.isNotBlank() },
        status = o.optString("status"),
        businessName = o.optString("businessName").takeIf { it.isNotBlank() },
        trackingId = o.optString("trackingId").takeIf { it.isNotBlank() },
        canSubmit = o.optBoolean("canSubmit", false)
    )
}
