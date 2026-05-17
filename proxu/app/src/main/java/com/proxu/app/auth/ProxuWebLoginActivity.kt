package com.proxu.app.auth

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.proxu.app.R
import com.proxu.app.databinding.ActivityProxuWebLoginBinding
import com.proxu.app.ui.BaseActivity
import com.proxu.app.ui.MainActivity
import com.proxu.app.util.LogUtil

class ProxuWebLoginActivity : BaseActivity() {
    private lateinit var binding: ActivityProxuWebLoginBinding

    private val isRequiredLogin: Boolean
        get() = intent.getBooleanExtra(ProxuLoginActivity.EXTRA_REQUIRED_LOGIN, false)
    private val shouldOpenMainOnSuccess: Boolean
        get() = intent.getBooleanExtra(EXTRA_OPEN_MAIN_ON_SUCCESS, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProxuWebLoginBinding.inflate(layoutInflater)
        setContentViewWithToolbar(binding.root, showHomeAsUp = !isRequiredLogin, title = getString(R.string.auth_web_title))
        if (isRequiredLogin) {
            setFinishOnTouchOutside(false)
        }

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.webProgressBar.visibility = View.VISIBLE
                LogUtil.d(TAG, "Loading auth page: ${url?.substringBefore('?')}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.webProgressBar.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val token = uri.getQueryParameter("token")
                val refreshToken = uri.getQueryParameter("refresh_token")
                val email = uri.getQueryParameter("email")

                if (!token.isNullOrBlank() && !email.isNullOrBlank()) {
                    ProxuAuthManager.saveAuth(this@ProxuWebLoginActivity, token, refreshToken, email)
                    finishLoginSuccessfully()
                    return true
                }

                return false
            }
        }

        binding.webView.webChromeClient = WebChromeClient()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else if (isRequiredLogin) {
                    moveTaskToBack(true)
                } else {
                    setResult(RESULT_CANCELED)
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        binding.webView.loadUrl("https://proxu.pro/login?redirect=app")
    }

    private fun finishLoginSuccessfully() {
        Toast.makeText(this, R.string.auth_login_successful, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)

        val token = ProxuAuthManager.getToken(this)
        if (!token.isNullOrBlank()) {
            lifecycleScope.launch {
                Toast.makeText(this@ProxuWebLoginActivity, R.string.auth_syncing_profiles, Toast.LENGTH_SHORT).show()
                val result = ProxuProfileSync.syncProfilesAndSelectFirst(this@ProxuWebLoginActivity, token)
                LogUtil.i(TAG, "Profile sync result: ${result.message} (added=${result.added}, skipped=${result.skipped})")
                
                if (shouldOpenMainOnSuccess) {
                    val intent = Intent(this@ProxuWebLoginActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
                finish()
            }
        } else {
            if (shouldOpenMainOnSuccess) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            finish()
        }
    }

    companion object {
        private const val TAG = "ProxuWebLogin"
        private const val EXTRA_OPEN_MAIN_ON_SUCCESS = "com.proxu.app.auth.extra.OPEN_MAIN_ON_SUCCESS"

        fun createIntent(context: Context, required: Boolean = false, openMainOnSuccess: Boolean = false): Intent {
            return Intent(context, ProxuWebLoginActivity::class.java)
                .putExtra(ProxuLoginActivity.EXTRA_REQUIRED_LOGIN, required)
                .putExtra(EXTRA_OPEN_MAIN_ON_SUCCESS, openMainOnSuccess)
        }
    }
}
