package com.proxu.app.auth

import com.proxu.app.util.LogUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProxuAuthApiService {
    private const val BASE_URL = "https://proxu.pro"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * New unified endpoint that handles both login and auto-registration.
     * Server automatically creates new users with 50 rub balance.
     *
     * POST /api/public/auth/google/api
     * Body: {"id_token": "..."}
     * Response: {"success": true, "token": "...", "user": {"balance": 50, "is_new": true}}
     */
    fun loginWithGoogle(idToken: String): AuthResponse {
        val json = JSONObject().put("id_token", idToken).toString()

        val request = Request.Builder()
            .url("$BASE_URL/api/public/auth/google/api")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            LogUtil.d("ProxuAuthApiService", "Auth response: HTTP ${response.code}, body: ${body.take(500)}")

            return parseAuthResponse(response.code, body)
        }
    }

    private fun parseAuthResponse(code: Int, body: String): AuthResponse {
        return try {
            if (body.isBlank()) {
                return AuthResponse(code, body, null, null, null, "Empty response", null)
            }

            val json = JSONObject(body)

            // New API format: {"success": true, "token": "...", "user": {...}}
            val success = json.optBoolean("success", false)
            val token = json.optString("token", "").takeIf { it.isNotBlank() }
            val refreshToken = json.optString("refresh_token", "").takeIf { it.isNotBlank() }

            // Extract balance from user object
            val userObj = json.optJSONObject("user")
            val balance = userObj?.optDouble("balance", -1.0)
                ?.takeIf { it >= 0 }
                ?.toInt()
                ?.toString()

            // Extract is_new flag for logging
            val isNew = userObj?.optBoolean("is_new", false) ?: false
            if (isNew) {
                LogUtil.i("ProxuAuthApiService", "New user registered automatically!")
            }

            val error = if (!success && code != 200) {
                json.optString("error", json.optString("message", "Unknown error"))
            } else null

            AuthResponse(
                code = code,
                body = body,
                token = token,
                refreshToken = refreshToken,
                message = json.optString("message", "").takeIf { it.isNotBlank() },
                error = error,
                balance = balance
            )
        } catch (e: Exception) {
            LogUtil.e("ProxuAuthApiService", "Failed to parse auth response", e)
            AuthResponse(code, body, null, null, null, "Parse error: ${e.message}", null)
        }
    }
}

data class AuthResponse(
    val code: Int,
    val body: String,
    val token: String?,
    val refreshToken: String?,
    val message: String?,
    val error: String?,
    val balance: String?
)
