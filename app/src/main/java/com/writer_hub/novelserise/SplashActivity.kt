/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
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
    private val dataLoadFallback = Runnable { startLoadingData() }
    private var dataLoadStarted = false
    private var mediaPlayer: MediaPlayer? = null
    private var playerPrepared = false
    private var surfaceReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system launch splash (Theme.App.Starting) shows the logo on black from the moment
        // of the tap and leaves at once; the launch animation takes over from the same logo.
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
        if (opensContentDirectly()) {
            finishVideo()
            startLoadingData()
        } else if (USE_LAUNCH_VIDEO) {
            binding.splashAnimation.visibility = View.GONE
            binding.videoSplash.visibility = View.VISIBLE
            playLaunchVideo()
            // Startup data loading does main-thread work; let the first video frame show first.
            // The fallback covers a video that never starts rendering.
            binding.root.postDelayed(dataLoadFallback, 800L)
        } else {
            playLaunchAnimation()
            // Data loading starts once the animation is on screen (see playLaunchAnimation);
            // the fallback covers an animation that never gets to draw.
            binding.root.postDelayed(dataLoadFallback, 1500L)
        }
    }

    private fun playLaunchAnimation() {
        binding.splashAnimation.onFramesShowing = { startLoadingData() }
        binding.splashAnimation.onFinished = { finishVideo() }
        binding.splashAnimation.start()
        binding.root.postDelayed(videoTimeout, 5000L)
    }

    private fun startLoadingData() {
        if (dataLoadStarted) return
        dataLoadStarted = true
        binding.root.removeCallbacks(dataLoadFallback)
        lifecycleScope.launch {
            loadData()
            dataLoaded = true
            navigateWhenReady()
        }
    }

    private fun opensContentDirectly(): Boolean =
        intent?.data != null || intent?.hasExtra("deep_link_uri") == true || intent?.hasExtra("notification_data") == true

    /**
     * Prepares the player straight away, in parallel with the surface being created (VideoView
     * only starts preparing once its surface exists), and starts as soon as both are ready.
     */
    private fun playLaunchVideo() {
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            resources.openRawResourceFd(R.raw.splash_video).use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
        } catch (e: Exception) {
            AppLoggerHelper.e("Splash", "Launch video unavailable", e)
            finishVideo()
            return
        }
        player.setOnPreparedListener {
            playerPrepared = true
            fitVideo(it.videoWidth, it.videoHeight)
            startVideoIfReady()
        }
        player.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) startLoadingData()
            false
        }
        player.setOnCompletionListener { finishVideo() }
        player.setOnErrorListener { _, _, _ -> finishVideo(); true }
        player.prepareAsync()

        binding.videoSplash.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                mediaPlayer?.setDisplay(holder)
                surfaceReady = true
                startVideoIfReady()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady = false
                mediaPlayer?.setDisplay(null)
            }
        })
        // Never hold the user on the intro if playback stalls (the clip itself is 4.3s).
        binding.root.postDelayed(videoTimeout, 7000L)
    }

    private fun startVideoIfReady() {
        if (playerPrepared && surfaceReady && !videoFinished) mediaPlayer?.start()
    }

    /** Scales the surface to fit the screen while keeping the video's aspect ratio. */
    private fun fitVideo(videoWidth: Int, videoHeight: Int) {
        val root = binding.root
        if (videoWidth <= 0 || videoHeight <= 0) return
        if (root.width == 0 || root.height == 0) {
            // Prepared before the first layout; fit once the screen size is known.
            root.post { fitVideo(videoWidth, videoHeight) }
            return
        }
        val scale = minOf(root.width / videoWidth.toFloat(), root.height / videoHeight.toFloat())
        binding.videoSplash.layoutParams = binding.videoSplash.layoutParams.apply {
            width = (videoWidth * scale).toInt()
            height = (videoHeight * scale).toInt()
        }
    }

    private fun finishVideo() {
        binding.root.removeCallbacks(videoTimeout)
        videoFinished = true
        releasePlayer()
        startLoadingData()
        navigateWhenReady()
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        /** true plays res/raw/splash_video instead of the code-drawn [SplashAnimationView]. */
        private const val USE_LAUNCH_VIDEO = false
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(dataLoadFallback)
        releasePlayer()
        super.onDestroy()
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
            releasePlayer()
            binding.root.removeCallbacks(videoTimeout)
            videoFinished = true
            startLoadingData()
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
        DeepLinkRouter.openWithDashboardBackStack(this, uri)
        isReady = true
        finish()
    }
}
