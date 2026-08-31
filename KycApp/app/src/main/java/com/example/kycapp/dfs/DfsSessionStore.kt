package com.example.kycapp.dfs

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

data class DfsSession(
    val sessionToken: String,
    val appUserId: Long,
    val phone: String,
    val fullName: String,
    val email: String?,
    val status: String,
    val businessName: String?,
    val trackingId: String?,
    val canSubmit: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sessionToken", sessionToken)
        put("appUserId", appUserId)
        put("phone", phone)
        put("fullName", fullName)
        put("email", email ?: JSONObject.NULL)
        put("status", status)
        put("businessName", businessName ?: JSONObject.NULL)
        put("trackingId", trackingId ?: JSONObject.NULL)
        put("canSubmit", canSubmit)
    }

    companion object {
        fun fromJson(o: JSONObject): DfsSession = DfsSession(
            sessionToken = o.getString("sessionToken"),
            appUserId = o.optLong("appUserId"),
            phone = o.optString("phone"),
            fullName = o.optString("fullName"),
            email = o.optString("email").takeIf { it.isNotBlank() && it != "null" },
            status = o.optString("status"),
            businessName = o.optString("businessName").takeIf { it.isNotBlank() && it != "null" },
            trackingId = o.optString("trackingId").takeIf { it.isNotBlank() && it != "null" },
            canSubmit = o.optBoolean("canSubmit", false)
        )
    }
}

class DfsSessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dfs_kyc_session", Context.MODE_PRIVATE)

    fun save(session: DfsSession) {
        prefs.edit().putString(KEY, session.toJson().toString()).apply()
    }

    fun load(): DfsSession? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching { DfsSession.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "session"
    }
}
