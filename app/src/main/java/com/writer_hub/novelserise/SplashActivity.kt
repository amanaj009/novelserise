/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.engine.core.AppCore.tokenManager
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.ConfigureModel
import com.engine.core.models.Notification
import com.engine.core.models.Novel
import com.engine.core.models.UserDetailsModel
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.HomePageViewModel
import com.engine.core.viewmodel.NovelViewModel
import com.engine.core.viewmodel.SplashViewModel
import com.engine.core.viewmodel.UserViewModel
import com.writer_hub.novelserise.databinding.ActivitySplashBinding
import com.writer_hub.novelserise.helper.NotificationHandler
import com.writer_hub.novelserise.ui.deepLink.DeepLinkRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private var currentUserData: UserDetailsModel? = null
    private var currentSiteSettings: ConfigureModel? = null
    
    private lateinit var splashViewModel: SplashViewModel
    private lateinit var homePageViewModel: HomePageViewModel
    private lateinit var userViewModel: UserViewModel
    private lateinit var novelViewModel: NovelViewModel

    private var isReady = false

    private lateinit var binding: ActivitySplashBinding
    private var dataLoaded = false
    private var videoFinished = false
    private var navigated = false
    private val videoTimeout = Runnable { finishVideo() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system launch splash is plain black with no icon (Theme.App.Starting) and leaves at
        // once; the launch video below takes over on the same black.
        installSplashScreen()
        setupIntentExtras()
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Shared links and notification taps should open their content without the intro.
        if (opensContentDirectly()) finishVideo() else playLaunchVideo()

        lifecycleScope.launch {
            loadData()
            dataLoaded = true
            navigateWhenReady()
        }
    }

    private fun opensContentDirectly(): Boolean =
        intent?.data != null || intent?.hasExtra("deep_link_uri") == true || intent?.hasExtra("notification_data") == true

    private fun playLaunchVideo() {
        binding.videoSplash.apply {
            setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.splash_video}"))
            setOnCompletionListener { finishVideo() }
            setOnErrorListener { _, _, _ -> finishVideo(); true }
            start()
        }
        // Never hold the user on the intro if playback stalls (the clip itself is 4.3s).
        binding.root.postDelayed(videoTimeout, 7000L)
    }

    private fun finishVideo() {
        binding.root.removeCallbacks(videoTimeout)
        videoFinished = true
        navigateWhenReady()
    }

    /** Leaves once both the intro and the startup data are done, and only while on screen. */
    private fun navigateWhenReady() {
        if (navigated || !videoFinished || !dataLoaded) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        navigated = true
        openNextActivity()
    }

    override fun onStart() {
        super.onStart()
        navigateWhenReady()
    }

    override fun onStop() {
        super.onStop()
        // A video stopped in the background does not resume cleanly; skip the rest of it.
        if (!videoFinished) {
            binding.videoSplash.stopPlayback()
            binding.root.removeCallbacks(videoTimeout)
            videoFinished = true
        }
    }

    /**
     * Fix for ClassNotFoundException when unmarshalling Parcelables.
     */
    private fun setupIntentExtras() {
        intent?.setExtrasClassLoader(Novel::class.java.classLoader)
    }

    private suspend fun loadData() {
        splashViewModel = AppProvider.getViewModel(application, SplashViewModel::class.java)
        homePageViewModel = AppProvider.getViewModel(application, HomePageViewModel::class.java)

        // Initialize Global Language Settings from Saved Preferences
        GlobalAppData.selectedAppContantLanguage = splashViewModel.getContentLanguage()
        AppLoggerHelper.d("Splash", "Initialized Content Language: ${GlobalAppData.selectedAppContantLanguage}")

        val (accessToken, userId) = tokenManager.getTokenAndUserId()
        if (accessToken != null && userId != null) {
            novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)
            userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)
            loadDataForLoggedInUser()
        } else {
            loadDataForGuest()
        }
    }

    private suspend fun loadDataForLoggedInUser() = coroutineScope {
        try {
            val siteJob = async(Dispatchers.IO) {
                currentSiteSettings = homePageViewModel.fetchSiteSettings(false)
                
                // Only override app type if it hasn't been configured by the user yet
                if (!splashViewModel.isAppTypeConfigured()) {
                    currentSiteSettings?.config?.appType?.let {
                        val enforcedType = when (it) {
                            GlobalAppData.APP_TYPE_AUDIO, GlobalAppData.APP_TYPE_ONLY_AUDIO -> GlobalAppData.APP_TYPE_ONLY_AUDIO
                            else -> GlobalAppData.APP_TYPE_ONLY_NOVEL
                        }
                        splashViewModel.setAppType(enforcedType)
                        // Note: We don't mark as configured here to let HomeFragment show the selection popup
                    }
                } else {
                    // Sync GlobalAppData with saved local type
                    GlobalAppData.appType = splashViewModel.getAppType()
                }
            }

            val userJob = async(Dispatchers.IO) {
                currentUserData = userViewModel.getUserDataDirect(true)
            }

            val novelJob = async(Dispatchers.IO) {
                novelViewModel.getFeaturedNovelFromSharedPreferences()
            }

            siteJob.await()
            userJob.await()
            novelJob.await()
        } catch (e: Exception) {
            AppLoggerHelper.e("Splash", "Error loading logged-in data", e)
        }
    }

    private suspend fun loadDataForGuest() {
        try {
            currentSiteSettings = homePageViewModel.fetchSiteSettings(false)
            
            if (!splashViewModel.isAppTypeConfigured()) {
                currentSiteSettings?.config?.appType?.let {
                    val enforcedType = when (it) {
                        GlobalAppData.APP_TYPE_AUDIO, GlobalAppData.APP_TYPE_ONLY_AUDIO -> GlobalAppData.APP_TYPE_ONLY_AUDIO
                        else -> GlobalAppData.APP_TYPE_ONLY_NOVEL
                    }
                    splashViewModel.setAppType(enforcedType)
                }
            } else {
                GlobalAppData.appType = splashViewModel.getAppType()
            }
        } catch (e: Exception) {
            AppLoggerHelper.e("Splash", "Error loading guest data", e)
        }
    }

    private fun openNextActivity() {
        val (accessToken, userId) = tokenManager.getTokenAndUserId()
        val isLoggedIn = accessToken != null && userId != null

        if (!isLoggedIn) {
            val intent = Intent(this@SplashActivity, AuthActivity::class.java)
            startActivity(intent)
            isReady = true
            finish()
        } else {
            checkDeepLinkOrNotification()
        }

        // We use a custom transition for a more premium feel
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun checkDeepLinkOrNotification() {
        val notification = intent?.let {
            IntentCompat.getParcelableExtra<Notification>(
                it,
                "notification_data",
                Notification::class.java
            )
        }

        if (notification != null) {
            NotificationHandler.handleNotificationClick(this, notification)
            isReady = true
            finish()
            return
        }

        val deepLinkUriString = intent?.getStringExtra("deep_link_uri")
        val deepLinkData =
            if (deepLinkUriString != null) Uri.parse(deepLinkUriString) else intent?.data

        if (deepLinkData != null) {
            handleDeepLink(deepLinkData)
            return
        }

        val intent = if (!splashViewModel.isLanguageSelected()) {
            Intent(this@SplashActivity, LanguageSelectionActivity::class.java)
        } else {
            Intent(this@SplashActivity, DashboardActivity::class.java)
        }
        
        startActivity(intent)
        isReady = true
        finish()
    }

    private fun handleDeepLink(uri: Uri) {
        // An audio-episode link must open in audio mode (and a chapter link in novel mode),
        // whatever mode the app was last in.
        DeepLinkRouter.applyMode(uri, splashViewModel)
        DeepLinkRouter.openOrDashboard(this, uri)
        isReady = true
        finish()
    }
}
