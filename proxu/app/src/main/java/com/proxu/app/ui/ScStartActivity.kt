package com.proxu.app.ui

import android.os.Bundle
import com.proxu.app.R
import com.proxu.app.handler.V2RayServiceManager

class ScStartActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(R.layout.activity_none)

        if (!V2RayServiceManager.isRunning()) {
            V2RayServiceManager.startVServiceFromToggle(this)
        }
        finish()
    }
}

