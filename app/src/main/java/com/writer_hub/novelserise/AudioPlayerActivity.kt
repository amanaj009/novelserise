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
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.AppCore.tokenManager
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.models.ChapterModel
import com.engine.core.models.Novel
import com.engine.core.models.WoChapter
import com.engine.core.service.AudioPlayerService
import com.engine.core.viewmodel.AdsViewModel
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel
import com.engine.core.viewmodel.SplashViewModel
import com.engine.core.viewmodel.WoChapterUpdateViewModel
import com.engine.core.viewmodel.WoChpterViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.writer_hub.novelserise.adapter.ChapterAdapter
import com.writer_hub.novelserise.databinding.ActivityAudioPlayerBinding
import com.writer_hub.novelserise.databinding.BottomSheetChaptersBinding
import com.writer_hub.novelserise.databinding.ItemTimerOptionBinding
import com.writer_hub.novelserise.databinding.LayoutSleepTimerDialogBinding
import com.writer_hub.novelserise.sheet.UnlockChapterBottomSheetNew
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AudioPlayerActivity : BaseActivity() {

    private lateinit var binding: ActivityAudioPlayerBinding
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
    private lateinit var chapterUpdateViewModel: WoChapterUpdateViewModel
    private lateinit var adsViewModel: AdsViewModel
    
    private var autoUnlock: Boolean = false
    private var lastQueuedChapterId: String? = null
    private var lastReportedProgress: Int = 0

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }
    
    private var sleepTimerRunnable: Runnable? = null
    private var sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerEndAt: Long = 0L
    private var isUserSeeking = false
    private var loadedArtUrl: String? = null
    private var pendingInterstitialAction: (() -> Unit)? = null
    private val interstitialFallback = Runnable { completePendingInterstitialAction() }

    companion object {
        private const val EXTRA_CHAPTER_ID = "extra_chapter_id"
        private const val EXTRA_NOVEL_ID = "extra_novel_id"
        private const val TAG = "AudioPlayerActivity"
        private const val PREFS_PLAYER = "audio_player_prefs"
        private const val KEY_AUTO_UNLOCK = "auto_unlock"
        private const val KEY_MIDROLL_COUNT = "midroll_transition_count"
        private const val MIDROLL_INTERVAL = 3

        fun start(context: Context, chapterId: String, novelId: String) {
            val intent = Intent(context, AudioPlayerActivity::class.java).apply {
                putExtra(EXTRA_CHAPTER_ID, chapterId); putExtra(EXTRA_NOVEL_ID, novelId)
            }
            context.startActivity(intent)
        }
    }

    // BaseActivity swaps in the user's theme style; keep the window translucent on top of it.
    override fun setTheme(resid: Int) {
        super.setTheme(resid)
        theme.applyStyle(R.style.ThemeOverlay_NovelSerise_PlayerSheet, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureScreen()
        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupSheet(animateIn = savedInstanceState == null)

        autoUnlock = getSharedPreferences(PREFS_PLAYER, MODE_PRIVATE).getBoolean(KEY_AUTO_UNLOCK, false)
        updateAutoUnlockUI(); updateSleepTimerLabel()

        novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)
        splashViewModel = AppProvider.getViewModel(application, SplashViewModel::class.java)
        chapterUpdateViewModel = AppProvider.getViewModel(application, WoChapterUpdateViewModel::class.java)
        adsViewModel = AppProvider.getViewModel(application, AdsViewModel::class.java)

        chapterId = intent.getStringExtra(EXTRA_CHAPTER_ID)
        novelId = intent.getStringExtra(EXTRA_NOVEL_ID)

        AppLoggerHelper.d(TAG, "onCreate: chapterId=$chapterId, novelId=$novelId")

        if (chapterId == null || novelId == null) {
            AppLoggerHelper.e(TAG, "Missing data")
            finish(); return
        }

        woChpterViewModel = AppProvider.getUniqueViewModel(application, WoChpterViewModel::class.java, novelId!!)
        woChpterViewModel.setSelectedNovelId(novelId!!)

        setupObservers(); setupListeners(); loadNovelDetails(); loadChapters(chapterId!!)
    }

    private fun loadChapters(readingId: String) {
        lifecycleScope.launch {
            try {
                val currentIdInt = readingId.toIntOrNull()
                if (currentIdInt != null) {
                    val chapter = woChpterViewModel.getChapter(currentIdInt)
                    if (chapter != null) { applySessionStatusToChapter(chapter); initializeWithChapter(chapter) }
                }
                woChpterViewModel.fetchChapters(order = woChpterViewModel.cachedOrder, offset = 1, status = "published", pagination = 1, scrolling = false, extra = true, currentReading = "0", fromPage = "audio_player")
            } catch (e: Exception) { AppLoggerHelper.e(TAG, "Error loading chapters", e) }
        }
    }

    private fun loadNovelDetails() {
        val id = novelId ?: return
        lifecycleScope.launch {
            val novel = novelViewModel.fetchSingleNovelById(id, "") ?: novelViewModel.fetchSingleNovelById(id, "detail")
            novel?.let { displayNovel(it) }
        }
    }

    private fun displayNovel(novel: Novel) {
        val imageUrl = novel.imageLink?.takeIf { it.isNotBlank() } ?: novel.imageLinkCover?.takeIf { it.isNotBlank() } ?: novel.bannerImage
        AppLoggerHelper.d(TAG, "displayNovel: ${novel.title}, imageUrl=$imageUrl")
        
        GlobalAppData.selectedNovel.value = novel
        binding.tvNovelTitle.text = decodeHtmlToTextV2(novel.title)

        bindCoverArt(imageUrl)

        checkAndShowAd()

        currentChapter?.let { splashViewModel.saveCurrentReading(novel, it) }
        refreshPlayerMetadata()
    }

    private fun bindCoverArt(imageUrl: String?) {
        if (imageUrl == loadedArtUrl) return
        loadedArtUrl = imageUrl
        ImageHelper.loadImage(this, imageUrl, binding.ivCover)
    }

    /**
     * The player is a full-screen sheet over a translucent window: it slides up on open and
     * slides away when dragged down, back is pressed or the collapse arrow is tapped.
     */
    private fun setupSheet(animateIn: Boolean) {
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        val surface = getColorFromAttr(com.google.android.material.R.attr.colorSurface)
        binding.sheet.setBackgroundColor(surface)
        // The visible sheet is the outline rect; it shrinks into the mini player bar while collapsing.
        binding.sheet.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) =
                outline.setRoundRect(0, sheetTop.toInt(), view.width, sheetBottom.toInt(), sheetCorner)
        }
        binding.sheet.clipToOutline = true

        // Own the insets instead of EdgeToEdgeHelper: it paints the decor view opaque, which would hide the screen behind.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // Translucent pixel format + clear background, otherwise areas the sheet uncovers keep stale frames.
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            val lightSurface = androidx.core.graphics.ColorUtils.calculateLuminance(surface) > 0.5
            isAppearanceLightStatusBars = lightSurface; isAppearanceLightNavigationBars = lightSurface
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.dragRoot) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            binding.sheet.setPadding(0, bars.top, 0, bars.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }

        binding.dragRoot.collapseDistance = { miniPlayerBottom()?.let { it - dp(MiniPlayerAnchor.HEIGHT_DP) } ?: binding.dragRoot.height.toFloat() }
        binding.dragRoot.onProgressChanged = { renderCollapse(it) }
        binding.sheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> binding.dragRoot.refresh() }
        binding.dragRoot.onDismissed = {
            // The real mini player is already drawn right under our collapsed copy. Blank this window
            // first and close it a frame later, so any system close animation runs on an empty window
            // and the hand-off shows no jump.
            binding.dragRoot.alpha = 0f
            binding.dragRoot.postOnAnimation {
                finish()
                @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = binding.dragRoot.dismiss()
        })

        if (animateIn) {
            binding.dragRoot.doOnLayoutCompat {
                binding.dragRoot.showCollapsed()
                binding.dragRoot.post { binding.dragRoot.animateIn() }
            }
        }
    }

    private var sheetTop = 0f
    private var sheetBottom = 0f
    private var sheetCorner = 0f
    private var coverLayerActive = false

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun sp(value: Float) = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

    private inline fun View.doOnLayoutCompat(crossinline action: () -> Unit) {
        if (isLaidOut && !isLayoutRequested) action()
        else addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                v.removeOnLayoutChangeListener(this); action()
            }
        })
    }

    /** Bottom edge (in this window) of the mini player we collapse into, or null to just slide away. */
    private fun miniPlayerBottom(): Float? {
        if (GlobalAppData.currentlyPlayingChapter.value == null) return null
        val anchor = MiniPlayerAnchor.bottomY ?: return null
        val location = IntArray(2)
        binding.dragRoot.getLocationOnScreen(location)
        return (anchor - location[1]).toFloat().takeIf { it > dp(MiniPlayerAnchor.HEIGHT_DP) }
    }

    /**
     * Draws the player at [p] (0 = open, 1 = collapsed). With a mini player on screen this runs in
     * two phases, like Pocket FM: first the sheet's top edge follows the finger and the cover shrinks
     * (toward the left) only as much as the remaining space needs, while titles and controls stay put;
     * once the cover is thumbnail-sized, everything settles into its mini-player spot. Without a mini
     * player the sheet simply slides down.
     */
    private fun renderCollapse(p: Float) {
        val root = binding.dragRoot
        val width = root.width.toFloat()
        val height = root.height.toFloat()
        if (width == 0f || binding.cardCover.height == 0) return
        // Scale the cover as a cached GPU texture while moving instead of redrawing the image every frame.
        val moving = p > 0f && p < 1f
        if (moving != coverLayerActive) {
            coverLayerActive = moving
            binding.cardCover.setLayerType(if (moving) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, null)
        }
        sheetCorner = dp(20f) * (p * 6f).coerceAtMost(1f) * ((1f - p) * 4f).coerceAtMost(1f)

        val miniBottom = miniPlayerBottom()
        if (miniBottom == null) {
            resetMorph()
            binding.sheet.translationY = p * height
            sheetTop = 0f; sheetBottom = height
            binding.sheet.invalidateOutline()
            return
        }
        binding.sheet.translationY = 0f
        val a = MiniPlayerAnchor
        val miniHeight = dp(a.HEIGHT_DP)
        val miniTop = miniBottom - miniHeight
        // Mini-player part bounds (relative to its top-left): measured by the host screen when available.
        fun part(measured: android.graphics.RectF?, left: Float, top: Float, w: Float, h: Float) =
            measured ?: android.graphics.RectF(left, top, left + w, top + h)
        val thumb = part(a.coverRect, dp(a.COVER_START_DP), (miniHeight - dp(a.COVER_HEIGHT_DP)) / 2f, dp(a.COVER_WIDTH_DP), dp(a.COVER_HEIGHT_DP))
        val playTarget = part(a.playRect, width - dp(a.PLAY_END_DP + a.PLAY_SIZE_DP), (miniHeight - dp(a.PLAY_SIZE_DP)) / 2f, dp(a.PLAY_SIZE_DP), dp(a.PLAY_SIZE_DP))
        val closeTarget = part(a.closeRect, width - dp(a.CLOSE_END_DP + a.CLOSE_SIZE_DP), (miniHeight - dp(a.CLOSE_SIZE_DP)) / 2f, dp(a.CLOSE_SIZE_DP), dp(a.CLOSE_SIZE_DP))
        val titleTarget = part(a.titleRect, dp(a.TEXT_START_DP), dp(a.TITLE_TOP_DP), 0f, 0f)
        val subtitleTarget = part(a.subtitleRect, dp(a.TEXT_START_DP), dp(a.SUBTITLE_TOP_DP), 0f, 0f)
        val titlePx = a.titleTextPx ?: sp(a.TITLE_TEXT_SP)
        val subtitlePx = a.subtitleTextPx ?: sp(a.SUBTITLE_TEXT_SP)

        val cover = binding.cardCover
        val coverRect = rectInRoot(cover)
        val thumbHeight = thumb.height()
        val thumbScale = thumb.width() / coverRect.width()
        val coverGap = dp(16f)
        val coverLimit = rectInRoot(binding.infoRow).top - dp(24f)

        // Phase 1 ends when the space left for the cover is down to thumbnail height.
        val phaseSplit = ((coverLimit - thumbHeight - coverGap) / miniTop).coerceIn(0.05f, 0.95f)
        val c = ((p - phaseSplit) / (1f - phaseSplit)).coerceIn(0f, 1f)
        sheetTop = lerp(0f, miniTop, p)
        sheetBottom = lerp(height, miniBottom, c)
        binding.sheet.invalidateOutline()

        // Top bar rides down with the sheet edge and fades out early.
        val headerAlpha = (1f - p / (phaseSplit * 0.5f)).coerceIn(0f, 1f)
        listOf(binding.btnBack, binding.btnMore).forEach { it.translationY = sheetTop; it.alpha = headerAlpha }

        val coverScale: Float
        val towardThumb: Float
        if (c == 0f) {
            val top = maxOf(coverRect.top, sheetTop + coverGap)
            coverScale = ((coverLimit - top) / coverRect.height()).coerceIn(thumbScale, 1f)
            towardThumb = ((1f - coverScale) / (1f - thumbScale)).coerceIn(0f, 1f)
            place(cover, coverRect, lerp(coverRect.left, thumb.left, towardThumb), top, coverScale)
        } else {
            coverScale = thumbScale
            towardThumb = 1f
            val top = lerp(coverLimit - thumbHeight, miniTop + thumb.top, c)
            place(cover, coverRect, thumb.left, top, thumbScale)
        }
        // Keep the on-screen corner radius heading to the mini thumbnail's instead of shrinking with the scale.
        cover.radius = lerp(dp(12f), dp(a.COVER_CORNER_DP), towardThumb) / coverScale

        // While settling, give the titles the mini player's width (at their final scale) so they
        // ellipsize at the same character and the hand-off shows the exact same text.
        matchTitleWidths(c > 0f, titleTarget.width() / (titlePx / binding.tvNovelTitle.textSize),
            subtitleTarget.width() / (subtitlePx / binding.tvChapterTitle.textSize))

        // Titles slide right quickly (clearing the thumbnail) while dropping into the mini player.
        morphText(binding.tvNovelTitle, titleTarget.left, miniTop + titleTarget.top, titlePx / binding.tvNovelTitle.textSize, c)
        morphText(binding.tvChapterTitle, subtitleTarget.left, miniTop + subtitleTarget.top, subtitlePx / binding.tvChapterTitle.textSize, c)

        val play = binding.playContainer
        val playRect = rectInRoot(play)
        place(play, playRect, lerp(playRect.left, playTarget.left, c), lerp(playRect.top, miniTop + playTarget.top, c),
            lerp(1f, playTarget.width() / play.width, c))

        // Seek bar track becomes the mini player's full-width progress line at its bottom edge.
        val seek = binding.seekBar
        val seekRect = rectInRoot(seek)
        val track = (seek.width - seek.paddingLeft - seek.paddingRight).coerceAtLeast(1).toFloat()
        seek.pivotX = width / 2f - seekRect.left
        seek.pivotY = seek.height / 2f
        seek.scaleX = lerp(1f, width / track, c)
        // 4dp track thins to the mini player's 2dp line.
        seek.scaleY = lerp(1f, a.PROGRESS_HEIGHT_DP / 4f, c)
        seek.translationY = lerp(0f, miniBottom - dp(a.PROGRESS_HEIGHT_DP) / 2f - (seekRect.top + seek.height / 2f), c)
        seek.thumb?.alpha = (255 * (1f - c * 3f)).toInt().coerceIn(0, 255)

        val controlsAlpha = (1f - c / 0.4f).coerceIn(0f, 1f)
        listOf(binding.btnShare, binding.actionsRow, binding.tvCurrentTime, binding.tvTotalTime,
            binding.btnPrevious, binding.btnRewind, binding.btnForward, binding.btnNext)
            .forEach { it.alpha = controlsAlpha }

        val close = binding.ivMiniClose
        close.translationX = closeTarget.left
        close.translationY = miniTop + closeTarget.top
        close.alpha = ((c - 0.6f) / 0.4f).coerceIn(0f, 1f)
    }

    private var titleWidthsMatched = false

    private fun matchTitleWidths(match: Boolean, titleWidth: Float, subtitleWidth: Float) {
        if (match == titleWidthsMatched || (match && (titleWidth <= 0f || subtitleWidth <= 0f))) return
        titleWidthsMatched = match
        for ((view, target) in listOf(binding.tvNovelTitle to titleWidth, binding.tvChapterTitle to subtitleWidth)) {
            val lp = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lp.width = if (match) target.toInt() else 0 // 0 = match constraints (normal layout)
            lp.horizontalBias = 0f
            view.layoutParams = lp
        }
    }

    private fun morphText(view: View, left: Float, top: Float, scale: Float, c: Float) {
        val rect = rectInRoot(view)
        place(view, rect, lerp(rect.left, left, (c * 2f).coerceAtMost(1f)), lerp(rect.top, top, c), lerp(1f, scale, c))
    }

    /** Draws [view] (laid out at [rect]) with its top-left at ([left], [top]) and uniform [scale]. */
    private fun place(view: View, rect: android.graphics.RectF, left: Float, top: Float, scale: Float) {
        view.pivotX = 0f; view.pivotY = 0f
        view.scaleX = scale; view.scaleY = scale
        view.translationX = left - rect.left
        view.translationY = top - rect.top
    }

    private fun resetMorph() {
        listOf(binding.cardCover, binding.tvNovelTitle, binding.tvChapterTitle, binding.playContainer, binding.seekBar,
            binding.btnBack, binding.btnMore, binding.btnShare, binding.actionsRow, binding.tvCurrentTime,
            binding.tvTotalTime, binding.btnPrevious, binding.btnRewind, binding.btnForward, binding.btnNext)
            .forEach { it.translationX = 0f; it.translationY = 0f; it.scaleX = 1f; it.scaleY = 1f; it.alpha = 1f }
        binding.seekBar.thumb?.alpha = 255
        binding.cardCover.radius = dp(12f)
        binding.ivMiniClose.alpha = 0f
        matchTitleWidths(false, 0f, 0f)
    }

    /** The view's laid-out bounds relative to the drag root, ignoring any translation/scale. */
    private fun rectInRoot(view: View): android.graphics.RectF {
        var x = 0f; var y = 0f
        var current: View = view
        while (current !== binding.dragRoot) {
            x += current.left; y += current.top
            current = current.parent as? View ?: break
        }
        return android.graphics.RectF(x, y, x + view.width, y + view.height)
    }

    private fun checkAndShowAd() {
        if (GlobalVariableManager.hasReadingSubscriptionPack()) {
            binding.adContainer.visibility = View.GONE
            return
        }

        val nativeAdEnabled = GlobalAppData.siteSettings?.config?.adUnits?.any { it.adType == "native" } == true
        if (nativeAdEnabled && GlobalVariableManager.isAdsEnabled()) {
            binding.adContainer.visibility = View.VISIBLE
            adsViewModel.showNativeAd(this, binding.adContainer, R.layout.layout_native_ad_player)
        } else {
            binding.adContainer.visibility = View.GONE
        }
    }

    private fun refreshPlayerMetadata() {
        val ctrl = controller ?: return
        val chapter = currentChapter ?: return
        
        if (ctrl.currentMediaItem?.mediaId == chapter.id && !ctrl.isPlaying && ctrl.currentPosition <= 0L) {
            val item = createMediaItem(chapter)
            AppLoggerHelper.d(TAG, "refreshPlayerMetadata: Refreshing item with artworkUri=${item.mediaMetadata.artworkUri}")
            ctrl.setMediaItem(item, false)
        }
    }

    private fun applySessionStatusToChapter(chapter: WoChapter) {
        val buyMap = woChpterViewModel.chapterIsBuyedStatus.value ?: emptyMap()
        val unlockMap = woChpterViewModel.chapterTemporaryUnlockStatus.value ?: emptyMap()
        chapter.id?.let { id ->
            buyMap[id]?.let { chapter.isBuyed = it }
            unlockMap[id]?.let { chapter.temporaryUnlock = it == "true" }
        }
    }

    private fun initializeWithChapter(chapter: WoChapter) {
        if (currentChapter?.id == chapter.id) return
        currentChapter = chapter; chapterId = chapter.id; lastReportedProgress = chapter.readingStatus?.toIntOrNull() ?: 0
        
        AppLoggerHelper.d(TAG, "initializeWithChapter: ${chapter.chapterName}, id=${chapter.id}")
        GlobalAppData.selectedChapter.value = chapter
        GlobalAppData.selectedNovel.value?.let { splashViewModel.saveCurrentReading(it, chapter) }
        
        if (chaptersList.none { it.id == chapter.id }) { chaptersList.add(chapter); chaptersList.sortBy { it.sqc?.toIntOrNull() ?: 0 } }
        setupUI(chapter); checkAndHandlePaywall(); proactivelyFetchNeighbors()
    }

    private fun proactivelyFetchNeighbors() {
        val current = currentChapter ?: return
        val nextId = current.nextChapterId
        if (!nextId.isNullOrEmpty() && chaptersList.none { it.id == nextId }) fetchAndAddChapterToList(nextId)
        val prevId = current.previousChapterId
        if (!prevId.isNullOrEmpty() && chaptersList.none { it.id == prevId }) fetchAndAddChapterToList(prevId)
    }

    private fun fetchAndAddChapterToList(id: String) {
        val idInt = id.toIntOrNull() ?: return
        lifecycleScope.launch {
            try {
                val chapter = woChpterViewModel.getChapter(idInt)
                if (chapter != null) {
                    applySessionStatusToChapter(chapter)
                    if (chaptersList.none { it.id == chapter.id }) {
                        chaptersList.add(chapter); chaptersList.sortBy { it.sqc?.toIntOrNull() ?: 0 }; checkAndQueueNextChapter()
                    }
                }
            } catch (e: Exception) { AppLoggerHelper.e(TAG, "Error pre-fetching $id", e) }
        }
    }

    private fun setupObservers() {
        adsViewModel.adDismissed.observe(this) { dismissed ->
            if (dismissed == true) completePendingInterstitialAction()
        }
        adsViewModel.adLoadFailed.observe(this) { failed ->
            if (failed == true) completePendingInterstitialAction()
        }

        woChpterViewModel._chapterModelsList.observe(this) { pageList ->
            if (!pageList.isNullOrEmpty()) {
                val allChapters = pageList.flatMap { it.chapters }
                allChapters.forEach { chapter -> if (chaptersList.none { it.id == chapter.id }) chaptersList.add(chapter) }
                chaptersList.sortBy { it.sqc?.toIntOrNull() ?: 0 }; checkAndQueueNextChapter()
            }
        }
        GlobalAppData.selectedNovel.observe(this) { novel ->
            if (novel != null) {
                val imageUrl = novel.imageLink?.takeIf { it.isNotBlank() } ?: novel.imageLinkCover?.takeIf { it.isNotBlank() } ?: novel.bannerImage
                binding.tvNovelTitle.text = decodeHtmlToTextV2(novel.title)
                bindCoverArt(imageUrl)
                checkAndShowAd()
            }
        }
        woChpterViewModel.chapterBuyStatusEvent.observe(this) { (id, isBought) -> if (isBought && id == currentChapter?.id) { currentChapter?.isBuyed = "1"; checkAndHandlePaywall(); startPlaybackIfReady() } }
        woChpterViewModel.chapterTemporaryUnlock.observe(this) { (id, isUnlocked) -> if (isUnlocked && id == currentChapter?.id) { currentChapter?.temporaryUnlock = true; checkAndHandlePaywall(); startPlaybackIfReady() } }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { binding.dragRoot.dismiss() }
        binding.btnShare.setOnClickListener { shareChapter() }
        binding.btnMore.setOnClickListener { showMoreOptions() }
        binding.btnViewFullScreen.setOnClickListener {
            val intent = Intent(this, PlayChapterActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); putExtra("extra_chapter_id", chapterId); putExtra("extra_novel_id", novelId) }
            startActivity(intent)
        }
        binding.btnPlayPause.setOnClickListener { val ctrl = this.controller ?: return@setOnClickListener; if (currentChapter != null && !canChapterBePlayed(currentChapter!!)) { showUnlockBottomSheet(); return@setOnClickListener }; if (ctrl.isPlaying) ctrl.pause() else if (ctrl.mediaItemCount == 0) currentChapter?.let { startPlayback(it) } else { if (ctrl.playbackState == Player.STATE_IDLE) ctrl.prepare(); ctrl.play() } }
        binding.btnForward.setOnClickListener { seekBy(10000) }
        binding.btnRewind.setOnClickListener { seekBy(-10000) }
        binding.btnNext.setOnClickListener { playNextChapter() }
        binding.btnPrevious.setOnClickListener { playPreviousChapter() }
        binding.btnEpisodes.setOnClickListener { showEpisodesSheet() }
        binding.btnSleepTimer.setOnClickListener { showSleepTimerDialog() }
        binding.btnPlaybackSpeed.setOnClickListener { showSpeedDialog() }
        binding.btnAutoUnlock.setOnClickListener {
            autoUnlock = !autoUnlock
            getSharedPreferences(PREFS_PLAYER, MODE_PRIVATE).edit().putBoolean(KEY_AUTO_UNLOCK, autoUnlock).apply()
            updateAutoUnlockUI()
            Toast.makeText(this, "Auto unlock ${if(autoUnlock) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }
        // Seek once on release; while dragging only the time label follows the thumb.
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) binding.tvCurrentTime.text = formatTime(progress.toLong()) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { isUserSeeking = false; seekBar?.let { controller?.seekTo(it.progress.toLong()) } }
        })
    }

    private fun updateAutoUnlockUI() {
        val state = binding.tvAutoUnlockState
        state.text = if (autoUnlock) "ON" else "OFF"
        state.setBackgroundResource(if (autoUnlock) R.drawable.bg_player_toggle_on else R.drawable.bg_player_toggle_off)
        state.setTextColor(if (autoUnlock) getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary) else getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
    }

    private fun setSettingHighlighted(icon: android.widget.ImageView, label: android.widget.TextView, active: Boolean) {
        val primary = getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
        icon.setColorFilter(if (active) primary else getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
        label.setTextColor(if (active) primary else getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
    }

    private fun updateSpeedLabel() {
        val speed = controller?.playbackParameters?.speed ?: 1f
        binding.tvPlaybackSpeed.text = "Speed (${formatSpeed(speed)})"
        setSettingHighlighted(binding.ivPlaybackSpeed, binding.tvPlaybackSpeed, speed != 1f)
    }

    private fun formatSpeed(speed: Float) = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

    private fun updateSleepTimerLabel() {
        val remaining = sleepTimerEndAt - System.currentTimeMillis()
        val active = sleepTimerEndAt > 0L && remaining > 0L
        binding.tvSleepTimer.text = if (active) formatTime(remaining) + " left" else "Sleep timer"
        setSettingHighlighted(binding.ivSleepTimer, binding.tvSleepTimer, active)
    }

    private fun getColorFromAttr(attr: Int): Int { val tv = android.util.TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data }

    private fun showMoreOptions() { val opt = arrayOf("Share", "Report", "Details"); AlertDialog.Builder(this).setItems(opt) { _, w -> when (w) { 0 -> shareChapter(); 1 -> showReportDialog(); 2 -> binding.btnViewFullScreen.performClick() } }.show() }

    private fun checkAndHandlePaywall() { val c = currentChapter ?: return; if (canChapterBePlayed(c)) startPlaybackIfReady() else if (autoUnlock) attemptAutoUnlock() else { showUnlockBottomSheet(); controller?.pause() } }

    private fun attemptAutoUnlock() {
        val novel = GlobalAppData.selectedNovel.value; val user = GlobalAppData.userDetailsLiveData.value
        if (novel != null && user != null) {
            val cp = GlobalVariableManager.getChapterCoinPrice(novel); val up = user.userData?.points?.toIntOrNull() ?: 0
            if (up >= cp && cp > 0) { woChpterViewModel.buyChapterWithCoins(cp); return }
            val dp = GlobalVariableManager.getChapterDiamondPrice(novel); val ud = user.userData?.diamond?.toIntOrNull() ?: 0
            if (ud >= dp && dp > 0) { woChpterViewModel.buyChapterWithDiamondsv2(dp); return }
        }
        showUnlockBottomSheet()
    }

    private fun canChapterBePlayed(chapter: WoChapter): Boolean {
        if (chapter.temporaryUnlock) return true
        val (_, userId) = tokenManager.getTokenAndUserId(); val fcc = GlobalVariableManager.getFreeChapterCount(); val sqc = chapter.sqc?.toIntOrNull() ?: 0
        return sqc <= fcc || chapter.isBuyed == "1" || chapter.userId == userId || GlobalVariableManager.hasReadingSubscriptionPack()
    }

    private fun showUnlockBottomSheet() { currentChapter?.let { GlobalAppData.selectedChapter.value = it }; UnlockChapterBottomSheetNew.newInstance(null).show(supportFragmentManager, "UnlockChapter") }

    /**
     * Loads [currentChapter] into the player unless the player already holds it (e.g. reopened from
     * the mini player). Decided from the controller's actual item, not the global flag, which can be
     * stale after a process restart. Safe to call before the controller connects: setupController()
     * calls it again once connected.
     */
    private fun startPlaybackIfReady() {
        val c = currentChapter ?: return
        if (!canChapterBePlayed(c)) return
        val ctrl = controller ?: return
        when {
            ctrl.currentMediaItem?.mediaId != c.id -> startPlayback(c)
            // Same chapter but stopped (e.g. mini player's close button): it must be re-prepared to play.
            ctrl.playbackState == Player.STATE_IDLE -> { ctrl.prepare(); ctrl.play(); startProgressUpdates() }
            else -> startProgressUpdates()
        }
        // The mini player (and the collapse-into-mini animation) key off this; it is cleared when the
        // mini player is closed, so restore it whenever this chapter is the one playing.
        if (GlobalAppData.currentlyPlayingChapter.value?.id != c.id) GlobalAppData.currentlyPlayingChapter.postValue(c)
    }

    private fun startPlayback(chapter: WoChapter) {
        val ctrl = this.controller ?: return
        val item = createMediaItem(chapter)
        AppLoggerHelper.d(TAG, "startPlayback: id=${chapter.id}, uri=${item.mediaMetadata.artworkUri}")
        ctrl.setMediaItem(item); ctrl.prepare(); ctrl.play(); lastQueuedChapterId = null
        GlobalAppData.currentlyPlayingChapter.postValue(chapter); startProgressUpdates()
    }

    private fun createMediaItem(chapter: WoChapter): MediaItem {
        val novel = GlobalAppData.selectedNovel.value
        val imageUrl = novel?.imageLink?.takeIf { it.isNotBlank() } ?: novel?.imageLinkCover?.takeIf { it.isNotBlank() } ?: novel?.bannerImage
        val artworkUri = imageUrl?.let { Uri.parse(it) }
        
        val metadata = MediaMetadata.Builder()
            .setTitle(decodeHtmlToTextV2(chapter.chapterName)).setArtist(decodeHtmlToTextV2(novel?.title ?: "")).setArtworkUri(artworkUri).build()
        return MediaItem.Builder().setMediaId(chapter.id ?: "").setUri(chapter.audioFilePath).setMediaMetadata(metadata).build()
    }

    private fun checkAndQueueNextChapter() {
        // Non-subscribers advance through playNextChapter(), where the configured
        // fullscreen mid-roll cadence can run before the next episode starts.
        if (shouldShowMidrollAds()) return
        val ctrl = this.controller ?: return
        val current = currentChapter ?: return
        val next = findAdjacentChapter(current, forward = true) ?: return
        val nextId = next.id ?: return
        if (nextId != lastQueuedChapterId && canChapterBePlayed(next) && ctrl.mediaItemCount == 1) {
            ctrl.addMediaItem(createMediaItem(next))
            lastQueuedChapterId = nextId
        }
    }

    private fun setupUI(chapter: WoChapter) { binding.tvChapterTitle.text = decodeHtmlToTextV2(chapter.chapterName); updatePlayPauseIcon(controller?.isPlaying == true) }

    private fun updateProgress() {
        updateSleepTimerLabel()
        val ctrl = this.controller ?: return
        if (!isUserSeeking) { binding.seekBar.progress = ctrl.currentPosition.toInt(); binding.tvCurrentTime.text = formatTime(ctrl.currentPosition) }
        if (ctrl.duration > 0) { if (!isUserSeeking) binding.seekBar.max = ctrl.duration.toInt(); binding.tvTotalTime.text = formatTime(ctrl.duration); val p = (ctrl.currentPosition.toFloat() / ctrl.duration * 100).toInt(); if (p >= lastReportedProgress + 10 || p >= 100) updateReadingStatus(p); if (ctrl.duration - ctrl.currentPosition < 15000) checkAndQueueNextChapter() }
    }

    private fun updateReadingStatus(progress: Int) {
        val c = currentChapter ?: return
        val fp = progress.coerceAtMost(100)
        if (fp <= lastReportedProgress) return
        lastReportedProgress = fp
        c.id?.let { chapterUpdateViewModel.updateReadingStatus(it, fp) }
        // Keep the locally saved "continue listening" chapter in step, so the home card shows real progress.
        c.readingStatus = fp.toString()
        GlobalAppData.selectedNovel.value?.let { splashViewModel.saveCurrentReading(it, c) }
    }

    private fun seekBy(deltaMs: Long) {
        val ctrl = controller ?: return
        val duration = ctrl.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        ctrl.seekTo((ctrl.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    private fun startProgressUpdates() {
        handler.removeCallbacks(updateProgressRunnable)
        handler.post(updateProgressRunnable)
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play)
    }

    /** Keeps the play button in place and swaps its icon for a spinner while audio buffers. */
    private fun showBuffering(buffering: Boolean) {
        binding.progressBarLoading.visibility = if (buffering) View.VISIBLE else View.GONE
        binding.btnPlayPause.imageAlpha = if (buffering) 0 else 255
    }

    private fun formatTime(millis: Long): String { val m = TimeUnit.MILLISECONDS.toMinutes(millis); val s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60; return String.format("%02d:%02d", m, s) }

    private fun findAdjacentChapter(current: WoChapter, forward: Boolean): WoChapter? {
        val linkedId = if (forward) current.nextChapterId else current.previousChapterId
        if (!linkedId.isNullOrEmpty()) {
            chaptersList.find { it.id == linkedId }?.let { return it }
        }

        val currentIndex = chaptersList.indexOfFirst { it.id == current.id }
        if (currentIndex == -1) return null
        val targetIndex = if (forward) currentIndex + 1 else currentIndex - 1
        return chaptersList.getOrNull(targetIndex)
    }

    private fun playNextChapter() {
        maybeShowInterstitialThen { playNextChapterNow() }
    }

    private fun playNextChapterNow() {
        val current = currentChapter ?: return
        val next = findAdjacentChapter(current, forward = true)
        if (next != null) {
            initializeWithChapter(next)
            return
        }

        val nextId = current.nextChapterId
        if (!nextId.isNullOrEmpty()) loadChapters(nextId)
        else Toast.makeText(this, "No more chapters", Toast.LENGTH_SHORT).show()
    }

    private fun shouldShowMidrollAds(): Boolean =
        GlobalVariableManager.isAdsEnabled() && !GlobalVariableManager.hasReadingSubscriptionPack()

    private fun maybeShowInterstitialThen(action: () -> Unit) {
        if (!shouldShowMidrollAds()) {
            action()
            return
        }
        val preferences = getSharedPreferences(PREFS_PLAYER, MODE_PRIVATE)
        val transitionCount = preferences.getInt(KEY_MIDROLL_COUNT, 0) + 1
        if (transitionCount < MIDROLL_INTERVAL) {
            preferences.edit().putInt(KEY_MIDROLL_COUNT, transitionCount).apply()
            action()
            return
        }
        preferences.edit().putInt(KEY_MIDROLL_COUNT, 0).apply()
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
        val current = currentChapter ?: return
        val previous = findAdjacentChapter(current, forward = false)
        if (previous != null) {
            initializeWithChapter(previous)
            return
        }

        val previousId = current.previousChapterId
        if (!previousId.isNullOrEmpty()) loadChapters(previousId)
        else Toast.makeText(this, "First chapter reached", Toast.LENGTH_SHORT).show()
    }

    /**
     * Episode list as a bottom sheet, like the reading screen's chapter sheet. It shares the novel's
     * chapter ViewModel, so the pages the player already loaded show at once and further pages load
     * on scroll. Picking a locked episode goes through the usual paywall in initializeWithChapter().
     */
    private fun showEpisodesSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetChaptersBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        sheetBinding.tvSheetTitle.text = "Episodes"
        sheetBinding.btnSort.visibility = View.GONE

        var scrolledToCurrent = false

        val adapter = ChapterAdapter(chapters = emptyList()) { chapter ->
            dialog.dismiss()
            applySessionStatusToChapter(chapter)
            initializeWithChapter(chapter)
        }
        val layoutManager = LinearLayoutManager(this)
        sheetBinding.rvChapters.layoutManager = layoutManager
        sheetBinding.rvChapters.adapter = adapter
        // The window is edge-to-edge: keep the last episode clear of the navigation bar.
        val listBottomPadding = sheetBinding.rvChapters.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(sheetBinding.rvChapters) { v, insets ->
            v.updatePadding(bottom = listBottomPadding + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }

        // Pages come from the shared cache, which another screen may have jumped ahead in: always
        // show the gap-free run from page 1 and fill the next missing page.
        fun loadNextPage() {
            if (woChpterViewModel.isLoading.value == true) return
            val page = woChpterViewModel.nextPageToLoad() ?: return
            lifecycleScope.launch {
                woChpterViewModel.fetchChapters(order = woChpterViewModel.cachedOrder, offset = page, status = "published", pagination = page, scrolling = page > 1, extra = true, currentReading = "0", fromPage = "audio_player_sheet")
            }
        }

        val pagesObserver = Observer<List<ChapterModel>> { pageList ->
            if (pageList.isNullOrEmpty()) return@Observer
            sheetBinding.progressBar.visibility = View.GONE
            val allChapters = woChpterViewModel.chaptersFromFirstPage(pageList)
            allChapters.forEach { applySessionStatusToChapter(it) }
            adapter.updateChapters(allChapters)
            if (!scrolledToCurrent) {
                val index = allChapters.indexOfFirst { it.id == currentChapter?.id }
                if (index != -1) { layoutManager.scrollToPositionWithOffset(index, 0); scrolledToCurrent = true }
            }
        }
        val loadingObserver = Observer<Boolean> { loading -> adapter.setLoadMore(show = loading && adapter.getChapters().isNotEmpty(), loading = loading) }
        woChpterViewModel._chapterModelsList.observe(this, pagesObserver)
        woChpterViewModel.isLoading.observe(this, loadingObserver)
        dialog.setOnDismissListener {
            woChpterViewModel._chapterModelsList.removeObserver(pagesObserver)
            woChpterViewModel.isLoading.removeObserver(loadingObserver)
        }

        if (woChpterViewModel._chapterModelsList.value.isNullOrEmpty()) sheetBinding.progressBar.visibility = View.VISIBLE
        if (woChpterViewModel.nextPageToLoad() == 1) loadNextPage()

        sheetBinding.rvChapters.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 5) loadNextPage()
            }
        })

        // Open tall enough to show a useful slice of the list.
        dialog.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.7f).toInt()
        dialog.show()
    }

    private fun showSpeedDialog() {
        val s = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f); val t = listOf("0.5x", "0.75x", "Normal", "1.25x", "1.5x", "2.0x")
        val current = s.indexOf(controller?.playbackParameters?.speed ?: 1f)
        AlertDialog.Builder(this).setTitle("Playback Speed").setSingleChoiceItems(t.toTypedArray(), current) { d, w -> controller?.setPlaybackSpeed(s[w]); updateSpeedLabel(); d.dismiss() }.show()
    }

    private fun showSleepTimerDialog() {
         val d = BottomSheetDialog(this); val db = LayoutSleepTimerDialogBinding.inflate(layoutInflater); d.setContentView(db.root)
         val times = listOf(15, 30, 45, 60, 90, 0)
         db.rvTimerOptions.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
             override fun onCreateViewHolder(p: ViewGroup, vt: Int) = object : RecyclerView.ViewHolder(ItemTimerOptionBinding.inflate(LayoutInflater.from(p.context), p, false).root) {}
             override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) { val m = times[pos]; val b = ItemTimerOptionBinding.bind(h.itemView); b.tvTimerOption.text = if (m == 0) "Turn off Timer" else "$m minutes"; b.root.setOnClickListener { setSleepTimer(m); d.dismiss() } }
             override fun getItemCount() = times.size
         }
         d.show()
    }

    private fun setSleepTimer(minutes: Int) {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        sleepTimerEndAt = 0L
        if (minutes > 0) {
            sleepTimerRunnable = Runnable { controller?.pause(); sleepTimerEndAt = 0L; updateSleepTimerLabel() }
            sleepTimerEndAt = System.currentTimeMillis() + minutes * 60 * 1000L
            sleepTimerHandler.postDelayed(sleepTimerRunnable!!, minutes * 60 * 1000L)
            Toast.makeText(this, "Sleep timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
        }
        updateSleepTimerLabel()
    }

    private fun shareChapter() {
        val chapter = currentChapter ?: return
        val chapterId = chapter.id?.takeIf { it.isNotBlank() } ?: return
        val currentNovelId = novelId?.takeIf { it.isNotBlank() }
            ?: chapter.novelId?.takeIf { it.isNotBlank() }
            ?: return
        val shareUrl = DeepLinkRouter.audioEpisodeLink(currentNovelId, chapterId)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Listen to: ${chapter.chapterName}\n${GlobalAppData.selectedNovel.value?.title}\n\n$shareUrl"
            )
        }
        startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun showReportDialog() { val input = EditText(this); input.hint = "Reason..."; AlertDialog.Builder(this).setTitle("Report").setView(input).setPositiveButton("Report") { _, _ -> Toast.makeText(this, "Reported", Toast.LENGTH_SHORT).show() }.show() }

    override fun onStart() { super.onStart(); val token = SessionToken(this, ComponentName(this, AudioPlayerService::class.java)); controllerFuture = MediaController.Builder(this, token).buildAsync(); controllerFuture?.addListener({ setupController() }, MoreExecutors.directExecutor()) }

    private fun setupController() { val ctrl = this.controller ?: return; AppLoggerHelper.d(TAG, "setupController: Connected"); ctrl.addListener(object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { updatePlayPauseIcon(isPlaying) }; override fun onPlaybackStateChanged(state: Int) { showBuffering(state == Player.STATE_BUFFERING); if (state == Player.STATE_READY) updateProgress(); if (state == Player.STATE_ENDED) playNextChapter() }
            // A queued next episode started on its own: move the screen (title, progress, paywall) to it.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return; chaptersList.find { it.id == mediaItem?.mediaId }?.let { initializeWithChapter(it) } }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) { updateSpeedLabel() } }); showBuffering(ctrl.playbackState == Player.STATE_BUFFERING); updatePlayPauseIcon(ctrl.isPlaying); updateSpeedLabel(); refreshPlayerMetadata(); updateProgress(); startPlaybackIfReady() }

    override fun onStop() { controllerFuture?.let { MediaController.releaseFuture(it) }; handler.removeCallbacks(updateProgressRunnable); super.onStop() }
}
