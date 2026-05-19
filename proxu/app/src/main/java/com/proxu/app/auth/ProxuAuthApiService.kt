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

    fun loginWithGoogle(idToken: String, email: String = "", name: String = ""): AuthResponse {
        return authRequest("google-mobile", idToken, email, name)
    }

    /**
     * Attempts to auto-register a new Google user on the server.
     * Sends same payload but with auto_register flag so server creates account if not exists.
     */
    fun registerWithGoogle(idToken: String, email: String = "", name: String = ""): AuthResponse {
        return authRequest("google-mobile", idToken, email, name, autoRegister = true)
    }

    private fun authRequest(
        endpoint: String,
        idToken: String,
        email: String = "",
        name: String = "",
        autoRegister: Boolean = false
    ): AuthResponse {
        val json = JSONObject()
            .put("id_token", idToken)
            .put("email", email)
            .put("name", name)
        if (autoRegister) {
            json.put("auto_register", true)
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/public/auth/$endpoint")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            LogUtil.d("ProxuAuthApiService", "Auth response body (autoRegister=$autoRegister): $body")
            return AuthResponse(
                code = response.code,
                body = body,
                token = body.extractJsonString("token"),
                refreshToken = body.extractJsonString("refresh_token"),
                message = body.extractJsonString("message"),
                error = body.extractJsonString("error"),
                balance = body.extractJsonString("balance")
            )
        }
    }

    private fun String.extractJsonString(key: String): String? {
        return runCatching {
            if (isBlank()) return@runCatching null
            val json = JSONObject(this)
            if (json.has(key) && !json.isNull(key)) {
                json.getString(key).takeIf { it.isNotBlank() }
            } else {
                null
            }
        }.getOrNull()
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
