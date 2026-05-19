@file:Suppress("DEPRECATION")

package com.proxu.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.proxu.app.R
import com.proxu.app.databinding.ActivityProxuLoginBinding
import com.proxu.app.handler.SettingsChangeManager
import com.proxu.app.ui.BaseActivity
import com.proxu.app.ui.MainActivity
import com.proxu.app.util.LogUtil
import com.proxu.app.util.Utils
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxuLoginActivity : BaseActivity() {
    private lateinit var binding: ActivityProxuLoginBinding
    private var googleSignInClient: GoogleSignInClient? = null

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        binding.loginProgressBar.visibility = View.GONE
        binding.googleSignInButton.isEnabled = true

        if (result.resultCode != RESULT_OK) {
            if (result.resultCode != RESULT_CANCELED) {
                showError(getString(R.string.auth_error_with_code, result.resultCode))
            }
            return@registerForActivityResult
        }

        handleGoogleSignInResult(result.data)
    }

    private val webLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            finishLoginSuccessfully()
        }
    }

    private val isRequiredLogin: Boolean
        get() = intent.getBooleanExtra(EXTRA_REQUIRED_LOGIN, false)
    private val shouldOpenMainOnSuccess: Boolean
        get() = intent.getBooleanExtra(EXTRA_OPEN_MAIN_ON_SUCCESS, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProxuLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge: draw behind system bars (status bar / nav bar)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (!Utils.getDarkModeStatus(this)) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }

        // Optional: hide the system ActionBar so nothing overlaps the background
        supportActionBar?.hide()

        // For non-required login (e.g. launched from Settings), keep system back button only
        configureRequiredLoginGate()

        // Hide Google Sign-In if Play Services are unavailable (e.g. F-Droid builds)
        val availability = GoogleApiAvailability.getInstance()
        if (availability.isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            binding.googleSignInButton.visibility = View.GONE
            binding.webSignInButton.visibility = View.VISIBLE
        }

        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions())

        binding.googleSignInButton.setOnClickListener {
            binding.errorText.visibility = View.GONE
            binding.loginProgressBar.visibility = View.VISIBLE
            binding.googleSignInButton.isEnabled = false
            launchGoogleSignIn()
        }

        binding.webSignInButton.setOnClickListener {
            webLoginLauncher.launch(ProxuWebLoginActivity.createIntent(this, isRequiredLogin, shouldOpenMainOnSuccess))
        }
    }

    private fun configureRequiredLoginGate() {
        if (!isRequiredLogin) return

        setFinishOnTouchOutside(false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    private fun googleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(getString(R.string.google_web_client_id))
            .build()
    }

    private fun launchGoogleSignIn() {
        val availability = GoogleApiAvailability.getInstance()
        val status = availability.isGooglePlayServicesAvailable(this)
        if (status != ConnectionResult.SUCCESS) {
            binding.loginProgressBar.visibility = View.GONE
            binding.googleSignInButton.isEnabled = true
            if (availability.isUserResolvableError(status)) {
                availability.getErrorDialog(this, status, 0)?.show()
            } else {
                showError(getString(R.string.auth_google_play_services_unavailable))
            }
            return
        }

        val client = googleSignInClient ?: GoogleSignIn.getClient(this, googleSignInOptions()).also {
            googleSignInClient = it
        }
        signInLauncher.launch(client.signInIntent)
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val idToken = account.idToken
            val email = account.email.orEmpty()
            val name = account.displayName.orEmpty()

            if (idToken.isNullOrBlank()) {
                showError(getString(R.string.auth_google_token_missing))
                return
            }

            sendTokenToServer(idToken, email, name)
        } catch (e: ApiException) {
            LogUtil.w(TAG, "Google Sign-In failed with status ${e.statusCode}", e)
            val errorMessage = when (e.statusCode) {
                10 -> getString(R.string.auth_google_signin_config_error)
                7 -> getString(R.string.auth_network_error)
                12501 -> getString(R.string.auth_cancelled)
                else -> getString(R.string.auth_google_signin_error_with_code, e.statusCode)
            }
            showError(errorMessage)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Google Sign-In failed", e)
            showError(getString(R.string.auth_error_with_message, e.message ?: getString(R.string.auth_unknown_error)))
        }
    }

    private fun sendTokenToServer(idToken: String, email: String, name: String) {
        binding.loginProgressBar.visibility = View.VISIBLE
        binding.googleSignInButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // New endpoint auto-creates users with 50 rub balance
                val response = ProxuAuthApiService.loginWithGoogle(idToken)
                withContext(Dispatchers.Main) {
                    binding.loginProgressBar.visibility = View.GONE
                    binding.googleSignInButton.isEnabled = true
                    handleAuthResponse(response, email, name)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Google auth backend request failed", e)
                withContext(Dispatchers.Main) {
                    binding.loginProgressBar.visibility = View.GONE
                    binding.googleSignInButton.isEnabled = true
                    showError(getString(R.string.auth_network_error_with_message, e.message ?: getString(R.string.auth_unknown_error)))
                }
            }
        }
    }

    private fun handleAuthResponse(response: AuthResponse, email: String, name: String) {
        LogUtil.d(TAG, "handleAuthResponse: code=${response.code}, token=${!response.token.isNullOrBlank()}, balance=${response.balance}, error=${response.error}")
        val token = response.token
        if (!token.isNullOrBlank()) {
            LogUtil.i(TAG, "Auth success! Saving token, balance=${response.balance}")
            ProxuAuthManager.saveAuth(this, token, response.refreshToken, email, response.balance)
            
            // Show welcome toast for new users (balance = 50 is the default)
            if (response.balance == "50" || response.balance == "50.0") {
                Toast.makeText(this, "Добро пожаловать! Ваш аккаунт создан, баланс: 50 руб.", Toast.LENGTH_LONG).show()
            }
            
            finishLoginSuccessfully()
            return
        }

        val message = when (response.code) {
            400 -> response.error ?: getString(R.string.auth_invalid_request)
            401 -> response.error ?: getString(R.string.auth_invalid_google_token)
            404 -> response.error ?: getString(R.string.auth_endpoint_not_found)
            500 -> response.error ?: "Ошибка сервера. Попробуйте позже."
            else -> response.error ?: response.message ?: getString(R.string.auth_server_error_with_code, response.code)
        }
        showError(message)
    }

    private fun finishLoginSuccessfully() {
        Toast.makeText(this, R.string.auth_login_successful, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        
        val token = ProxuAuthManager.getToken(this)
        if (!token.isNullOrBlank()) {
            lifecycleScope.launch {
                // Sync profiles from server
                Toast.makeText(this@ProxuLoginActivity, R.string.auth_syncing_profiles, Toast.LENGTH_SHORT).show()
                val result = ProxuProfileSync.syncProfilesAndSelectFirst(this@ProxuLoginActivity, token)
                LogUtil.i(TAG, "Profile sync result: ${result.message} (added=${result.added}, skipped=${result.skipped})")
                
                // Download and apply remote configuration (if available)
                LogUtil.i(TAG, "Attempting to download remote config...")
                val configApplied = ProxuConfigDownloader.downloadAndApplyConfig(this@ProxuLoginActivity)
                if (configApplied) {
                    Toast.makeText(this@ProxuLoginActivity, R.string.auth_downloading_config, Toast.LENGTH_SHORT).show()
                } else {
                    LogUtil.i(TAG, "Remote config not available or failed to apply")
                }
                
                // Request MainActivity to refresh groups
                SettingsChangeManager.makeSetupGroupTab()
                
                if (shouldOpenMainOnSuccess) {
                    val intent = Intent(this@ProxuLoginActivity, MainActivity::class.java)
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

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
        binding.webSignInButton.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_REQUIRED_LOGIN = "com.proxu.app.auth.extra.REQUIRED_LOGIN"
        private const val EXTRA_OPEN_MAIN_ON_SUCCESS = "com.proxu.app.auth.extra.OPEN_MAIN_ON_SUCCESS"
        private const val TAG = "ProxuLogin"

        fun createIntent(context: Context, required: Boolean = false, openMainOnSuccess: Boolean = false): Intent {
            return Intent(context, ProxuLoginActivity::class.java)
                .putExtra(EXTRA_REQUIRED_LOGIN, required)
                .putExtra(EXTRA_OPEN_MAIN_ON_SUCCESS, openMainOnSuccess)
        }
    }
}
