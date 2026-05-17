package com.proxu.app.auth

import android.content.Context
import android.content.SharedPreferences
import com.proxu.app.util.LogUtil

object ProxuAuthManager {
    private const val PREFS_NAME = "pulse_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_BALANCE = "balance"
    private const val KEY_PROFILES_SYNC_PENDING = "profiles_sync_pending"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(context: Context): Boolean {
        return !getToken(context).isNullOrBlank()
    }

    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_TOKEN, null)
    }

    fun getRefreshToken(context: Context): String? {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, null)
    }

    fun getUserEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_EMAIL, null)
    }

    fun getBalance(context: Context): String? {
        return getPrefs(context).getString(KEY_BALANCE, null)
    }

    fun saveAuth(context: Context, token: String, refreshToken: String?, email: String, balance: String? = null) {
        LogUtil.d("ProxuAuthManager", "saveAuth called with balance=$balance")
        getPrefs(context).edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_USER_EMAIL, email)
            balance?.let { 
                LogUtil.d("ProxuAuthManager", "Saving balance: $it")
                putString(KEY_BALANCE, it) 
            }
            putBoolean(KEY_PROFILES_SYNC_PENDING, true)
            apply()
        }
    }

    fun updateBalance(context: Context, balance: String?) {
        getPrefs(context).edit().putString(KEY_BALANCE, balance).apply()
    }

    fun clearAuth(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun setProfilesSyncPending(context: Context, pending: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PROFILES_SYNC_PENDING, pending).apply()
    }

    fun isProfilesSyncPending(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PROFILES_SYNC_PENDING, false)
    }
}
