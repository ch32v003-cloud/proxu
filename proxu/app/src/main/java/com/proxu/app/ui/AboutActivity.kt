package com.proxu.app.ui

import android.os.Bundle
import com.proxu.app.AppConfig
import com.proxu.app.BuildConfig
import com.proxu.app.R
import com.proxu.app.core.CoreNativeManager
import com.proxu.app.databinding.ActivityAboutBinding
import com.proxu.app.util.Utils

class AboutActivity : BaseActivity() {
    private val binding by lazy { ActivityAboutBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(binding.root)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.title_about))

        binding.layoutSoureCcode.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_URL)
        }

        binding.layoutFeedback.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.title_support)
                .setItems(arrayOf(getString(R.string.support_telegram), getString(R.string.support_github))) { _, which ->
                    when (which) {
                        0 -> Utils.openUri(this, AppConfig.TG_CHANNEL_URL)
                        1 -> Utils.openUri(this, AppConfig.APP_ISSUES_URL)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.layoutOssLicenses.setOnClickListener {
            val webView = android.webkit.WebView(this)
            webView.loadUrl("file:///android_asset/open_source_licenses.html")
            android.app.AlertDialog.Builder(this)
                .setTitle("Open source licenses")
                .setView(webView)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        binding.layoutPrivacyPolicy.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_PRIVACY_POLICY)
        }

        "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})".also {
            binding.tvVersion.text = it
        }
        BuildConfig.APPLICATION_ID.also {
            binding.tvAppId.text = it
        }
    }
}