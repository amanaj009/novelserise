/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.app.Application
import com.engine.core.AppCore
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.manager.GlobalAppData
import com.engine.core.manager.ThemePrefManager
import com.engine.core.manager.TokenManager
import com.engine.core.notification.SMInternalNotificationManager
import com.engine.core.notification.SMNotificationHelper
import com.engine.core.repo.FCMRepository
import com.engine.core.viewmodel.AppProvider
import com.google.android.gms.ads.MobileAds
import com.writer_hub.novelserise.ui.handlePayload.HandlePayloadActivity
import com.writer_hub.novelserise.util.RateAppManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Disable all local caching and data flags
        GlobalAppData.isLocalCachingEnabled = false
        GlobalAppData.isLocalDataReady = false
        GlobalAppData.updateLocalDataReady(false)

        // Initialize TokenManager
        val tokenManager = TokenManager.getInstance(this)
        
        // Initialize StoryMania module
        AppCore.init(
            context = this,
            appName = "NovelSeries",
            baseUrl = "https://app.novelseries.in",
            serverKey = "e51e37d9a91867c770703cf11dab3881e31673c7-45a8fd5ac0443e498eb24540293f0a38-82221824",
            apiKey = "",
            shareFaceLink = "https://app.novelseries.in",
            isDebug = BuildConfig.DEBUG,
            getUserId = { tokenManager.getTokenAndUserId().second ?: "-1" }
        )

        // Register the Auth Activity
        AppCore.registerAuthActivity(AuthActivity::class.java)

        // Initialize AppProvider
        AppProvider.initSharedPreferences(this)

        // Apply theme
        ThemePrefManager.getInstance(this).applyTheme()

        // Increment app launch count
        RateAppManager.incrementLaunchCount(this)

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Initialize Notification System & Ensure FCM Topic Subscription
        appScope.launch(Dispatchers.IO) {
            try {
                SMNotificationHelper.init(
                    HandlePayloadActivity::class.java,
                    SplashActivity::class.java
                )
                SMInternalNotificationManager.initializeChannels()

                // Sync Token and Topic subscription on startup
                val fcmRepo = FCMRepository(this@MyApplication)
                fcmRepo.sendFCMTokenToServer()
                fcmRepo.subscribeToPlatform()

                AppLoggerHelper.d(
                    "FCM_INIT",
                    "FCM setup and Topic subscription completed on startup."
                )
            } catch (e: Exception) {
                AppLoggerHelper.e("FCM_INIT", "Error during FCM setup: ${e.message}")
            }
        }
    }
}
