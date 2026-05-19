package com.proxu.app.auth

import android.content.Context
import com.proxu.app.handler.SettingsChangeManager
import com.proxu.app.util.LogUtil
import com.proxu.app.util.ZipUtil
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object ProxuConfigDownloader {
    private const val TAG = "ProxuConfigDownloader"
    private const val CONFIG_URL = "https://proxu.pro/app-config/Proxu_config.zip"
    private const val CONFIG_BACKUP_URL = "https://proxu.pro/app-config/proxu_config.zip"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Downloads and applies remote configuration after successful login.
     * If download fails (404, network error), returns false but doesn't crash.
     * Uses the same approach as BackupActivity.restoreConfiguration().
     * 
     * @param context Application context
     * @return true if config was downloaded and applied, false otherwise
     */
    suspend fun downloadAndApplyConfig(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtil.i(TAG, "Downloading config from: $CONFIG_URL")
            
            // Try primary URL first, then backup URL (lowercase)
            var response = downloadConfig(CONFIG_URL)
            
            if (response == null || !response.isSuccessful) {
                LogUtil.i(TAG, "Primary URL failed (HTTP ${response?.code ?: "null"}), trying backup: $CONFIG_BACKUP_URL")
                response?.close()
                response = downloadConfig(CONFIG_BACKUP_URL)
            }
            
            if (response == null || !response.isSuccessful) {
                val code = response?.code ?: -1
                LogUtil.w(TAG, "Config download failed with HTTP $code. Server may not have config file yet.")
                response?.close()
                return@withContext false
            }
            
            // Save zip to temp file
            val zipFile = File(context.cacheDir, "proxu_config_${System.currentTimeMillis()}.zip")
            response.body?.byteStream()?.use { input ->
                zipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            response.close()
            
            if (!zipFile.exists() || zipFile.length() == 0L) {
                LogUtil.e(TAG, "Downloaded zip file is empty")
                return@withContext false
            }
            
            LogUtil.i(TAG, "Config downloaded (${zipFile.length()} bytes), extracting...")
            
            // Extract to temp directory (same approach as BackupActivity)
            val backupDir = context.cacheDir.absolutePath + "/config_restore_${System.currentTimeMillis()}"
            if (!ZipUtil.unzipToFolder(zipFile, backupDir)) {
                LogUtil.e(TAG, "Failed to unzip config")
                zipFile.delete()
                return@withContext false
            }
            
            // Apply using MMKV restoreAllFromDirectory (same as BackupActivity)
            val count = MMKV.restoreAllFromDirectory(backupDir)
            
            // Cleanup
            zipFile.delete()
            File(backupDir).deleteRecursively()
            
            if (count > 0) {
                LogUtil.i(TAG, "Config applied successfully. Restored $count items.")
                // Remote config may contain stale bundled/example profiles (e.g. old price in VLESS remark).
                // User VPN profiles must always come from proxu.pro API sync, so strip restored profiles
                // while keeping restored app settings/theme preferences.
                ProxuProfileSync.clearCloudProfiles()
                SettingsChangeManager.makeSetupGroupTab()
                SettingsChangeManager.makeRestartService()
                return@withContext true
            } else {
                LogUtil.w(TAG, "Config zip was empty or contained no valid MMKV data")
                return@withContext false
            }
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to download/apply config", e)
            return@withContext false
        }
    }
    
    private fun downloadConfig(url: String): okhttp3.Response? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ProxuApp/2.1.7.1")
                .build()
            client.newCall(request).execute()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Network error downloading from $url", e)
            null
        }
    }
}