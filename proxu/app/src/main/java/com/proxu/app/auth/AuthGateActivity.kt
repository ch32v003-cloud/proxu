package com.proxu.app.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.proxu.app.ui.MainActivity

class AuthGateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProxuAuthManager.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(ProxuLoginActivity.createIntent(this, required = true, openMainOnSuccess = true))
        }
        finish()
    }
}
