package com.proxu.app.auth

import com.proxu.app.util.LogUtil
import org.json.JSONObject

data class ProxuProxy(
    val id: String,
    val name: String,
    val server: String,
    val port: Int,
    val protocol: String,
    val username: String?,
    val password: String?,
    val encryption: String?,
    val extra: JSONObject?,
    val link: String? = null
)

data class ProxuVpnServer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val country: String?,
    val city: String?
)

data class ProxuVpnConfig(
    val id: String,
    val serverId: String,
    val config: String
)

data class ProxuUserProfile(
    val id: String,
    val email: String,
    val name: String?,
    val balance: String?
)

data class ProxuApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
    val error: String?
)

object ProxuApiModels {
    fun parseProxy(json: JSONObject): ProxuProxy? {
        return try {
            ProxuProxy(
                id = json.getString("id"),
                name = json.optString("name", ""),
                server = json.getString("server"),
                port = json.getInt("port"),
                protocol = json.optString("protocol", "vless"),
                username = json.optString("username").takeIf { it.isNotBlank() },
                password = json.optString("password").takeIf { it.isNotBlank() },
                encryption = json.optString("encryption").takeIf { it.isNotBlank() },
                extra = json.optJSONObject("extra")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseVpnServer(json: JSONObject): ProxuVpnServer? {
        return try {
            val id = when {
                json.has("id") && !json.isNull("id") -> {
                    when (json.get("id")) {
                        is String -> json.getString("id")
                        is Int -> json.getInt("id").toString()
                        else -> json.get("id").toString()
                    }
                }
                else -> ""
            }
            ProxuVpnServer(
                id = id,
                name = json.optString("name", ""),
                host = json.getString("host"),
                port = json.optInt("port", 443),
                country = json.optString("country").takeIf { it.isNotBlank() },
                city = json.optString("city").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseVpnConfig(json: JSONObject): ProxuVpnConfig? {
        return try {
            val id = json.optString("id", "").takeIf { it.isNotBlank() }
                ?: json.optString("vpn_id", "").takeIf { it.isNotBlank() }
            val config = json.optString("link", "").takeIf { it.isNotBlank() }
                ?: json.optString("config", "").takeIf { it.isNotBlank() }
            val serverId = json.optString("server_id", "").takeIf { it.isNotBlank() }
            
            if (id.isNullOrBlank()) {
                LogUtil.e("ProxuApiModels", "parseVpnConfig: no id found in $json")
                return null
            }
            
            ProxuVpnConfig(
                id = id,
                serverId = serverId ?: "",
                config = config ?: ""
            )
        } catch (e: Exception) {
            LogUtil.e("ProxuApiModels", "parseVpnConfig failed", e)
            null
        }
    }

    fun parseUserProfile(json: JSONObject): ProxuUserProfile? {
        return try {
            val balanceValue = json.optDouble("balance", -1.0)
            val balanceStr = if (balanceValue >= 0) balanceValue.toInt().toString() else null
            ProxuUserProfile(
                id = json.getString("id"),
                email = json.getString("email"),
                name = json.optString("display_name").takeIf { it.isNotBlank() },
                balance = balanceStr
            )
        } catch (e: Exception) {
            null
        }
    }
}
