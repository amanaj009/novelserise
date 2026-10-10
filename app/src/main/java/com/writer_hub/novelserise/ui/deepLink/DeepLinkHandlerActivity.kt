/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.deepLink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.engine.core.helper.AppLoggerHelper
import com.writer_hub.novelserise.BaseActivity
import com.writer_hub.novelserise.SplashActivity

class DeepLinkHandlerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deepLinkData: Uri? = intent?.data
        AppLoggerHelper.d("DeepLinkHandler", "Deep link received: $deepLinkData")

        if (deepLinkData == null) {
            finish()
            return
        }

        if (isTaskRoot || DeepLinkRouter.needsModeSwitch(deepLinkData)) {
            // App is not running, or the link needs the other app mode (audio vs novel): screens
            // already open were built for the old mode, so restart through Splash, which switches
            // the mode, reloads data and then opens the link.
            AppLoggerHelper.d("DeepLinkHandler", "Routing through Splash (cold start or mode switch)")
            val splashIntent = Intent(this, SplashActivity::class.java).apply {
                data = deepLinkData
                putExtra("deep_link_uri", deepLinkData.toString())
                if (!isTaskRoot) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(splashIntent)
            finish()
        } else {
            // App is already running in the right mode, handle immediately
            DeepLinkRouter.open(this, deepLinkData)
            finish()
        }
    }
}
