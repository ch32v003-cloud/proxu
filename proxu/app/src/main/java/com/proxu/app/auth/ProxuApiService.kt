package com.proxu.app.auth

import com.proxu.app.util.LogUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProxuApiService {
    private const val BASE_URL = "https://proxu.pro/api/user"
    private const val TAG = "ProxuApiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun createAuthRequest(token: String, url: String, method: String = "GET", body: String? = null): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
        return when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonMediaType))
            "PUT" -> builder.put((body ?: "{}").toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
    }

    fun getProxies(token: String): List<ProxuProxy>? {
        val request = createAuthRequest(token, "$BASE_URL/proxies").build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (response.code != 200) {
                    return null
                }
                val jsonArray = JSONArray(body)
                val list = mutableListOf<ProxuProxy>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i)
                    obj?.let {
                        // Map the API response to ProxuProxy
                        val proxy = ProxuProxy(
                            id = it.getString("id"),
                            name = it.optString("name", ""), // API doesn't provide name, use empty
                            server = it.optString("ip", it.optString("domain", "")), // Try ip first, then domain
                            port = it.optString("port").toIntOrNull() ?: 0, // Port comes as string
                            protocol = determineProtocol(it),
                            username = it.optString("proxy_user").takeIf { it.isNotBlank() },
                            password = it.optString("proxy_pass").takeIf { it.isNotBlank() },
                            encryption = null, // Not provided in API
                            extra = null // Not provided in API
                        )
                        list.add(proxy)
                    }
                }
                list
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun determineProtocol(jsonObject: JSONObject): String {
        val protocolFromApi = jsonObject.optString("protocol", "").lowercase()
        return when {
            protocolFromApi == "vless" || protocolFromApi == "vmess" || protocolFromApi == "trojan" || 
                    protocolFromApi == "shadowsocks" || protocolFromApi == "socks5" || protocolFromApi == "socks" || 
                    protocolFromApi == "hy2" || protocolFromApi == "hysteria2" -> protocolFromApi
            protocolFromApi == "vpn" -> {
                // Try to extract from connection_string
                val conn = jsonObject.optString("connection_string", "").lowercase()
                return when {
                    conn.startsWith("vless://") -> "vless"
                    conn.startsWith("vmess://") -> "vmess"
                    conn.startsWith("trojan://") -> "trojan"
                    conn.startsWith("ss://") -> "shadowsocks"
                    conn.startsWith("socks5://") || conn.startsWith("socks://") -> "socks"
                    conn.startsWith("hy2://") || conn.startsWith("hysteria2://") -> "hysteria2"
                    else -> "vless" // default
                }
            }
            else -> "vless" // default fallback
        }
    }

    fun getProfile(token: String): ProxuUserProfile? {
        val request = createAuthRequest(token, "$BASE_URL/profile").build()
        return executeRequest(request) { json ->
            ProxuApiModels.parseUserProfile(json)
        }
    }

    fun getProfileRaw(token: String): JSONObject? {
        val request = createAuthRequest(token, "$BASE_URL/profile").build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                LogUtil.e("ProxuApiService", "getProfileRaw HTTP ${response.code}: ${body.take(500)}")
                if (response.code != 200) return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            LogUtil.e("ProxuApiService", "getProfileRaw failed", e)
            null
        }
    }

    fun getVpnServers(token: String): List<ProxuVpnServer>? {
        val request = createAuthRequest(token, "$BASE_URL/vpn-servers").build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (response.code != 200) {
                    return null
                }
                val jsonArray = JSONArray(body)
                val list = mutableListOf<ProxuVpnServer>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i)
                    obj?.let { ProxuApiModels.parseVpnServer(it) }?.let { list.add(it) }
                }
                list
            }
        } catch (e: Exception) {
            return null
        }
    }

    fun createVpn(token: String, serverId: Int? = null, inboundId: Int? = null): ProxuVpnConfig? {
        val bodyJson = JSONObject().put("quantity", 1)
        serverId?.let { bodyJson.put("xui_server_id", it) }
        inboundId?.let { bodyJson.put("xui_inbound_id", it) }
        val request = createAuthRequest(token, "$BASE_URL/vpn", "POST", bodyJson.toString()).build()
        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return null
                LogUtil.e("ProxuApiService", "createVpn HTTP ${response.code}: $bodyStr")
                if (response.code != 200) {
                    LogUtil.e("ProxuApiService", "createVpn failed with code ${response.code}")
                    return null
                }
                val json = JSONObject(bodyStr)
                // Parse from response which contains vpns array
                val vpns = json.optJSONArray("vpns")
                if (vpns != null && vpns.length() > 0) {
                    ProxuApiModels.parseVpnConfig(vpns.getJSONObject(0))
                } else {
                    ProxuApiModels.parseVpnConfig(json)
                }
            }
        } catch (e: Exception) {
            LogUtil.e("ProxuApiService", "createVpn failed", e)
            null
        }
    }

    fun getUserVpns(token: String): List<ProxuVpnConfig>? {
        val request = createAuthRequest(token, "$BASE_URL/vpn").build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (response.code != 200) {
                    LogUtil.e("ProxuApiService", "getUserVpns failed: HTTP ${response.code}: $body")
                    return null
                }
                val list = mutableListOf<ProxuVpnConfig>()
                try {
                    val jsonArray = JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i)
                        obj?.let { ProxuApiModels.parseVpnConfig(it) }?.let { list.add(it) }
                    }
                } catch (e: Exception) {
                    // Try parsing as object with "vpns" field
                    try {
                        val jsonObj = JSONObject(body)
                        val vpns = jsonObj.optJSONArray("vpns")
                        if (vpns != null) {
                            for (i in 0 until vpns.length()) {
                                val obj = vpns.optJSONObject(i)
                                obj?.let { ProxuApiModels.parseVpnConfig(it) }?.let { list.add(it) }
                            }
                        } else {
                            // Single VPN object
                            ProxuApiModels.parseVpnConfig(jsonObj)?.let { list.add(it) }
                        }
                    } catch (e2: Exception) {
                        LogUtil.e("ProxuApiService", "Failed to parse VPN response", e2)
                    }
                }
                LogUtil.i("ProxuApiService", "getUserVpns returned ${list.size} VPNs")
                list
            }
        } catch (e: Exception) {
            LogUtil.e("ProxuApiService", "getUserVpns failed", e)
            null
        }
    }

    fun getVpnConfig(token: String, vpnId: String): String? {
        val request = createAuthRequest(token, "$BASE_URL/vpn/$vpnId/config").build()
        return executeRequest(request) { json ->
            json.optString("config").takeIf { it.isNotBlank() }
        }
    }

    fun getVpnConfigJson(token: String, vpnId: String): JSONObject? {
        val request = createAuthRequest(token, "$BASE_URL/vpn/$vpnId/config").build()
        return executeRequest(request) { it }
    }

    fun getVpnInbounds(token: String, serverId: String): JSONArray? {
        val request = createAuthRequest(token, "$BASE_URL/vpn-inbounds/$serverId").build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                LogUtil.e("ProxuApiService", "getVpnInbounds HTTP ${response.code} for server $serverId: $body")
                if (response.code != 200) return null
                JSONArray(body)
            }
        } catch (e: Exception) {
            LogUtil.e("ProxuApiService", "getVpnInbounds failed", e)
            null
        }
    }

    fun deleteProxy(token: String, proxyId: String): Boolean {
        val request = createAuthRequest(token, "$BASE_URL/proxies/$proxyId", "DELETE").build()
        return executeSimpleRequest(request)
    }

    fun updateProxyPassword(token: String, proxyId: String, password: String): Boolean {
        val body = JSONObject().put("password", password).toString()
        val request = createAuthRequest(token, "$BASE_URL/proxies/$proxyId/password", "PUT", body).build()
        return executeSimpleRequest(request)
    }

    fun getTransactions(token: String): JSONArray? {
        val request = createAuthRequest(token, "$BASE_URL/transactions").build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (response.code != 200) {
                    LogUtil.e(TAG, "getTransactions failed: HTTP ${response.code}: ${body.take(200)}")
                    return null
                }
                JSONArray(body)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "getTransactions failed", e)
            null
        }
    }

    fun getPricing(token: String): JSONObject? {
        val request = createAuthRequest(token, "$BASE_URL/pricing").build()
        return executeRequest(request) { it }
    }

    fun createPayment(token: String, amount: Double, method: String): JSONObject? {
        val bodyJson = JSONObject()
            .put("amount", amount)
            .put("payment_method", method)
            .put("client_type", "mobile")
        val request = createAuthRequest(token, "$BASE_URL/payments/create", "POST", bodyJson.toString()).build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                LogUtil.e("ProxuApiService", "createPayment HTTP ${response.code}: ${body.take(200)}")
                if (response.code != 200) {
                    LogUtil.e("ProxuApiService", "createPayment failed: HTTP ${response.code}")
                    return null
                }
                JSONObject(body)
            }
        } catch (e: Exception) {
            LogUtil.e("ProxuApiService", "createPayment failed", e)
            null
        }
    }

    fun getPaymentStatus(token: String, paymentId: String): JSONObject? {
        val request = createAuthRequest(token, "$BASE_URL/payments/$paymentId/status").build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (response.code != 200) return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            LogUtil.e("ProxuApiService", "getPaymentStatus failed", e)
            null
        }
    }

    private inline fun <T> executeRequest(request: Request, parse: (JSONObject) -> T): T? {
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                
                // Check if account is blocked
                if (isBlockedResponse(body)) {
                    LogUtil.e(TAG, "Account blocked! Response: ${body.take(200)}")
                    return null
                }
                
                if (response.code != 200) {
                    return null
                }
                val json = JSONObject(body)
                parse(json)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun executeSimpleRequest(request: Request): Boolean {
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                
                // Check if account is blocked
                if (isBlockedResponse(body)) {
                    LogUtil.e(TAG, "Account blocked! Response: ${body.take(200)}")
                    return false
                }
                
                response.code == 200 || response.code == 204
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if API response indicates account is blocked.
     * Server returns 403 with "Пользователь не найден или заблокирован" or similar.
     */
    fun isBlockedResponse(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        return body.contains("заблокирован", true)
                || body.contains("blocked", true)
                || body.contains("не найден", true)
    }
}