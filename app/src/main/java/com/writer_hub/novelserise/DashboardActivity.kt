/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.engine.core.custom.CustomAlertDialogs
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.service.AudioPlayerService
import com.engine.core.utill.ThemeUtils
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.AppUpdateViewModel
import com.engine.core.viewmodel.DashboardViewModel
import com.engine.core.viewmodel.UpdateCheckState
import com.engine.core.viewmodel.UpdateState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.writer_hub.novelserise.databinding.ActivityDashboardBinding
import com.writer_hub.novelserise.fragment.HomeFragment
import com.writer_hub.novelserise.fragment.LibraryFragment
import com.writer_hub.novelserise.fragment.NotificationFragment
import com.writer_hub.novelserise.fragment.PostFragment
import com.writer_hub.novelserise.fragment.ShopFragment

class DashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var viewModel: DashboardViewModel
    private lateinit var updateViewModel: AppUpdateViewModel

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLoggerHelper.d("Dashboard", "Notification permission granted: $granted")
    }
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() {
            val future = controllerFuture ?: return null
            if (!future.isDone || future.isCancelled) return null
            return try { future.get() } catch (e: Exception) { null }
        }

    private var activeFragment: Fragment? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updatePlaybackProgress()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = AppProvider.getViewModel(application, DashboardViewModel::class.java)
        updateViewModel = AppProvider.getViewModel(application, AppUpdateViewModel::class.java)

        setupBottomNavigation()
        applyThemeToNavigation()
        setupNowPlayingWidget()
        setupBackNavigation()
        observeViewModel()
        requestNotificationPermissionIfNeeded()

        // Initial setup
        if (savedInstanceState == null) {
            showFragment("home")
        } else {
            val currentTag = viewModel.currentTab.value ?: "home"
            restoreFragments(currentTag)
        }

        // Check for updates
        checkAppUpdates()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkAppUpdates() {
        updateViewModel.checkForUpdate()

        updateViewModel.updateCheckState.observe(this) { state ->
            when (state) {
                is UpdateCheckState.UpdateAvailable -> {
                    showUpdateDialog()
                }

                is UpdateCheckState.Error -> {
                    AppLoggerHelper.e("Dashboard", "Update check failed: ${state.message}")
                }

                else -> {}
            }
        }

        updateViewModel.updateState.observe(this) { state ->
            when (state) {
                is UpdateState.DownloadedAndReadyToInstall -> {
                    showInstallReadyDialog()
                }

                is UpdateState.InProgress -> {
                    // Optional: Show a small progress indicator
                }

                is UpdateState.Error -> {
                    Toast.makeText(this, "Update failed: ${state.message}", Toast.LENGTH_SHORT)
                        .show()
                }

                else -> {}
            }
        }
    }

    private fun showUpdateDialog() {
        CustomAlertDialogs.showCustomAlertDialog(
            context = this,
            iconRes = R.drawable.ic_app_mode_switch,
            title = "Update Available",
            message = "A new version of ${getString(R.string.app_name)} is available. Update now to enjoy the latest features and improvements.",
            knowMoreText = "Later",
            okayText = "Update Now",
            onOkayClick = {
                updateViewModel.startUpdateProcess(this)
            }
        )
    }

    private fun showInstallReadyDialog() {
        CustomAlertDialogs.showCustomAlertDialog(
            context = this,
            iconRes = R.drawable.ic_app_mode_switch,
            title = "Update Ready",
            message = "The update has been downloaded and is ready to install. The app will restart to apply the update.",
            okayText = "Install Now",
            onOkayClick = {
                updateViewModel.installFlexibleUpdate()
            }
        )
    }

    private fun observeViewModel() {
        viewModel.currentTab.observe(this) { tag ->
            val id = when (tag) {
                "home" -> R.id.nav_home
                "library" -> R.id.nav_library
                "post" -> R.id.nav_post
                "notifications" -> R.id.nav_notifications
                "shop" -> R.id.nav_shop
                else -> R.id.nav_home
            }
            if (binding.bottomNavigation.selectedItemId != id) {
                binding.bottomNavigation.selectedItemId = id
            }
        }
        
        viewModel.playbackProgress.observe(this) { progress ->
            binding.layoutNowPlaying.progressBarNowPlaying.progress = progress.toInt()
        }
        
        viewModel.playbackDuration.observe(this) { duration ->
            binding.layoutNowPlaying.progressBarNowPlaying.max = duration.toInt()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.bottomNavigation.addOnLayoutChangeListener(publishMiniPlayerAnchor)
        binding.layoutNowPlaying.root.addOnLayoutChangeListener(publishMiniPlayerAnchor)
        binding.bottomNavigation.requestLayout()
        val sessionToken = SessionToken(this, ComponentName(this, AudioPlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            handler.post(updateProgressRunnable)
        }, MoreExecutors.directExecutor())
    }

    private fun updatePlaybackProgress() {
        val currentController = controller ?: return
        if (currentController.isPlaying) {
            viewModel.updatePlaybackStatus(currentController.currentPosition, currentController.duration)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.currentTab.value != "home") {
                    viewModel.setCurrentTab("home")
                    showFragment("home")
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val tag = getTagForId(item.itemId)
            viewModel.setCurrentTab(tag)
            showFragment(tag)
            true
        }
    }

    private fun getTagForId(id: Int): String = when (id) {
        R.id.nav_home -> "home"
        R.id.nav_library -> "library"
        R.id.nav_post -> "post"
        R.id.nav_notifications -> "notifications"
        R.id.nav_shop -> "shop"
        else -> "home"
    }

    private fun restoreFragments(activeTag: String) {
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction()
        
        val tags = listOf("home", "library", "post", "notifications", "shop")
        for (tag in tags) {
            val f = fm.findFragmentByTag(tag) ?: continue
            if (tag == activeTag) {
                transaction.show(f)
                activeFragment = f
            } else {
                transaction.hide(f)
            }
        }
        transaction.commit()
    }

    private fun showFragment(tag: String) {
        if (activeFragment?.tag == tag) return

        val fm = supportFragmentManager
        val existingFragment = fm.findFragmentByTag(tag)
        val transaction = fm.beginTransaction().setReorderingAllowed(true)

        activeFragment?.let { transaction.hide(it) }

        if (existingFragment == null) {
            val newFragment = createFragmentByTag(tag)
            transaction.add(R.id.fragmentContainer, newFragment, tag)
            activeFragment = newFragment
        } else {
            transaction.show(existingFragment)
            activeFragment = existingFragment
        }

        transaction.commit()
    }

    private fun createFragmentByTag(tag: String): Fragment = when (tag) {
        "home" -> HomeFragment()
        "library" -> LibraryFragment()
        "post" -> PostFragment()
        "notifications" -> NotificationFragment()
        "shop" -> ShopFragment()
        else -> HomeFragment()
    }

    private fun setupNowPlayingWidget() {
        GlobalAppData.currentlyPlayingChapter.observe(this) { chapter ->
            if (chapter == null) {
                binding.layoutNowPlaying.root.visibility = View.GONE
                return@observe
            }
            
            binding.layoutNowPlaying.root.visibility = View.VISIBLE
            binding.layoutNowPlaying.tvNowPlayingTitle.text = decodeHtmlToTextV2(chapter.chapterName)
            bindNowPlayingNovel(GlobalAppData.selectedNovel.value)
        }

        // Tap or swipe up on the mini player expands it back into the full player.
        binding.layoutNowPlaying.root.setOnClickListener { openFullPlayer() }
        val swipeSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop * 2
        var downY = 0f
        var swipeHandled = false
        binding.layoutNowPlaying.root.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downY = event.rawY; swipeHandled = false }
                android.view.MotionEvent.ACTION_MOVE -> if (!swipeHandled && downY - event.rawY > swipeSlop) {
                    swipeHandled = true
                    v.isPressed = false
                    openFullPlayer()
                }
            }
            // Once the swipe opened the player, swallow the rest so the release doesn't also count as a tap.
            swipeHandled
        }

        // The novel often loads after playback starts; refresh the cover/title when it arrives.
        GlobalAppData.selectedNovel.observe(this) { novel ->
            if (GlobalAppData.currentlyPlayingChapter.value != null) bindNowPlayingNovel(novel)
        }

        GlobalAppData.isPlaying.observe(this) { isPlaying ->
            binding.layoutNowPlaying.btnNowPlayingPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
            )
        }

        binding.layoutNowPlaying.btnNowPlayingPlayPause.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            if (c.isPlaying) c.pause() else c.play()
        }

        binding.layoutNowPlaying.btnNowPlayingClose.setOnClickListener {
            controller?.stop()
            GlobalAppData.currentlyPlayingChapter.postValue(null)
        }
    }

    private var nowPlayingCoverUrl: String? = null

    private fun openFullPlayer() {
        val chapter = GlobalAppData.currentlyPlayingChapter.value ?: return
        val chapterId = chapter.id ?: return
        val novelId = chapter.novelId?.takeIf { it.isNotBlank() } ?: GlobalAppData.selectedNovel.value?.id
        if (!novelId.isNullOrBlank()) AudioPlayerActivity.start(this, chapterId, novelId)
        else PlayChapterActivity.start(this, chapterId, chapter)
    }

    private fun bindNowPlayingNovel(novel: com.engine.core.models.Novel?) {
        binding.layoutNowPlaying.tvNowPlayingNovel.text = decodeHtmlToTextV2(novel?.title ?: "")
        val url = novel?.imageLink?.takeIf { it.isNotBlank() } ?: novel?.imageLinkCover?.takeIf { it.isNotBlank() } ?: novel?.bannerImage
        if (url == nowPlayingCoverUrl) return
        nowPlayingCoverUrl = url
        ImageHelper.loadImage(this, url, binding.layoutNowPlaying.ivNowPlayingCover)
    }

    /** Tells the full audio player where the mini player sits (directly above the bottom nav). */
    private val publishMiniPlayerAnchor = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val location = IntArray(2)
        binding.bottomNavigation.getLocationOnScreen(location)
        MiniPlayerAnchor.bottomY = location[1]

        // Exact part positions, so the collapsing player lands on the same pixels as the real mini player.
        val mini = binding.layoutNowPlaying
        if (mini.root.width == 0 || mini.root.height == 0) return@OnLayoutChangeListener
        fun rectOf(v: View) = android.graphics.RectF(v.left.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
        MiniPlayerAnchor.coverRect = rectOf(mini.ivNowPlayingCover)
        MiniPlayerAnchor.titleRect = rectOf(mini.tvNowPlayingNovel)
        MiniPlayerAnchor.subtitleRect = rectOf(mini.tvNowPlayingTitle)
        MiniPlayerAnchor.playRect = rectOf(mini.btnNowPlayingPlayPause)
        MiniPlayerAnchor.closeRect = rectOf(mini.btnNowPlayingClose)
        MiniPlayerAnchor.titleTextPx = mini.tvNowPlayingNovel.textSize
        MiniPlayerAnchor.subtitleTextPx = mini.tvNowPlayingTitle.textSize
    }

    private fun applyThemeToNavigation() {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
        val colors = intArrayOf(
            ThemeUtils.getThemeColor(this, androidx.appcompat.R.attr.colorPrimary),
            ThemeUtils.getThemeColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
        val navigationColorStateList = ColorStateList(states, colors)
        binding.bottomNavigation.itemIconTintList = navigationColorStateList
        binding.bottomNavigation.itemTextColor = navigationColorStateList
    }

    override fun onStop() {
        binding.bottomNavigation.removeOnLayoutChangeListener(publishMiniPlayerAnchor)
        binding.layoutNowPlaying.root.removeOnLayoutChangeListener(publishMiniPlayerAnchor)
        MiniPlayerAnchor.bottomY = null
        MiniPlayerAnchor.clearParts()
        handler.removeCallbacks(updateProgressRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onStop()
    }
}
