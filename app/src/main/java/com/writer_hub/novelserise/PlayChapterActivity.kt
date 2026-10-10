/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import com.writer_hub.novelserise.ui.deepLink.DeepLinkRouter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.engine.core.AppCore.tokenManager
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.models.Novel
import com.engine.core.models.WoChapter
import com.engine.core.service.AudioPlayerService
import com.engine.core.viewmodel.AdsViewModel
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.GiftViewModel
import com.engine.core.viewmodel.NovelViewModel
import com.engine.core.viewmodel.SplashViewModel
import com.engine.core.viewmodel.WoChapterUpdateViewModel
import com.engine.core.viewmodel.WoChpterViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.writer_hub.novelserise.adapter.GiftAdapter
import com.writer_hub.novelserise.databinding.ActivityPlayChapterBinding
import com.writer_hub.novelserise.databinding.ItemMoreOptionBinding
import com.writer_hub.novelserise.databinding.ItemTimerOptionBinding
import com.writer_hub.novelserise.databinding.LayoutGiveGiftDialogBinding
import com.writer_hub.novelserise.databinding.LayoutPlayChapterMoreBinding
import com.writer_hub.novelserise.databinding.LayoutSleepTimerDialogBinding
import com.writer_hub.novelserise.sheet.UnlockChapterBottomSheetNew
import com.writer_hub.novelserise.util.DisplayText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PlayChapterActivity : BaseActivity() {

    private lateinit var binding: ActivityPlayChapterBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private var chapterId: String? = null
    private var novelId: String? = null
    private var currentChapter: WoChapter? = null
    private var chaptersList: MutableList<WoChapter> = mutableListOf()

    private lateinit var woChpterViewModel: WoChpterViewModel
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var splashViewModel: SplashViewModel
    private lateinit var adsViewModel: AdsViewModel
    private lateinit var chapterUpdateViewModel: WoChapterUpdateViewModel
    private lateinit var giftViewModel: GiftViewModel
    
    private var unlockBottomSheet: UnlockChapterBottomSheetNew? = null
    private var isTemporarilyUnlocked: Boolean = false

    private var autoUnlock: Boolean = false
    private var autoPlay: Boolean = true
    private var lastQueuedChapterId: String? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }
    
    private var sleepTimerRunnable: Runnable? = null
    private var sleepTimerHandler = Handler(Looper.getMainLooper())

    private var isFetchingNext = false
    private var isFetchingPrev = false
    private var lastReportedProgress: Int = 0
    private var isInitialSeekDone = false
    private var pendingInterstitialAction: (() -> Unit)? = null
    private val interstitialFallback = Runnable { completePendingInterstitialAction() }

    companion object {
        private const val PLAYER_PREFS = "audio_player_preferences"
        private const val PREF_AUTO_UNLOCK = "auto_unlock"
        private const val PREF_AUTO_PLAY = "auto_play"
        private const val EXTRA_CHAPTER_ID = "extra_chapter_id"
        private const val EXTRA_CHAPTER_DATA = "extra_chapter_data"
        private const val EXTRA_CHAPTER_BUNDLE = "extra_chapter_bundle"
        private const val EXTRA_NOVEL_ID = "extra_novel_id"
        private const val TAG = "PlayChapterActivity"
        private const val PREF_MIDROLL_COUNT = "midroll_transition_count"
        private const val MIDROLL_INTERVAL = 3

        fun start(context: Context, chapterId: String, chapter: WoChapter? = null, chapters: ArrayList<WoChapter>? = null) {
            val intent = Intent(context, PlayChapterActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(EXTRA_CHAPTER_ID, chapterId)
                if (chapter != null) {
                    val bundle = Bundle()
                    bundle.putParcelable(EXTRA_CHAPTER_DATA, chapter)
                    putExtra(EXTRA_CHAPTER_BUNDLE, bundle)
                    chapter.novelId?.let { putExtra(EXTRA_NOVEL_ID, it) }
                }
            }
            context.startActivity(intent)
        }

        fun start(context: Context, chapterId: String, novelId: String) {
            val intent = Intent(context, PlayChapterActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(EXTRA_CHAPTER_ID, chapterId)
                putExtra(EXTRA_NOVEL_ID, novelId)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureScreen()
        binding = ActivityPlayChapterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        getSharedPreferences(PLAYER_PREFS, Context.MODE_PRIVATE).let { preferences ->
            autoUnlock = preferences.getBoolean(PREF_AUTO_UNLOCK, false)
            autoPlay = preferences.getBoolean(PREF_AUTO_PLAY, true)
        }

        novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)
        splashViewModel = AppProvider.getViewModel(application, SplashViewModel::class.java)
        adsViewModel = AppProvider.getViewModel(application, AdsViewModel::class.java)
        chapterUpdateViewModel = AppProvider.getViewModel(application, WoChapterUpdateViewModel::class.java)
        giftViewModel = AppProvider.getViewModel(application, GiftViewModel::class.java)

        chapterId = intent.getStringExtra(EXTRA_CHAPTER_ID)
        novelId = intent.getStringExtra(EXTRA_NOVEL_ID)
        
        if (novelId == null) {
            val bundle = intent.getBundleExtra(EXTRA_CHAPTER_BUNDLE)
            val chapter = if (bundle != null) {
                bundle.classLoader = WoChapter::class.java.classLoader
                bundle.getParcelable<WoChapter>(EXTRA_CHAPTER_DATA)
            } else {
                intent.getParcelableExtra(EXTRA_CHAPTER_DATA)
            }
            novelId = chapter?.novelId
        }

        if (chapterId == null || novelId == null) {
            Toast.makeText(this, "Error: Missing data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        woChpterViewModel = AppProvider.getUniqueViewModel(application, WoChpterViewModel::class.java, novelId!!)
        woChpterViewModel.setSelectedNovelId(novelId!!)
        woChpterViewModel.clearChapterCache()

        setupViewPager()
        setupObservers()
        setupListeners()
        loadNovelDetails()
        loadChapters(chapterId!!)
    }

    private fun setupViewPager() {
        val adapter = PlayChapterPagerAdapter(this, novelId!!)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 3
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Details"
                1 -> "Chapters"
                2 -> "Comments"
                else -> null
            }
        }.attach()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val newChapterId = intent.getStringExtra(EXTRA_CHAPTER_ID)
        val newNovelId = intent.getStringExtra(EXTRA_NOVEL_ID)

        if (newChapterId != null && newNovelId != null) {
             if (newNovelId != novelId) {
                  novelId = newNovelId
                  chapterId = newChapterId
                  chaptersList.clear()
                  currentChapter = null
                  lastQueuedChapterId = null
                  woChpterViewModel = AppProvider.getUniqueViewModel(application, WoChpterViewModel::class.java, novelId!!)
                  woChpterViewModel.setSelectedNovelId(novelId!!)
                  woChpterViewModel.clearChapterCache()
                  setupViewPager()
                  loadNovelDetails()
                  loadChapters(chapterId!!)
             } else if (newChapterId != chapterId) {
                  loadChapters(newChapterId)
             }
        }
    }

    private fun loadChapters(readingId: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val currentIdInt = readingId.toIntOrNull()
                if (currentIdInt != null) {
                    val chapter = woChpterViewModel.getChapter(currentIdInt)
                    if (chapter != null) {
                        applySessionStatusToChapter(chapter)
                        initializeWithChapter(chapter)
                    }
                }

                woChpterViewModel.fetchChapters(
                    order = woChpterViewModel.cachedOrder,
                    offset = 1,
                    status = "published",
                    pagination = 1,
                    scrolling = false,
                    extra = true,
                    currentReading = "0",
                    fromPage = "player"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chapters", e)
                showLoading(false)
            }
        }
    }

    private fun loadNovelDetails() {
        val id = novelId ?: return
        lifecycleScope.launch {
            val localNovel = novelViewModel.fetchSingleNovelById(id, "")
            localNovel?.let { displayNovel(it) }

            val novel = novelViewModel.fetchSingleNovelById(id, "detail")
            if (novel != null && novel.id != null) {
                displayNovel(novel)
            }
        }
    }

    private fun displayNovel(novel: Novel) {
        GlobalAppData.selectedNovel.value = novel
        currentChapter?.let { setupUI(it) }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.shimmerView.visibility = View.VISIBLE
            binding.shimmerView.startShimmer()
            binding.layoutMainContent.visibility = View.GONE
        } else {
            binding.shimmerView.stopShimmer()
            binding.shimmerView.visibility = View.GONE
            binding.layoutMainContent.visibility = View.VISIBLE
            checkAndShowBannerAd()
            checkAndShowNativeAd()
            checkAndShowSmallBannerAd()
        }
    }
    
    private fun checkAndShowBannerAd() {
        if (GlobalVariableManager.shouldShowBannerAd()) {
            adsViewModel.showBannerAd(this, binding.adContainer)
        } else {
            binding.adContainer.visibility = View.GONE
        }
    }

    private fun checkAndShowNativeAd() {
        if (GlobalVariableManager.isAdsEnabled() && !GlobalVariableManager.hasReadingSubscriptionPack()) {
            adsViewModel.showNativeAd(this, binding.nativeAdContainer, R.layout.layout_native_ad_player)

            // Toggle visibility of the container itself when ad is loaded
            binding.nativeAdContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                binding.nativeAdContainer.visibility = if(binding.nativeAdContainer.childCount > 0) View.VISIBLE else View.GONE
            }
        } else {
            binding.nativeAdContainer.visibility = View.GONE
        }
    }

    private fun checkAndShowSmallBannerAd() {
        if (GlobalVariableManager.shouldShowBannerAd()) {
            adsViewModel.showBannerAd(this, binding.smallBannerAdContainer)
            binding.cardSmallAd.visibility = View.VISIBLE
        } else {
            binding.cardSmallAd.visibility = View.GONE
        }
    }

    private fun applySessionStatusToChapter(chapter: WoChapter) {
        val buyMap = woChpterViewModel.chapterIsBuyedStatus.value ?: emptyMap()
        val unlockMap = woChpterViewModel.chapterTemporaryUnlockStatus.value ?: emptyMap()
        chapter.id?.let { id ->
            buyMap[id]?.let { chapter.isBuyed = it }
            unlockMap[id]?.let {
                val isUnlocked = it == "true"
                chapter.temporaryUnlock = isUnlocked
                if (id == chapterId) isTemporarilyUnlocked = isUnlocked
            }
        }
    }

    fun initializeWithChapter(chapter: WoChapter) {
        if (currentChapter?.id == chapter.id) {
             setupUI(chapter)
             return
        }

        currentChapter = chapter
        chapterId = chapter.id
        isTemporarilyUnlocked = chapter.temporaryUnlock
        lastReportedProgress = chapter.readingStatus?.toIntOrNull() ?: 0
        isInitialSeekDone = false

        // CRITICAL: Update GlobalAppData.selectedChapter for the Unlock Sheet
        GlobalAppData.selectedChapter.value = chapter

        if (chaptersList.none { it.id == chapter.id }) {
            chaptersList.add(chapter)
            chaptersList.sortBy { it.sqc?.toIntOrNull() ?: 0 }
        }

        setupUI(chapter)
        checkAndHandlePaywall()
        showLoading(false)
        proactivelyFetchNeighbors()
    }
    
    private fun proactivelyFetchNeighbors() {
        val current = currentChapter ?: return
        val nextId = current.nextChapterId
        if (!nextId.isNullOrEmpty()) {
            if (chaptersList.none { it.id == nextId }) {
                fetchAndAddChapterToList(nextId)
            } else {
                checkAndQueueNextChapter()
            }
        }

        val prevId = current.previousChapterId
        if (!prevId.isNullOrEmpty() && chaptersList.none { it.id == prevId }) {
             fetchAndAddChapterToList(prevId)
        }
    }

    private fun fetchAndAddChapterToList(id: String) {
        val idInt = id.toIntOrNull() ?: return
        lifecycleScope.launch {
            try {
                val chapter = woChpterViewModel.getChapter(idInt)
                if (chapter != null) {
                    applySessionStatusToChapter(chapter)
                    if (chaptersList.none { it.id == chapter.id }) {
                        chaptersList.add(chapter)
                        chaptersList.sortBy { it.sqc?.toIntOrNull() ?: 0 }
                        setupUI(currentChapter ?: chapter)
                        checkAndQueueNextChapter()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pre-fetching chapter $id", e)
            }
        }
    }

    private fun setupObservers() {
        adsViewModel.adDismissed.observe(this) { dismissed ->
            if (dismissed == true) completePendingInterstitialAction()
        }
        adsViewModel.adLoadFailed.observe(this) { failed ->
            if (failed == true) completePendingInterstitialAction()
        }

        woChpterViewModel.chapterBuyStatusEvent.observe(this) { pair ->
            val (unlockedChapterId, isBought) = pair
            if (isBought && unlockedChapterId == currentChapter?.id) {
                currentChapter?.isBuyed = "1"
                checkAndHandlePaywall()
                startPlaybackIfReady()
            }
        }

        woChpterViewModel.chapterTemporaryUnlock.observe(this) { pair ->
            val (unlockedChapterId, isUnlocked) = pair
            if (isUnlocked && unlockedChapterId == currentChapter?.id) {
                isTemporarilyUnlocked = true
                currentChapter?.temporaryUnlock = true
                checkAndHandlePaywall()
                startPlaybackIfReady()
            }
        }

        woChpterViewModel.purchaseStatus.observe(this) { status ->
             if (status == WoChpterViewModel.PurchaseStatus.SUCCESS) {
                 woChpterViewModel.clearPurchaseStatus()
             } else if (status == WoChpterViewModel.PurchaseStatus.ERROR) {
                 Toast.makeText(this, "Not Enough Coins", Toast.LENGTH_SHORT).show()
                 showUnlockBottomSheet()
                 woChpterViewModel.clearPurchaseStatus()
             }
        }

        woChpterViewModel._chapterModelsList.observe(this) { pageList ->
            if (!pageList.isNullOrEmpty()) {
                val allChapters = pageList.flatMap { it.chapters }
                syncChaptersWithSessionStatus(allChapters)

                allChapters.forEach { chapter ->
                    if (chaptersList.none { it.id == chapter.id }) {
                        chaptersList.add(chapter)
                    }
                }
                chaptersList.sortBy { it.sqc?.toIntOrNull() ?: 0 }

                if (currentChapter == null && chapterId != null) {
                    chaptersList.find { it.id == chapterId }?.let { initializeWithChapter(it) }
                }
                setupUI(currentChapter ?: return@observe)
                checkAndQueueNextChapter()
            }
            if (currentChapter != null || !pageList.isNullOrEmpty()) showLoading(false)
        }
        
        woChpterViewModel.chapterIsBuyedStatus.observe(this) { statusMap ->
            chaptersList.forEach { chapter ->
                val id = chapter.id ?: return@forEach
                statusMap[id]?.let { chapter.isBuyed = it }
            }
            currentChapter?.id?.let { id -> statusMap[id]?.let { currentChapter?.isBuyed = it } }
        }

        woChpterViewModel.chapterTemporaryUnlockStatus.observe(this) { statusMap ->
            chaptersList.forEach { chapter ->
                val id = chapter.id ?: return@forEach
                statusMap[id]?.let { chapter.temporaryUnlock = it == "true" }
            }
            currentChapter?.id?.let { id ->
                statusMap[id]?.let {
                    currentChapter?.temporaryUnlock = it == "true"
                    if (id == chapterId) isTemporarilyUnlocked = (it == "true")
                }
            }
        }

        GlobalAppData.selectedNovel.observe(this) { novel ->
            if (novel != null) {
                binding.tvNovelTitle.text = DisplayText.of(novel.title)
                loadCover(novel.imageLink)
                bindGenreTags(novel)
            }
        }
    }

    /** Category + sub-category tags under "View Full Screen"; the sub-category is skipped if it repeats. */
    private fun bindGenreTags(novel: Novel) {
        val category = DisplayText.of(novel.categoryName).takeIf { it.isNotBlank() }
        val subCategory = DisplayText.of(novel.subCategoryName)
            .takeIf { it.isNotBlank() && !it.equals(category, ignoreCase = true) }
        binding.tvCategory.text = category.orEmpty()
        binding.tvCategory.visibility = if (category != null) View.VISIBLE else View.GONE
        binding.tvSubCategory.text = subCategory.orEmpty()
        binding.tvSubCategory.visibility = if (subCategory != null) View.VISIBLE else View.GONE
        binding.tagsRow.visibility = if (category != null || subCategory != null) View.VISIBLE else View.GONE
    }

    /** Cover decoded at its on-screen 104x156dp as a hardware bitmap; skips reloading the same URL. */
    private var loadedCoverUrl: String? = null

    private fun loadCover(url: String?) {
        if (url == loadedCoverUrl) return
        loadedCoverUrl = url
        val density = resources.displayMetrics.density
        ImageHelper.loadImage(
            this, url, binding.ivCover, allowHardware = true,
            targetWidthPx = (104 * density).toInt(), targetHeightPx = (156 * density).toInt()
        )
    }

    /** Keeps the round play button in place and swaps its icon for a spinner while buffering. */
    private fun showBuffering(buffering: Boolean) {
        binding.progressBarLoading.visibility = if (buffering) View.VISIBLE else View.GONE
        binding.btnPlayPause.imageAlpha = if (buffering) 0 else 255
    }

    private fun syncChaptersWithSessionStatus(chapters: List<WoChapter>) {
        val buyMap = woChpterViewModel.chapterIsBuyedStatus.value ?: emptyMap()
        val unlockMap = woChpterViewModel.chapterTemporaryUnlockStatus.value ?: emptyMap()
        chapters.forEach { chapter ->
            val id = chapter.id ?: return@forEach
            buyMap[id]?.let { chapter.isBuyed = it }
            unlockMap[id]?.let { chapter.temporaryUnlock = it == "true" }
        }
    }
    
    private fun checkAndHandlePaywall() {
        val chapter = currentChapter ?: return
        if (canChapterBePlayed(chapter)) {
            unlockBottomSheet?.dismiss()
            unlockBottomSheet = null
            startPlaybackIfReady()
        } else {
            if (autoUnlock) attemptAutoUnlock() else {
                showUnlockBottomSheet()
                controller?.pause()
            }
        }
    }
    
    private fun attemptAutoUnlock() {
        if (!autoUnlock) { showUnlockBottomSheet(); return }
        val novel = GlobalAppData.selectedNovel.value
        val userDetails = GlobalAppData.userDetailsLiveData.value
        if (novel != null && userDetails != null) {
            val coinPrice = GlobalVariableManager.getChapterCoinPrice(novel)
            val userPoints = userDetails.userData?.points?.toIntOrNull() ?: 0
            if (userPoints >= coinPrice && coinPrice > 0) {
                 woChpterViewModel.buyChapterWithCoins(coinPrice)
                 Toast.makeText(this, "Auto-unlocking with points...", Toast.LENGTH_SHORT).show()
                 return
            }

            val diamondPrice = GlobalVariableManager.getChapterDiamondPrice(novel)
            val userDiamonds = userDetails.userData?.diamond?.toIntOrNull() ?: 0
            if (userDiamonds >= diamondPrice && diamondPrice > 0) {
                 woChpterViewModel.buyChapterWithDiamondsv2(diamondPrice)
                 Toast.makeText(this, "Auto-unlocking with diamonds...", Toast.LENGTH_SHORT).show()
                 return
            }
        }
        showUnlockBottomSheet()
    }

    private fun canChapterBePlayed(chapter: WoChapter): Boolean {
        if (isTemporarilyUnlocked || chapter.temporaryUnlock) return true
        val (_, userId) = tokenManager.getTokenAndUserId()
        val freeChaptersCount = GlobalVariableManager.getFreeChapterCount()
        val chapterSqc = chapter.sqc?.toIntOrNull() ?: 0
        return chapterSqc <= freeChaptersCount || chapter.isBuyed == "1" || chapter.userId == userId || GlobalVariableManager.hasReadingSubscriptionPack()
    }
    
    private fun showUnlockBottomSheet() {
        if (unlockBottomSheet?.isAdded == true) return

        // Ensure selected chapter is set for the sheet
        currentChapter?.let { GlobalAppData.selectedChapter.value = it }

        unlockBottomSheet = UnlockChapterBottomSheetNew.newInstance(null)
        unlockBottomSheet?.show(supportFragmentManager, "UnlockChapter")
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, AudioPlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({ setupController() }, MoreExecutors.directExecutor())
    }

    private fun setupController() {
        val controller = this.controller ?: return
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { updatePlayPauseIcon(isPlaying) }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { id ->
                    if (id != currentChapter?.id) {
                        chaptersList.find { it.id == id }?.let { initializeWithChapter(it) }
                    }
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> showBuffering(true)
                    Player.STATE_READY -> {
                        showBuffering(false)
                        binding.seekBar.max = controller.duration.toInt()
                        binding.tvTotalTime.text = formatTime(controller.duration)

                        if (!isInitialSeekDone && lastReportedProgress > 0 && controller.duration > 0) {
                            val seekPos = (lastReportedProgress.toFloat() / 100 * controller.duration).toLong()
                            controller.seekTo(seekPos)
                            isInitialSeekDone = true
                        }

                        checkAndQueueNextChapter()
                    }
                    Player.STATE_ENDED -> {
                        if (autoPlay) playNextChapter() else updatePlayPauseIcon(false)
                    }
                    else -> showBuffering(false)
                }
            }
        })
        updatePlayPauseIcon(controller.isPlaying)
        startPlaybackIfReady()
    }
    
    private fun startPlaybackIfReady() {
        val chapter = currentChapter ?: return
        if (!canChapterBePlayed(chapter)) return
        val controller = this.controller
        when {
            controller == null || controller.currentMediaItem?.mediaId != chapter.id -> startPlayback(chapter)
            // Same chapter but stopped (mini player closed): it must be re-prepared to play.
            controller.playbackState == Player.STATE_IDLE -> { controller.prepare(); controller.play(); startProgressUpdates() }
            else -> startProgressUpdates()
        }
    }
    
    private fun startPlayback(chapter: WoChapter) {
        val audioUrl = chapter.audioFilePath ?: return
        val controller = this.controller
        if (controller != null) {
            val mediaItem = createMediaItem(chapter)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            lastQueuedChapterId = null
        } else {
            val serviceIntent = Intent(this, AudioPlayerService::class.java).apply {
                action = "PLAY"
                putExtra("audio_url", audioUrl)
                putExtra("title", decodeHtmlToTextV2(chapter.chapterName))
                putExtra("artist", decodeHtmlToTextV2(GlobalAppData.selectedNovel.value?.title ?: ""))
                putExtra("chapter_id", chapter.id)
                putExtra("image_url", GlobalAppData.selectedNovel.value?.imageLink)
            }
            startService(serviceIntent)
        }
        GlobalAppData.currentlyPlayingChapter.postValue(chapter)
        startProgressUpdates()
    }

    private fun createMediaItem(chapter: WoChapter): MediaItem {
        val novel = GlobalAppData.selectedNovel.value
        val metadata = MediaMetadata.Builder()
            .setTitle(decodeHtmlToTextV2(chapter.chapterName))
            .setArtist(decodeHtmlToTextV2(novel?.title ?: ""))
            .setArtworkUri(novel?.imageLink?.let { Uri.parse(it) })
            .build()
        return MediaItem.Builder()
            .setMediaId(chapter.id ?: "")
            .setUri(chapter.audioFilePath)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun checkAndQueueNextChapter() {
        if (!autoPlay) return
        if (shouldShowMidrollAds()) return
        val controller = this.controller ?: return
        val current = currentChapter ?: return
        val nextId = current.nextChapterId

        if (!nextId.isNullOrEmpty() && nextId != lastQueuedChapterId) {
            val nextChapter = chaptersList.find { it.id == nextId }
            if (nextChapter != null && canChapterBePlayed(nextChapter)) {
                if (controller.mediaItemCount == 1) {
                    controller.addMediaItem(createMediaItem(nextChapter))
                    lastQueuedChapterId = nextId
                    Log.d(TAG, "Queued next chapter: ${nextChapter.chapterName}")
                }
            }
        }
    }

    private fun setupUI(chapter: WoChapter) {
        binding.tvChapterTitle.text = DisplayText.of(chapter.chapterName)
        val novel = GlobalAppData.selectedNovel.value
        if (novel != null) {
            binding.tvNovelTitle.text = DisplayText.of(novel.title)
            loadCover(novel.imageLink)
            bindGenreTags(novel)
        }

        val index = chaptersList.indexOfFirst { it.id == chapter.id }
        val hasNext = (index != -1 && index < chaptersList.size - 1) || !chapter.nextChapterId.isNullOrEmpty()
        val hasPrev = (index > 0) || !chapter.previousChapterId.isNullOrEmpty()

        binding.btnNext.visibility = if (hasNext) View.VISIBLE else View.INVISIBLE
        binding.btnPrevious.visibility = if (hasPrev) View.VISIBLE else View.INVISIBLE
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        val openNovelDetails = View.OnClickListener {
            val targetNovelId = novelId?.takeIf { it.isNotBlank() }
                ?: GlobalAppData.selectedNovel.value?.id?.takeIf { it.isNotBlank() }
                ?: return@OnClickListener
            NovelDetailActivity.start(this, targetNovelId)
        }
        binding.cardCover.setOnClickListener(openNovelDetails)
        binding.tvNovelTitle.setOnClickListener(openNovelDetails)
        binding.tvViewFullScreen.setOnClickListener {
            AudioPlayerActivity.start(this, chapterId ?: "", novelId ?: "")
        }
        binding.btnMore.setOnClickListener { showMoreOptionsBottomSheet() }
        binding.btnShare.setOnClickListener { shareChapter() }
        binding.btnPlayPause.setOnClickListener {
            val controller = this.controller ?: return@setOnClickListener
            if (currentChapter != null && !canChapterBePlayed(currentChapter!!)) { showUnlockBottomSheet(); return@setOnClickListener }
            if (controller.isPlaying) controller.pause() else controller.play()
        }
        binding.btnForward.setOnClickListener { seekBy(10000) }
        binding.btnRewind.setOnClickListener { seekBy(-10000) }
        binding.btnNext.setOnClickListener { playNextChapter() }
        binding.btnPrevious.setOnClickListener { playPreviousChapter() }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) controller?.seekTo(progress.toLong()) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.btnGift.setOnClickListener {
            showGiveGiftDialog()
        }
    }
    
    private fun showGiveGiftDialog() {
        val novel = GlobalAppData.selectedNovel.value ?: return
        val authorId = novel.userId ?: return
        val dialog = BottomSheetDialog(this)
        val dialogBinding = LayoutGiveGiftDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val adapter = GiftAdapter(emptyList(), GiftAdapter.GiftMode.OWNED) { gift ->
            lifecycleScope.launch {
                dialogBinding.layoutLoading.visibility = View.VISIBLE

                val success = giftViewModel.sendGift(
                    gift = gift,
                    recipientId = authorId,
                    type = "send_to_novel",
                    resourceId ="0",
                    novelId = novel.id
                )

                dialogBinding.layoutLoading.visibility = View.GONE

                if (success) {
                    dialogBinding.layoutSuccess.visibility = View.VISIBLE
                    dialogBinding.lottieSuccess.playAnimation()
                    delay(2000)
                    dialog.dismiss()
                } else {
                    Toast.makeText(this@PlayChapterActivity, "Failed to send gift", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialogBinding.rvGifts.layoutManager = GridLayoutManager(this, 3)
        dialogBinding.rvGifts.adapter = adapter

        dialogBinding.btnBuyGift.setOnClickListener {
            dialog.dismiss()
            // Assuming RewardsActivity exists in this scope
            startActivity(Intent(this, RewardsActivity::class.java))
        }

        giftViewModel.userGifts.observe(this) { ownedGifts ->
            if (ownedGifts.isNullOrEmpty()) {
                dialogBinding.rvGifts.visibility = View.GONE
                dialogBinding.layoutEmpty.visibility = View.VISIBLE
                dialogBinding.btnGoToShop.setOnClickListener {
                    dialog.dismiss()
                    startActivity(Intent(this, RewardsActivity::class.java))
                }
            } else {
                dialogBinding.rvGifts.visibility = View.VISIBLE
                dialogBinding.layoutEmpty.visibility = View.GONE
                adapter.updateData(ownedGifts)
            }
        }

        dialog.show()
        giftViewModel.fetchUserGifts(50, 0, true)
    }

    private fun playNextChapter() {
        maybeShowInterstitialThen { playNextChapterNow() }
    }

    private fun playNextChapterNow() {
        if (isFetchingNext) return
        val current = currentChapter ?: return
        val index = chaptersList.indexOfFirst { it.id == current.id }
        if (index != -1 && index < chaptersList.size - 1) initializeWithChapter(chaptersList[index + 1])
        else {
            val nextId = current.nextChapterId
            if (!nextId.isNullOrEmpty()) fetchAndPlayChapter(nextId, isNext = true)
            else Toast.makeText(this, "No more chapters", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shouldShowMidrollAds(): Boolean =
        GlobalVariableManager.isAdsEnabled() && !GlobalVariableManager.hasReadingSubscriptionPack()

    private fun maybeShowInterstitialThen(action: () -> Unit) {
        if (!shouldShowMidrollAds()) {
            action()
            return
        }
        val preferences = getSharedPreferences(PLAYER_PREFS, Context.MODE_PRIVATE)
        val transitionCount = preferences.getInt(PREF_MIDROLL_COUNT, 0) + 1
        if (transitionCount < MIDROLL_INTERVAL) {
            preferences.edit().putInt(PREF_MIDROLL_COUNT, transitionCount).apply()
            action()
            return
        }
        preferences.edit().putInt(PREF_MIDROLL_COUNT, 0).apply()
        pendingInterstitialAction = action
        adsViewModel.resetValues()
        handler.removeCallbacks(interstitialFallback)
        handler.postDelayed(interstitialFallback, 7000L)
        adsViewModel.showInterstitialAd(this)
    }

    private fun completePendingInterstitialAction() {
        val action = pendingInterstitialAction ?: return
        pendingInterstitialAction = null
        handler.removeCallbacks(interstitialFallback)
        adsViewModel.resetValues()
        action()
    }
    
    private fun playPreviousChapter() {
        if (isFetchingPrev) return
        val current = currentChapter ?: return
        val index = chaptersList.indexOfFirst { it.id == current.id }
        if (index > 0) initializeWithChapter(chaptersList[index - 1])
        else {
            val prevId = current.previousChapterId
            if (!prevId.isNullOrEmpty()) fetchAndPlayChapter(prevId, isNext = false)
            else Toast.makeText(this, "First chapter reached", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchAndPlayChapter(id: String, isNext: Boolean) {
        val idInt = id.toIntOrNull() ?: return
        if (isNext) { isFetchingNext = true; binding.btnNext.visibility = View.INVISIBLE; binding.progressNext.visibility = View.VISIBLE }
        else { isFetchingPrev = true; binding.btnPrevious.visibility = View.INVISIBLE; binding.progressPrevious.visibility = View.VISIBLE }

        lifecycleScope.launch {
            try {
                val chapter = woChpterViewModel.getChapter(idInt)
                if (chapter != null) { applySessionStatusToChapter(chapter); initializeWithChapter(chapter) }
                else Toast.makeText(this@PlayChapterActivity, "Failed to load chapter", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Log.e(TAG, "Error fetching chapter $id", e); Toast.makeText(this@PlayChapterActivity, "Error loading chapter", Toast.LENGTH_SHORT).show() }
            finally {
                if (isNext) { isFetchingNext = false; binding.btnNext.visibility = View.VISIBLE; binding.progressNext.visibility = View.GONE }
                else { isFetchingPrev = false; binding.btnPrevious.visibility = View.VISIBLE; binding.progressPrevious.visibility = View.GONE }
                setupUI(currentChapter ?: return@launch)
            }
        }
    }

    private fun updateProgress() {
        val controller = this.controller ?: return
        binding.seekBar.progress = controller.currentPosition.toInt()
        binding.tvCurrentTime.text = formatTime(controller.currentPosition)

        if (controller.duration > 0) {
            val progress = (controller.currentPosition.toFloat() / controller.duration * 100).toInt()
            if (progress >= lastReportedProgress + 10 || progress >= 100) {
                updateReadingStatus(progress)
            }
        }

        if (controller.duration > 0 && controller.duration - controller.currentPosition < 15000) checkAndQueueNextChapter()
    }

    private fun updateReadingStatus(progress: Int) {
        val chapter = currentChapter ?: return
        val finalProgress = progress.coerceAtMost(100)
        if (finalProgress > lastReportedProgress) {
            lastReportedProgress = finalProgress
            chapter.id?.let { id ->
                chapterUpdateViewModel.updateReadingStatus(id, finalProgress)
            }
        }
    }

    private fun seekBy(deltaMs: Long) {
        val controller = this.controller ?: return
        val duration = controller.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        controller.seekTo((controller.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    private fun startProgressUpdates() {
        handler.removeCallbacks(updateProgressRunnable)
        handler.post(updateProgressRunnable)
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play)
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    private fun showMoreOptionsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = LayoutPlayChapterMoreBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        val options = listOf(
            MoreOption("Comment", com.engine.core.R.drawable.ic_comment) { showCommentDialog() },
            MoreOption("Rate", R.drawable.ic_star_rate_raw) { showRateDialog() },
            MoreOption("Speed", R.drawable.ic_speed) { showSpeedDialog() },
            MoreOption(
                "Sleep Timer",
                R.drawable.ic_timer
            ) { showSleepTimerDialog() },
            MoreOption("Share", com.engine.core.R.drawable.ic_share) { shareChapter() },
            MoreOption("Report", com.engine.core.R.drawable.ic_report) { showReportDialog() },
            MoreOption(
                "Auto Unlock",
                R.drawable.ic_lock_open,
                isToggle = true,
                isChecked = autoUnlock
            ) { isChecked ->
                autoUnlock = isChecked
                getSharedPreferences(PLAYER_PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_AUTO_UNLOCK, isChecked).apply()
                Toast.makeText(this, "Auto unlock ${if(isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            },
            MoreOption(
                "Auto Play",
                com.engine.core.R.drawable.ic_play,
                isToggle = true,
                isChecked = autoPlay
            ) { isChecked ->
                autoPlay = isChecked
                getSharedPreferences(PLAYER_PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_AUTO_PLAY, isChecked).apply()
                if (isChecked) checkAndQueueNextChapter()
                Toast.makeText(this, "Auto play ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            }
        )
        dialogBinding.rvMoreOptions.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvMoreOptions.adapter = MoreOptionsAdapter(options)
        dialog.show()
    }
    
    data class MoreOption(val title: String, val iconRes: Int, val isToggle: Boolean = false, var isChecked: Boolean = false, val onClick: ((Boolean) -> Unit)? = null)
    
    inner class MoreOptionsAdapter(private val options: List<MoreOption>) : RecyclerView.Adapter<MoreOptionsAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemMoreOptionBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(ItemMoreOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val option = options[position]
            holder.binding.tvOptionTitle.text = option.title
            holder.binding.ivOptionIcon.setImageResource(option.iconRes)
            if (option.isToggle) {
                holder.binding.switchOption.visibility = View.VISIBLE
                holder.binding.switchOption.isChecked = option.isChecked
                holder.binding.root.setOnClickListener { holder.binding.switchOption.toggle(); option.isChecked = holder.binding.switchOption.isChecked; option.onClick?.invoke(option.isChecked) }
                holder.binding.switchOption.setOnCheckedChangeListener { _, isChecked -> option.isChecked = isChecked; option.onClick?.invoke(isChecked) }
            } else { holder.binding.switchOption.visibility = View.GONE; holder.binding.root.setOnClickListener { option.onClick?.invoke(false) } }
        }
        override fun getItemCount() = options.size
    }
    
    private fun showCommentDialog() { binding.viewPager.currentItem = 2 }
    private fun showRateDialog() {
        val chapter = currentChapter ?: return
        val dialog = BottomSheetDialog(this); dialog.setContentView(R.layout.dialog_rate_comment)
        val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar); val etComment = dialog.findViewById<EditText>(R.id.etComment); val btnSubmit = dialog.findViewById<Button>(R.id.btnSubmit)
        btnSubmit?.setOnClickListener {
            val rating = ratingBar?.rating?.toInt() ?: 0; val comment = etComment?.text?.toString()?.trim() ?: ""
            if (rating > 0 || comment.isNotEmpty()) { chapter.id?.let { id -> if (rating > 0) submitRating(id, rating); if (comment.isNotEmpty()) submitComment(id, comment) }; dialog.dismiss() }
        }
        dialog.show()
    }
    private fun submitRating(chapterId: String, rating: Int) { lifecycleScope.launch { woChpterViewModel.updateChapterDetails(wordCount = null, chapterId = chapterId, type = "rating", value = rating.toString(), paragraphId = null, id = null); Toast.makeText(this@PlayChapterActivity, "Rating submitted", Toast.LENGTH_SHORT).show() } }
    private fun submitComment(chapterId: String, comment: String) { lifecycleScope.launch { woChpterViewModel.addnewChapterComment(chapterId = chapterId, type = "comment", value = comment, paragraphId = null); Toast.makeText(this@PlayChapterActivity, "Comment submitted", Toast.LENGTH_SHORT).show() } }
    private fun showSpeedDialog() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f); val titles = listOf("0.5x", "0.75x", "Normal", "1.25x", "1.5x", "2.0x")
        val builder = AlertDialog.Builder(this); builder.setTitle("Playback Speed"); builder.setItems(titles.toTypedArray()) { _, which -> controller?.setPlaybackSpeed(speeds[which]); Toast.makeText(this, "Speed set to ${titles[which]}", Toast.LENGTH_SHORT).show() }; builder.show()
    }
    private fun showSleepTimerDialog() {
         val dialog = BottomSheetDialog(this); val binding = LayoutSleepTimerDialogBinding.inflate(layoutInflater); dialog.setContentView(binding.root)
         val times = listOf(15, 30, 45, 60, 90, 0); val adapter = TimerOptionsAdapter(times) { minutes -> setSleepTimer(minutes); dialog.dismiss() }; binding.rvTimerOptions.adapter = adapter; dialog.show()
    }
    inner class TimerOptionsAdapter(val times: List<Int>, val onClick: (Int) -> Unit) : RecyclerView.Adapter<TimerOptionsAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemTimerOptionBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(ItemTimerOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) { val min = times[position]; holder.binding.tvTimerOption.text = if (min == 0) "Turn off Timer" else "$min minutes"; holder.binding.root.setOnClickListener { onClick(min) } }
        override fun getItemCount() = times.size
    }
    private fun setSleepTimer(minutes: Int) {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        if (minutes > 0) {
            sleepTimerRunnable = Runnable { controller?.pause(); Toast.makeText(this, "Sleep timer: Paused playback", Toast.LENGTH_SHORT).show() }
            sleepTimerHandler.postDelayed(sleepTimerRunnable!!, minutes * 60 * 1000L); Toast.makeText(this, "Sleep timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
    }
    private fun shareChapter() {
        val chapter = currentChapter ?: return
        val chapterId = chapter.id?.takeIf { it.isNotBlank() } ?: return
        // Episode link, so it opens the audio player (not the reading chapter) on the other end.
        val sharedNovelId = novelId?.takeIf { it.isNotBlank() }
            ?: chapter.novelId?.takeIf { it.isNotBlank() } ?: GlobalAppData.selectedNovel.value?.id
        val shareUrl = DeepLinkRouter.audioEpisodeLink(sharedNovelId, chapterId)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, decodeHtmlToTextV2(chapter.chapterName))
            putExtra(
                Intent.EXTRA_TEXT,
                "Listen to this chapter: ${decodeHtmlToTextV2(chapter.chapterName)} in " +
                    "${decodeHtmlToTextV2(GlobalAppData.selectedNovel.value?.title ?: "")}\n\n$shareUrl"
            )
        }
        startActivity(Intent.createChooser(shareIntent, "Share Chapter"))
    }
    private fun showReportDialog() {
        val novel = GlobalAppData.selectedNovel.value ?: return; val dialog = AlertDialog.Builder(this); dialog.setTitle("Report Novel"); val input = EditText(this); input.hint = "Reason for reporting..."; dialog.setView(input)
        dialog.setPositiveButton("Report") { _, _ -> val reason = input.text.toString(); if (reason.isNotEmpty()) { val userId = tokenManager.getCurrentUserId(); if (userId != null && novel.id != null) { lifecycleScope.launch { novelViewModel.insertNovelReport(novel.id!!.toInt(), userId.toInt(), "Report", reason); Toast.makeText(this@PlayChapterActivity, "Report submitted", Toast.LENGTH_SHORT).show() } } } }
        dialog.setNegativeButton("Cancel", null).show()
    }

    private fun showChaptersBottomSheet() { binding.viewPager.currentItem = 1 }

    inner class PlayChapterPagerAdapter(activity: PlayChapterActivity, private val novelId: String) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): Fragment {
            val bundle = Bundle().apply { putString("novelId", novelId) }
            return when (position) {
                0 -> DetailsFragment().apply { arguments = bundle }
                1 -> ChaptersFragment().apply { arguments = bundle }
                2 -> CommentsFragment().apply { arguments = bundle }
                else -> throw IllegalStateException("Unexpected position $position")
            }
        }
    }

    override fun onStop() { controllerFuture?.let { MediaController.releaseFuture(it) }; handler.removeCallbacks(updateProgressRunnable); super.onStop() }
}
