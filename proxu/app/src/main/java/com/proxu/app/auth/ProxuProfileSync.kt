package com.proxu.app.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.proxu.app.AppConfig
import com.proxu.app.dto.entities.ProfileItem
import com.proxu.app.enums.EConfigType
import com.proxu.app.handler.AngConfigManager
import com.proxu.app.handler.MmkvManager
import com.proxu.app.handler.SettingsChangeManager
import com.proxu.app.util.LogUtil
import com.proxu.app.util.MessageUtil
import com.proxu.app.util.Utils
import com.proxu.app.util.JsonUtil
import org.json.JSONObject
import com.tencent.mmkv.MMKV

object ProxuProfileSync {

    private const val TAG = "ProxuProfileSync"
    private const val SUBSCRIPTION_ID = AppConfig.DEFAULT_SUBSCRIPTION_ID
    private const val SOURCE_TAG = "[proxu.pro]"

    fun syncProfiles(context: Context, token: String, onComplete: ((SyncResult) -> Unit)? = null) {
        Thread {
            try {
                LogUtil.i(TAG, "Starting profile sync from proxu.pro")
                val proxies = ProxuApiService.getProxies(token)
                if (proxies.isNullOrEmpty()) {
                    LogUtil.w(TAG, "No proxies returned from proxu.pro")
                    onComplete?.invoke(SyncResult(0, 0, "No proxies available"))
                    return@Thread
                }

                var added = 0
                var skipped = 0
                val existingServers = MmkvManager.decodeServerList(SUBSCRIPTION_ID).toMutableSet()
                val newServerList = mutableListOf<String>()

                for (proxy in proxies) {
                    val serverKey = "proxu_${proxy.id}"
                    newServerList.add(serverKey)

                    if (existingServers.contains(serverKey)) {
                        skipped++
                        continue
                    }

                    val profile = convertProxyToProfile(token, proxy)
                    if (profile != null) {
                        val key = MmkvManager.encodeServerConfig(serverKey, profile)
                        LogUtil.i(TAG, "Added proxy: ${proxy.id} ($serverKey)")
                        added++
                    } else {
                        LogUtil.w(TAG, "Failed to convert proxy: ${proxy.id}")
                        skipped++
                    }
                }

                for (oldKey in existingServers) {
                    if (!newServerList.contains(oldKey)) {
                        MmkvManager.removeServer(oldKey)
                        LogUtil.i(TAG, "Removed old proxy: $oldKey")
                    }
                }

                MmkvManager.encodeServerList(newServerList.toMutableList(), SUBSCRIPTION_ID)

                LogUtil.i(TAG, "Sync complete: $added added, $skipped skipped")
                ProxuAuthManager.setProfilesSyncPending(context, false)
                onComplete?.invoke(SyncResult(added, skipped, "Sync complete"))
            } catch (e: Exception) {
                LogUtil.e(TAG, "Profile sync failed", e)
                onComplete?.invoke(SyncResult(0, 0, "Sync failed: ${e.message}"))
            }
        }.start()
    }

    suspend fun syncProfilesAndSelectFirst(context: Context, token: String): SyncResult = withContext(Dispatchers.IO) {
        try {
                LogUtil.e(TAG, "Starting profile sync from proxu.pro")
                LogUtil.e(TAG, "Token length: ${token.length}")
                
                // Always fetch profile to get balance
                val userProfile = ProxuApiService.getProfile(token)
                userProfile?.balance?.let { balance ->
                    ProxuAuthManager.updateBalance(context, balance)
                    LogUtil.i(TAG, "Updated balance: $balance")
                }
                
                // Get all proxies from API - this includes VPN configs with full VLESS links
                val proxies = ProxuApiService.getProxies(token)
                LogUtil.e(TAG, "Proxies returned: ${proxies?.size ?: -1}")
                
                if (proxies.isNullOrEmpty()) {
                    LogUtil.w(TAG, "No proxies returned from proxu.pro")
                    return@withContext SyncResult(0, 0, "No proxies available")
                }

                LogUtil.e(TAG, "Processing proxies...")
                var added = 0
                var skipped = 0
                val existingServers = MmkvManager.decodeServerList(SUBSCRIPTION_ID).toMutableSet()
                LogUtil.e(TAG, "Existing servers: ${existingServers.size}")
                val newServerList = mutableListOf<String>()
                var firstServerKey: String? = null

                for (proxy in proxies) {
                    LogUtil.e(TAG, "Loop iteration for proxy: ${proxy.id}, protocol=${proxy.protocol}")
                    val serverKey = "proxu_${proxy.id}"
                    
                    val profile = convertProxyToProfile(token, proxy)
                    if (profile != null) {
                        // Only add to list AFTER successful parsing
                        if (!newServerList.contains(serverKey)) {
                            newServerList.add(serverKey)
                        }
                        
                        if (existingServers.contains(serverKey)) {
                            MmkvManager.removeServer(serverKey)
                            LogUtil.i(TAG, "Removed old proxy for update: $serverKey")
                        }
                        MmkvManager.encodeServerConfig(serverKey, profile)
                        LogUtil.e(TAG, "Successfully saved profile: $serverKey")
                        added++
                        if (firstServerKey == null) {
                            firstServerKey = serverKey
                        }
                    } else {
                        LogUtil.e(TAG, "Failed to convert proxy: ${proxy.id} - keeping existing if present")
                        // Don't add to newServerList - keep existing profile if it exists
                    }
                }

                LogUtil.e(TAG, "Finished processing proxies. added=$added, skipped=$skipped, newServerList=${newServerList.size}, existing=${existingServers.size}")
                
                // CRITICAL: Remove old profiles that are no longer on server
                // If API returned empty list (user deleted all profiles), we must clear them
                // If API returned null (error), we keep existing profiles
                if (proxies != null) {
                    for (oldKey in existingServers) {
                        if (!newServerList.contains(oldKey)) {
                            MmkvManager.removeServer(oldKey)
                            LogUtil.i(TAG, "Removed old proxy: $oldKey")
                        }
                    }
                    MmkvManager.encodeServerList(newServerList.toMutableList(), SUBSCRIPTION_ID)
                    LogUtil.e(TAG, "Saved server list to MmkvManager: $newServerList")
                } else {
                    LogUtil.w(TAG, "API returned null (error) - keeping existing ${existingServers.size} profiles")
                }
                
                // Verify what was saved
                val savedList = MmkvManager.decodeServerList(SUBSCRIPTION_ID)
                LogUtil.e(TAG, "Verified saved list: ${savedList.size} items")
                
                // Debug: check all server keys in mainStorage
                try {
                    val mainStorage = com.tencent.mmkv.MMKV.mmkvWithID("MAIN")
                    val allKeys = mainStorage.allKeys()
                    LogUtil.e(TAG, "All MAIN storage keys count: ${allKeys?.size ?: 0}")
                    val relevantKeys = allKeys?.filter { it.contains("SUB_SERVERS") || it == "SELECTED_SERVER" || it.contains("ANG_CONFIG") || it.contains("proxu") }
                    LogUtil.e(TAG, "Relevant keys: ${relevantKeys?.joinToString()}")
                    // Also check what SUB_IDS contains
                    val subIds = mainStorage.decodeString("SUB_IDS")
                    LogUtil.e(TAG, "SUB_IDS: $subIds")
                    
                    // Register the subscription if not exists
                    if (subIds != null && !subIds.contains(SUBSCRIPTION_ID)) {
                        LogUtil.e(TAG, "Registering subscription: $SUBSCRIPTION_ID")
                        try {
                            val jsonArray = org.json.JSONArray(subIds)
                            val currentSubIds = mutableListOf<String>()
                            for (i in 0 until jsonArray.length()) {
                                currentSubIds.add(jsonArray.getString(i))
                            }
                            currentSubIds.add(SUBSCRIPTION_ID)
                            mainStorage.encode("SUB_IDS", currentSubIds.joinToString(",", "[", "]"))
                            
                            // Create subscription entry
                            val subItem = com.proxu.app.dto.entities.SubscriptionItem()
                            subItem.remarks = "proxu.pro VPN"
                            subItem.url = ""
                            subItem.enabled = true
                            com.proxu.app.handler.MmkvManager.encodeSubscription(SUBSCRIPTION_ID, subItem)
                            LogUtil.e(TAG, "Subscription registered")
                        } catch (e: Exception) {
                            LogUtil.e(TAG, "Failed to parse SUB_IDS", e)
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to check storage keys", e)
                }

firstServerKey?.let {
                    MmkvManager.setSelectServer(it)
                    LogUtil.i(TAG, "Selected first server: $it")
                }
                
                MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, "")
                
                // NOTE: Do NOT remove default subscription here!
                // removeSubscription() calls removeServerViaSubid() which deletes ALL servers
                // we just saved to default subscription. The empty default subscription is harmless.
                // MmkvManager.removeSubscription(AppConfig.DEFAULT_SUBSCRIPTION_ID)
                
                MessageUtil.sendMsg2UI(context, AppConfig.MSG_RELOAD_SERVER_LIST, "")
                
                SettingsChangeManager.makeSetupGroupTab()
                LogUtil.i(TAG, "Requested group tab refresh")
                
                LogUtil.i(TAG, "Sync complete: $added added, $skipped skipped")
                ProxuAuthManager.setProfilesSyncPending(context, false)
                return@withContext SyncResult(added, skipped, "Sync complete")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Profile sync failed", e)
                return@withContext SyncResult(0, 0, "Sync failed: ${e.message}")
            }
        }

    fun syncVpnConfigs(context: Context, token: String, onComplete: ((SyncResult) -> Unit)? = null) {
        Thread {
            try {
                LogUtil.i(TAG, "Starting VPN config sync from proxu.pro")
                val vpnServers = ProxuApiService.getVpnServers(token)
                if (vpnServers.isNullOrEmpty()) {
                    LogUtil.w(TAG, "No VPN servers returned from proxu.pro")
                    onComplete?.invoke(SyncResult(0, 0, "No VPN servers available"))
                    return@Thread
                }

                var added = 0
                for (server in vpnServers) {
                    try {
                        val vpnConfig = ProxuApiService.createVpn(token)
                        if (vpnConfig != null) {
                            val configContent = ProxuApiService.getVpnConfig(token, vpnConfig.id)
                            if (!configContent.isNullOrBlank()) {
                                val profile = convertVpnConfigToProfile(server, configContent)
                                if (profile != null) {
                                    val key = MmkvManager.encodeServerConfig("", profile)
                                    LogUtil.i(TAG, "Added VPN config: ${server.name}")
                                    added++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Failed to add VPN server: ${server.name}", e)
                    }
                }

                LogUtil.i(TAG, "VPN sync complete: $added added")
                onComplete?.invoke(SyncResult(added, 0, "VPN sync complete"))
            } catch (e: Exception) {
                LogUtil.e(TAG, "VPN sync failed", e)
                onComplete?.invoke(SyncResult(0, 0, "VPN sync failed: ${e.message}"))
            }
        }.start()
    }

    private fun convertProxyToProfile(token: String, proxy: ProxuProxy): ProfileItem? {
        return try {
            LogUtil.e(TAG, "Converting proxy: ${proxy.id}, type=${proxy.protocol}, server=${proxy.server}, port=${proxy.port}, extra=${proxy.extra}")
            
            // For VPN-type proxies, get the full config including TLS/reality settings
            if (proxy.id.startsWith("vpn_") || proxy.protocol == "vpn") {
                LogUtil.e(TAG, "Detected VPN type, calling convertVpnProxy")
                return convertVpnProxy(token, proxy)
            }
            
            if (proxy.protocol.lowercase() == "vless" && proxy.id.startsWith("vpn_")) {
                LogUtil.e(TAG, "VLESS proxy with VPN ID, fetching full config via getVpnConfigJson")
                val vpnConfigJson = ProxuApiService.getVpnConfigJson(token, proxy.id)
                if (vpnConfigJson != null) {
                    val link = vpnConfigJson.optString("link", "")
                    if (link.isNotBlank() && link.startsWith("vless://")) {
                        LogUtil.e(TAG, "Got VPN link from config: ${link.take(50)}...")
                        val profile = importVlessLink(link)
                        if (profile != null) {
                            profile.subscriptionId = SUBSCRIPTION_ID
                            profile.description = "VPN from proxu.pro"
                            return profile
                        }
                    }
                }
                LogUtil.e(TAG, "Could not get VPN link, falling through to standard conversion")
            }
            
            LogUtil.d(TAG, "Converting as standard proxy type: ${proxy.protocol}")
            when (proxy.protocol.lowercase()) {
                "vless" -> convertVlessProxy(proxy)
                "vmess" -> convertVmessProxy(proxy)
                "trojan" -> convertTrojanProxy(proxy)
                "shadowsocks" -> convertShadowsocksProxy(proxy)
                "socks5", "socks" -> convertSocksProxy(proxy)
                "hy2", "hysteria2" -> convertHysteria2Proxy(proxy)
                else -> convertGenericProxy(proxy)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert proxy ${proxy.id}", e)
            null
        }
    }
    
    private fun convertVpnProxy(token: String, proxy: ProxuProxy): ProfileItem? {
        return try {
            LogUtil.e(TAG, "Getting VPN config for ${proxy.id}")
            val vpnConfigJson = ProxuApiService.getVpnConfigJson(token, proxy.id)
            if (vpnConfigJson != null) {
                LogUtil.e(TAG, "Got VPN config JSON: ${vpnConfigJson}")
                val link = vpnConfigJson.optString("link", "")
                if (link.isNotBlank()) {
                    LogUtil.e(TAG, "Got VPN link: ${link.take(50)}...")
                    
                    // Parse the link directly instead of using AngConfigManager
                    // This ensures we get the correct profile with proxu_{id} key
                    val profile = importFromLink(link, proxy.id)
                    if (profile != null) {
                        profile.subscriptionId = SUBSCRIPTION_ID
                        profile.description = "VPN from proxu.pro"
                        return profile
                    }
                    LogUtil.e(TAG, "Failed to parse VPN link")
                } else {
                    LogUtil.w(TAG, "No link in VPN config JSON")
                }
            } else {
                LogUtil.w(TAG, "No VPN config JSON returned for ${proxy.id}")
            }
            null
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert VPN proxy ${proxy.id}", e)
            null
        }
    }
    
    private fun importFromLink(link: String, proxyId: String): ProfileItem? {
        return try {
            val configStr = if (link.startsWith("vmess://")) {
                String(android.util.Base64.decode(link.removePrefix("vmess://"), android.util.Base64.NO_PADDING))
            } else {
                link
            }
            
            val profile = when {
                link.startsWith("vless://") -> importVlessLink(link)
                link.startsWith("vmess://") -> importVmessJson(configStr)
                link.startsWith("trojan://") -> importTrojanLink(link)
                link.startsWith("ss://") -> importShadowsocksLink(link)
                else -> null
            }
            
            profile?.let {
                it.subscriptionId = SUBSCRIPTION_ID
                it.description = "VPN from proxu.pro"
            }
            
            profile
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to import link: ${link.take(50)}", e)
            null
        }
    }
    
    private fun importVlessLink(link: String): ProfileItem? {
        return try {
            val profile = ProfileItem.create(EConfigType.VLESS)
            val uri = android.net.Uri.parse(link)
            
            profile.server = uri.host ?: ""
            profile.serverPort = uri.port.toString()
            profile.password = uri.userInfo ?: ""
            
            val security = uri.getQueryParameter("security") ?: "reality"
            profile.security = security
            
            val flow = uri.getQueryParameter("flow") ?: ""
            profile.flow = flow
            
            profile.network = uri.getQueryParameter("type") ?: "tcp"
            profile.headerType = uri.getQueryParameter("headerType") ?: ""
            profile.host = uri.getQueryParameter("host") ?: ""
            profile.path = uri.getQueryParameter("path") ?: ""
            profile.sni = uri.getQueryParameter("sni") ?: uri.host ?: ""
            profile.fingerPrint = uri.getQueryParameter("fp") ?: ""
            profile.alpn = uri.getQueryParameter("alpn") ?: ""
            profile.method = uri.getQueryParameter("encryption") ?: "none"
            
            val insecure = uri.getQueryParameter("insecure")
            profile.insecure = insecure?.toBoolean() ?: (security == "tls")
            
            profile.publicKey = uri.getQueryParameter("pbk") ?: ""
            profile.shortId = uri.getQueryParameter("sid") ?: ""
            
            profile.spiderX = uri.getQueryParameter("spx") ?: ""
            profile.echConfigList = uri.getQueryParameter("ech") ?: ""
            profile.secretKey = uri.getQueryParameter("scy") ?: ""
            
            profile.remarks = uri.fragment ?: "proxu.pro VPN"
            
            LogUtil.e(TAG, "Imported VLESS profile: server=${profile.server}, port=${profile.serverPort}, security=${profile.security}, flow=${profile.flow}, sni=${profile.sni}, publicKey=${profile.publicKey?.take(10)}..., shortId=${profile.shortId}")
            
            profile
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to import VLESS link", e)
            null
        }
    }
    
    private fun importVmessJson(configStr: String): ProfileItem? {
        return try {
            val json = JSONObject(configStr)
            val profile = ProfileItem.create(EConfigType.VMESS)
            
            profile.server = json.optString("add", "")
            profile.serverPort = json.optString("port", "443")
            profile.password = json.optString("id", "")
            profile.method = json.optString("scy", "auto")
            profile.network = json.optString("net", "tcp")
            profile.headerType = json.optString("type", "none")
            profile.host = json.optString("host", "")
            profile.path = json.optString("path", "")
            profile.sni = json.optString("sni", json.optString("add", ""))
            profile.fingerPrint = json.optString("fp", "")
            profile.insecure = json.optBoolean("insecure", false)
            profile.remarks = json.optString("ps", "")
            
            profile
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to import VMess JSON", e)
            null
        }
    }
    
    private fun importTrojanLink(link: String): ProfileItem? {
        return try {
            val profile = ProfileItem.create(EConfigType.TROJAN)
            val uri = android.net.Uri.parse(link)
            
            profile.server = uri.host ?: ""
            profile.serverPort = uri.port.toString()
            profile.password = uri.userInfo ?: ""
            profile.sni = uri.getQueryParameter("sni") ?: uri.host ?: ""
            profile.fingerPrint = uri.getQueryParameter("fp") ?: ""
            profile.alpn = uri.getQueryParameter("alpn") ?: ""
            profile.insecure = uri.getQueryParameter("insecure")?.toBoolean() ?: false
            profile.remarks = uri.fragment ?: ""
            
            profile
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to import Trojan link", e)
            null
        }
    }
    
    private fun importShadowsocksLink(link: String): ProfileItem? {
        return try {
            val profile = ProfileItem.create(EConfigType.SHADOWSOCKS)
            
            val withoutScheme = link.removePrefix("ss://")
            val atIndex = withoutScheme.indexOf("@")
            val hashIndex = withoutScheme.indexOf("#")
            
            val userInfo = if (atIndex > 0) java.net.URLDecoder.decode(withoutScheme.substring(0, atIndex), "UTF-8") else ""
            val serverInfo = if (hashIndex > 0) withoutScheme.substring(atIndex + 1, hashIndex) else withoutScheme.substring(atIndex + 1)
            
            val colonIndex = userInfo.indexOf(":")
            if (colonIndex > 0) {
                profile.method = userInfo.substring(0, colonIndex)
                profile.password = userInfo.substring(colonIndex + 1)
            }
            
            val lastAtIndex = serverInfo.lastIndexOf(":")
            if (lastAtIndex > 0) {
                profile.server = serverInfo.substring(0, lastAtIndex)
                profile.serverPort = serverInfo.substring(lastAtIndex + 1)
            }
            
            profile.remarks = if (hashIndex > 0) java.net.URLDecoder.decode(withoutScheme.substring(hashIndex + 1), "UTF-8") else ""
            
            profile
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to import Shadowsocks link", e)
            null
        }
    }

    private fun convertVlessProxy(proxy: ProxuProxy): ProfileItem? {
        val link = proxy.extra?.optString("connection_string") ?: proxy.extra?.optString("link")
        if (!link.isNullOrBlank() && link.startsWith("vless://")) {
            return importVlessLink(link)
        }
        
        val profile = ProfileItem.create(EConfigType.VLESS)
        profile.subscriptionId = SUBSCRIPTION_ID
        profile.remarks = "$SOURCE_TAG ${proxy.name.ifBlank { proxy.id }}"
        profile.server = proxy.server
        profile.serverPort = proxy.port.toString()
        profile.password = proxy.password ?: proxy.username ?: ""
        profile.method = proxy.encryption ?: "none"
        profile.flow = proxy.extra?.optString("flow") ?: ""
        profile.network = proxy.extra?.optString("network") ?: "tcp"
        profile.headerType = proxy.extra?.optString("headerType") ?: ""
        profile.host = proxy.extra?.optString("host") ?: ""
        profile.path = proxy.extra?.optString("path") ?: ""
        profile.security = proxy.extra?.optString("security") ?: ""
        profile.sni = proxy.extra?.optString("sni") ?: proxy.server
        profile.fingerPrint = proxy.extra?.optString("fingerprint") ?: ""
        profile.alpn = proxy.extra?.optString("alpn") ?: ""
        profile.insecure = proxy.extra?.optBoolean("insecure") ?: false
        profile.publicKey = proxy.extra?.optString("publicKey") ?: proxy.extra?.optString("pbk") ?: ""
        profile.shortId = proxy.extra?.optString("shortId") ?: proxy.extra?.optString("sid") ?: ""
        profile.spiderX = proxy.extra?.optString("spiderX") ?: proxy.extra?.optString("spx") ?: ""
        profile.description = generateDescription(proxy)
        return profile
    }

    private fun convertVmessProxy(proxy: ProxuProxy): ProfileItem {
        val profile = ProfileItem.create(EConfigType.VMESS)
        profile.subscriptionId = SUBSCRIPTION_ID
        profile.remarks = "$SOURCE_TAG ${proxy.name.ifBlank { proxy.id }}"
        profile.server = proxy.server
        profile.serverPort = proxy.port.toString()
        profile.password = proxy.username ?: Utils.getUuid()
        profile.method = proxy.encryption ?: "auto"
        profile.network = proxy.extra?.optString("network") ?: "tcp"
        profile.headerType = proxy.extra?.optString("headerType") ?: ""
        profile.host = proxy.extra?.optString("host") ?: ""
        profile.path = proxy.extra?.optString("path") ?: ""
        profile.sni = proxy.extra?.optString("sni") ?: proxy.server
        profile.fingerPrint = proxy.extra?.optString("fingerprint") ?: ""
        profile.insecure = proxy.extra?.optBoolean("insecure") ?: false
        profile.description = generateDescription(proxy)
        return profile
    }

    private fun convertTrojanProxy(proxy: ProxuProxy): ProfileItem {
        val profile = ProfileItem.create(EConfigType.TROJAN)
        profile.subscriptionId = SUBSCRIPTION_ID
        profile.remarks = "$SOURCE_TAG ${proxy.name.ifBlank { proxy.id }}"
        profile.server = proxy.server
        profile.serverPort = proxy.port.toString()
        profile.password = proxy.password ?: ""
        profile.sni = proxy.extra?.optString("sni") ?: proxy.server
        profile.fingerPrint = proxy.extra?.optString("fingerprint") ?: ""
        profile.network = proxy.extra?.optString("network") ?: "tcp"
        profile.headerType = proxy.extra?.optString("headerType") ?: ""
        profile.host = proxy.extra?.optString("host") ?: ""
        profile.path = proxy.extra?.optString("path") ?: ""
        profile.alpn = proxy.extra?.optString("alpn") ?: ""
        profile.insecure = proxy.extra?.optBoolean("insecure") ?: false
        profile.description = generateDescription(proxy)
        return profile
    }

    private fun convertShadowsocksProxy(proxy: ProxuProxy): ProfileItem {
        val profile = ProfileItem.create(EConfigType.SHADOWSOCKS)
        profile.subscriptionId = SUBSCRIPTION_ID
        profile.remarks = "$SOURCE_TAG ${proxy.name.ifBlank { proxy.id }}"
        profile.server = proxy.server
        profile.serverPort = proxy.port.toString()
        profile.method = proxy.encryption ?: "chacha20-ietf-poly1305"
        profile.password = proxy.password ?: ""
        profile.description = generateDescription(proxy)
        return profile
    }

    private fun convertSocksProxy(proxy: ProxuProxy): ProfileItem {
        val profile = ProfileItem.create(EConfigType.SOCKS)
        profile.subscriptionId = SUBSCRIPTION_ID
        profile.remarks = "$SOURCE_TAG ${proxy.name.ifBlank { proxy.id }}"
        profile.server = proxy.server
        profile.serverPort = proxy.port.toString()
        profile.username = proxy.username
        profile.password = proxy.password
        profile.security = "noauth"
        profile.description = generateDescription(proxy)
        return profile
    }

    private fun convertHysteria2Proxy(proxy: ProxuProxy): ProfileItem {
        val profile = ProfileItem.create(EConfigType.HYSTERIA2)
        profile.subscriptionId = SUBSCRIPTION_ID
        profile.remarks = "$SOURCE_TAG ${proxy.name.ifBlank { proxy.id }}"
        profile.server = proxy.server
        profile.serverPort = proxy.port.toString()
        profile.password = proxy.password ?: ""
        profile.insecure = proxy.extra?.optBoolean("insecure") ?: false
        profile.sni = proxy.extra?.optString("sni") ?: proxy.server
        profile.alpn = proxy.extra?.optString("alpn") ?: ""
        profile.description = generateDescription(proxy)
        return profile
    }

    private fun convertGenericProxy(proxy: ProxuProxy): ProfileItem {
        val profile = ProfileItem.create(EConfigType.VLESS)
        profile.subscriptionId = SUBSCRIPTION_ID
        profile.remarks = "$SOURCE_TAG ${proxy.name.ifBlank { proxy.id }}"
        profile.server = proxy.server
        profile.serverPort = proxy.port.toString()
        profile.password = proxy.password ?: proxy.username ?: ""
        profile.sni = proxy.server
        profile.description = generateDescription(proxy)
        return profile
    }

    private fun convertVpnConfigToProfile(server: ProxuVpnServer, configContent: String): ProfileItem? {
        return try {
            if (configContent.contains("\"v\": \"2\"") || configContent.contains("\"ps\":")) {
                val vmessConfig = JSONObject(configContent)
                val profile = ProfileItem.create(EConfigType.VMESS)
                profile.subscriptionId = SUBSCRIPTION_ID
                profile.remarks = server.name
                profile.server = vmessConfig.optString("add", server.host)
                profile.serverPort = vmessConfig.optString("port", server.port.toString())
                profile.password = vmessConfig.optString("id", "")
                profile.method = vmessConfig.optString("scy", "auto")
                profile.network = vmessConfig.optString("net", "tcp")
                profile.headerType = vmessConfig.optString("type", "none")
                profile.host = vmessConfig.optString("host", "")
                profile.path = vmessConfig.optString("path", "")
                profile.sni = vmessConfig.optString("add", server.host)
                profile.fingerPrint = vmessConfig.optString("fp", "")
                profile.description = "VPN: ${server.country ?: ""} ${server.city ?: ""}"
                return profile
            }

            if (configContent.contains("vmess://") || configContent.contains("vless://") ||
                configContent.contains("trojan://") || configContent.contains("ss://")) {
                val serverList = MmkvManager.decodeServerList(SUBSCRIPTION_ID)
                val key = Utils.getUuid()
                val count = AngConfigManager.importBatchConfig(configContent, SUBSCRIPTION_ID, append = true).first
                if (count > 0) {
                    return MmkvManager.decodeServerConfig(serverList.lastOrNull() ?: key)
                }
            }

            val profile = ProfileItem.create(EConfigType.CUSTOM)
            profile.subscriptionId = SUBSCRIPTION_ID
            profile.remarks = server.name
            profile.server = server.host
            profile.serverPort = server.port.toString()
            profile.description = "VPN: ${server.country ?: ""} ${server.city ?: ""}"
            profile
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert VPN config", e)
            null
        }
    }

    private fun generateDescription(proxy: ProxuProxy): String {
        return "${proxy.server}:${proxy.port}"
    }

    fun clearCloudProfiles() {
        MmkvManager.removeServerViaSubid(SUBSCRIPTION_ID)
    }

    fun createProfile(context: Context, token: String, onComplete: ((SyncResult) -> Unit)? = null) {
        Thread {
            try {
                LogUtil.i(TAG, "Creating new VPN profile from proxu.pro")
                
                val vpnServers = ProxuApiService.getVpnServers(token)
                if (vpnServers.isNullOrEmpty()) {
                    LogUtil.w(TAG, "No VPN servers available")
                    onComplete?.invoke(SyncResult(0, 0, "No VPN servers available"))
                    return@Thread
                }
                
                // Get all available VLESS inbounds across all servers
                val availableInbounds = mutableListOf<Pair<ProxuVpnServer, org.json.JSONObject>>()
                
                for (server in vpnServers) {
                    val inboundsArray = ProxuApiService.getVpnInbounds(token, server.id)
                    if (inboundsArray == null) {
                        LogUtil.w(TAG, "Failed to get inbounds for server ${server.name}")
                        continue
                    }
                    
                    for (i in 0 until inboundsArray.length()) {
                        val inbound = inboundsArray.getJSONObject(i)
                        val protocol = inbound.optString("protocol", "").lowercase()
                        if (protocol == "vless") {
                            availableInbounds.add(Pair(server, inbound))
                        }
                    }
                }
                
                if (availableInbounds.isEmpty()) {
                    LogUtil.e(TAG, "No VLESS inbounds available on any server")
                    onComplete?.invoke(SyncResult(0, 0, "No VLESS inbounds available"))
                    return@Thread
                }
                
                // Select random server and inbound
                val (selectedServer, selectedInbound) = availableInbounds.random()
                val xuiServerId = selectedServer.id.toIntOrNull()
                val xuiInboundId = selectedInbound.optInt("id", -1)
                
                if (xuiServerId == null || xuiInboundId < 0) {
                    LogUtil.e(TAG, "Invalid server/inbound ID: serverId=${selectedServer.id}, inboundId=$xuiInboundId")
                    onComplete?.invoke(SyncResult(0, 0, "Invalid server/inbound ID"))
                    return@Thread
                }
                
                LogUtil.i(TAG, "Selected server: ${selectedServer.name} (ID: $xuiServerId), inbound ID: $xuiInboundId")
                
                val vpnConfig = ProxuApiService.createVpn(token, xuiServerId, xuiInboundId)
                if (vpnConfig == null) {
                    LogUtil.e(TAG, "Failed to create VPN config")
                    onComplete?.invoke(SyncResult(0, 0, "Failed to create VPN"))
                    return@Thread
                }
                
                val link = vpnConfig.config
                if (link.isBlank() || !link.startsWith("vless://")) {
                    LogUtil.e(TAG, "Invalid VPN link received: $link")
                    onComplete?.invoke(SyncResult(0, 0, "Invalid VPN link"))
                    return@Thread
                }
                
                LogUtil.e(TAG, "STEP 1: About to parse link")
                val profile = importVlessLink(link)
                LogUtil.e(TAG, "STEP 2: importVlessLink returned: ${profile != null}")
                
                if (profile != null) {
                    LogUtil.e(TAG, "STEP 3: Profile not null, saving...")
                    // Save to default subscription so it shows up immediately in UI
                    profile.subscriptionId = ""
                    profile.description = "VPN from proxu.pro"
                    val serverKey = "proxu_${vpnConfig.id}"
                    LogUtil.e(TAG, "STEP 4: serverKey=$serverKey")
                    val key = MmkvManager.encodeServerConfig(serverKey, profile)
                    LogUtil.e(TAG, "STEP 5: encoded server config, key=$key")
                    MmkvManager.setSelectServer(key)
                    LogUtil.e(TAG, "STEP 6: set selected server")
                    
                    val serverList = MmkvManager.decodeServerList("").toMutableList()
                    LogUtil.e(TAG, "STEP 7: current serverList size=${serverList.size}")
                    if (!serverList.contains(serverKey)) {
                        serverList.add(0, serverKey)
                        MmkvManager.encodeServerList(serverList, "")
                        LogUtil.e(TAG, "STEP 8: added to server list")
                    }
                    
                    LogUtil.e(TAG, "STEP 9: triggering UI refresh")
                    SettingsChangeManager.makeSetupGroupTab()
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_RELOAD_SERVER_LIST, "")
                    
                    LogUtil.e(TAG, "STEP 10: DONE - Created new VPN profile: $serverKey")
                    onComplete?.invoke(SyncResult(1, 0, "Profile created"))
                } else {
                    LogUtil.e(TAG, "Failed to parse VPN link")
                    onComplete?.invoke(SyncResult(0, 0, "Failed to parse VPN link"))
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to create profile", e)
                onComplete?.invoke(SyncResult(0, 0, "Error: ${e.message}"))
            }
        }.start()
    }
}

data class SyncResult(
    val added: Int,
    val skipped: Int,
    val message: String
)