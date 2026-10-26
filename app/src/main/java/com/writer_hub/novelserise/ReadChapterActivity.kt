/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import ToastUtil
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.RatingBar
import android.widget.SeekBar
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.AppCore.tokenManager
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.models.ChapterModel
import com.engine.core.models.ReadingSettings
import com.engine.core.models.WoChapter
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel
import com.engine.core.viewmodel.SplashViewModel
import com.engine.core.viewmodel.WoChapterUpdateViewModel
import com.engine.core.viewmodel.WoChpterViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.writer_hub.novelserise.adapter.ChapterAdapter
import com.writer_hub.novelserise.databinding.ActivityReadChapterBinding
import com.writer_hub.novelserise.databinding.BottomSheetChaptersBinding
import com.writer_hub.novelserise.databinding.BottomSheetReadingSettingsBinding
import com.writer_hub.novelserise.sheet.UnlockChapterBottomSheetNew
import kotlinx.coroutines.launch

class ReadChapterActivity : BaseActivity() {

    private lateinit var binding: ActivityReadChapterBinding
    private var chapterViewModel: WoChpterViewModel? = null
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var splashViewModel: SplashViewModel
    private lateinit var chapterUpdateViewModel: WoChapterUpdateViewModel
    
    private var chapterId: Int = -1
    private var currentChapter: WoChapter? = null
    private lateinit var settings: ReadingSettings
    private var unlockBottomSheet: UnlockChapterBottomSheetNew? = null
    private var isTemporarilyUnlocked: Boolean = false
    private var lastReportedProgress: Int = 0

    companion object {
        private const val EXTRA_CHAPTER_ID = "extra_chapter_id"

        fun start(context: Context, chapterId: Int) {
            val intent = Intent(context, ReadChapterActivity::class.java)
            intent.putExtra(EXTRA_CHAPTER_ID, chapterId)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureScreen()
        binding = ActivityReadChapterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)
        splashViewModel = AppProvider.getViewModel(application, SplashViewModel::class.java)
        chapterUpdateViewModel = AppProvider.getViewModel(application, WoChapterUpdateViewModel::class.java)

        chapterId = intent.getIntExtra(EXTRA_CHAPTER_ID, -1)
        settings = splashViewModel.getReadingSetting()

        setupToolbar()
        applySettings()
        loadChapter()
        setupScrollListener()
        
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
        binding.btnComment.setOnClickListener { 
            currentChapter?.let { chapter ->
                ChapterCommentsActivity.start(this, chapter.novelId ?: "", chapter.id ?: "", "Chapter Comments")
            }
        }
        binding.btnRate.setOnClickListener { showRateCommentDialog() }
        binding.btnChapters.setOnClickListener { showChaptersBottomSheet() }
        
        binding.btnPrevChapter.setOnClickListener { navigateChapter(false) }
        binding.btnNextChapter.setOnClickListener { navigateChapter(true) }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupObservers() {
        val viewModel = chapterViewModel ?: return
        
        viewModel.chapterBuyStatusEvent.observe(this) { pair ->
            val (unlockedChapterId, isBought) = pair
            if (isBought && unlockedChapterId == currentChapter?.id) {
                // Update local object state instead of reloading from network
                currentChapter?.isBuyed = "1"
                checkAndHandlePaywall()
            }
        }

        viewModel.chapterTemporaryUnlock.observe(this) { pair ->
            val (unlockedChapterId, isUnlocked) = pair
            if (isUnlocked && unlockedChapterId == currentChapter?.id) {
                // Flag as temporarily unlocked for this session
                isTemporarilyUnlocked = true
                currentChapter?.temporaryUnlock = true
                checkAndHandlePaywall()
            }
        }
    }

    private fun applySettings() {
        binding.rootContainer.setBackgroundColor(settings.themeBackgroundColor)
        binding.tvContent.setTextColor(settings.themeTextColor)
        binding.tvChapterTitle.setTextColor(settings.themeTextColor)
        binding.tvContent.textSize = settings.fontSize
        binding.tvContent.setLineSpacing(0f, settings.lineSpacing)
        
        val isDarkReaderTheme = ColorUtils.calculateLuminance(settings.themeBackgroundColor) < 0.45
        val iconColor = if (isDarkReaderTheme) Color.WHITE else Color.parseColor("#111827")
        val accentColor = if (isDarkReaderTheme) {
            Color.parseColor("#FBBF24")
        } else {
            com.engine.core.manager.ThemeManager.getThemeColor(this, "colorPrimary")
        }
        // Bars blend into the page (a touch of the text colour) so reading stays immersive.
        val barColor = ColorUtils.blendARGB(settings.themeBackgroundColor, iconColor, 0.04f)
        val hairline = ColorUtils.setAlphaComponent(iconColor, 0x1F)
        val density = resources.displayMetrics.density

        binding.toolbar.background = barWithEdge(barColor, hairline, bottomEdge = true)
        binding.bottomBar.background = barWithEdge(barColor, hairline, bottomEdge = false)
        binding.toolbar.elevation = 0f
        binding.bottomBar.elevation = 0f
        binding.toolbar.setTitleTextColor(iconColor)

        // Update Navigation Icon Tint
        binding.toolbar.navigationIcon?.setTint(iconColor)

        binding.btnSettings.setColorFilter(iconColor)
        binding.btnComment.setColorFilter(iconColor)
        binding.btnRate.setColorFilter(iconColor)
        binding.btnChapters.setColorFilter(iconColor)

        // Prev: outlined pill. Next: filled accent pill.
        binding.btnPrevChapter.setTextColor(iconColor)
        binding.btnPrevChapter.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 100 * density
            setStroke((1 * density).toInt(), ColorUtils.setAlphaComponent(iconColor, 0x40))
        }
        val onAccent = if (ColorUtils.calculateLuminance(accentColor) > 0.5) Color.parseColor("#111827") else Color.WHITE
        binding.btnNextChapter.setTextColor(onAccent)
        binding.btnNextChapter.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 100 * density
            setColor(accentColor)
        }

        unlockBottomSheet?.applyTheme(settings)
    }

    /** Solid bar with a 1px hairline on its inner edge. */
    private fun barWithEdge(color: Int, edge: Int, bottomEdge: Boolean): android.graphics.drawable.Drawable {
        val px = maxOf(1, resources.displayMetrics.density.toInt())
        val layers = android.graphics.drawable.LayerDrawable(
            arrayOf(android.graphics.drawable.ColorDrawable(edge), android.graphics.drawable.ColorDrawable(color))
        )
        if (bottomEdge) layers.setLayerInset(1, 0, 0, 0, px) else layers.setLayerInset(1, 0, px, 0, 0)
        return layers
    }

    private fun loadChapter() {
        if (chapterId == -1) return
        
        lifecycleScope.launch {
            toggleLoading(true)
            
            val initialViewModel = AppProvider.getViewModel(application, WoChpterViewModel::class.java)
            val chapter = initialViewModel.getChapter(chapterId)
            
            if (chapter != null) {
                currentChapter = chapter
                isTemporarilyUnlocked = false // Reset for new chapter
                
                // Get progress from shared ViewModel if available, otherwise from chapter data
                val sharedProgress = chapterUpdateViewModel.getProgressForChapter(chapter.id)
                val initialProgress = chapter.readingStatus?.toIntOrNull() ?: 0
                lastReportedProgress = maxOf(sharedProgress, initialProgress)
                
                AppLoggerHelper.d("ReadChapter", "Opening chapter ${chapter.id}. Last reading status: $lastReportedProgress%")
                
                GlobalAppData.selectedChapter.value = chapter
                
                val novelId = chapter.novelId ?: ""
                chapterViewModel = AppProvider.getUniqueViewModel(application, WoChpterViewModel::class.java, novelId)
                
                // Apply session status (unlock/buy) before checking paywall
                applySessionStatusToChapter(chapter)
                
                setupObservers()

                val existingNovel = GlobalAppData.selectedNovel.value
                val novelToSave = if (existingNovel == null || existingNovel.id != novelId) {
                    val novel = novelViewModel.fetchSingleNovelById(novelId, "reading")
                    GlobalAppData.selectedNovel.value = novel
                    novel
                } else {
                    existingNovel
                }

                // Save last reading to SplashViewModel
                if (novelToSave != null) {
                    splashViewModel.saveCurrentReading(novelToSave, chapter)
                }

                toggleLoading(false)
                
                // Always render content and make it visible
                displayChapter(chapter)
                
                // Then check paywall status
                checkAndHandlePaywall()
                
                // Resume reading position once layout is stable
                resumeReadingPosition(lastReportedProgress)
                
            } else {
                toggleLoading(false)
                ToastUtil.showToast(this@ReadChapterActivity, "Failed to load chapter")
            }
        }
    }

    private fun applySessionStatusToChapter(chapter: WoChapter) {
        val viewModel = chapterViewModel ?: return
        val buyMap = viewModel.chapterIsBuyedStatus.value ?: emptyMap()
        val unlockMap = viewModel.chapterTemporaryUnlockStatus.value ?: emptyMap()
        chapter.id?.let { id ->
            buyMap[id]?.let { chapter.isBuyed = it }
            unlockMap[id]?.let { 
                val isUnlocked = it == "true"
                chapter.temporaryUnlock = isUnlocked
                if (isUnlocked) isTemporarilyUnlocked = true
            }
        }
    }

    private fun resumeReadingPosition(progress: Int) {
        if (progress <= 0) return
        
        // Wait for the ScrollView to measure its children properly
        binding.scrollView.post {
            val contentHeight = binding.scrollView.getChildAt(0).measuredHeight
            val scrollHeight = binding.scrollView.measuredHeight
            val totalScrollableRange = contentHeight - scrollHeight
            
            if (totalScrollableRange > 0) {
                val scrollY = (progress.toFloat() / 100 * totalScrollableRange).toInt()
                AppLoggerHelper.d("ReadChapter", "Resuming position: $progress% -> ScrollY: $scrollY (Range: $totalScrollableRange)")
                binding.scrollView.scrollTo(0, scrollY)
            } else {
                // If it's not ready, try one more time after a short delay
                binding.scrollView.postDelayed({
                    val newRange = binding.scrollView.getChildAt(0).measuredHeight - binding.scrollView.measuredHeight
                    if (newRange > 0) {
                        val newScrollY = (progress.toFloat() / 100 * newRange).toInt()
                        binding.scrollView.scrollTo(0, newScrollY)
                    }
                }, 100)
            }
        }
    }

    private fun setupScrollListener() {
        binding.scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            val totalHeight = v.getChildAt(0).measuredHeight - v.measuredHeight
            if (totalHeight > 0) {
                val progress = (scrollY.toFloat() / totalHeight * 100).toInt()
                // Update only if progress has increased and it's a significant change
                if (progress > lastReportedProgress) {
                    if (progress >= lastReportedProgress + 10 || progress >= 100) {
                        Log.d("ReadChapterActivity", "setupScrollListener: Significant progress change detected: $progress%")
                        updateReadingStatus(progress)
                    }
                }
            }
        })
    }

    private fun updateReadingStatus(progress: Int) {
        val chapter = currentChapter ?: return
        val finalProgress = progress.coerceAtMost(100)
        
        // Final sanity check to ensure no regression
        if (finalProgress > lastReportedProgress) {
            lastReportedProgress = finalProgress
            Log.d("ReadChapterActivity", "updateReadingStatus: Sending update to server. Chapter ID: ${chapter.id}, Progress: $finalProgress%")
            chapter.id?.let { id ->
                chapterUpdateViewModel.updateReadingStatus(id, finalProgress)
            }
        }
    }

    private fun checkAndHandlePaywall() {
        val chapter = currentChapter ?: return
        if (canChapterBeRead(chapter)) {
            unlockBottomSheet?.dismiss()
            unlockBottomSheet = null
            binding.scrollView.visibility = View.VISIBLE
        } else {
            showUnlockBottomSheet()
        }
    }

    private fun canChapterBeRead(chapter: WoChapter): Boolean {
        if (isTemporarilyUnlocked || chapter.temporaryUnlock) return true
        
        val (_, userId) = tokenManager.getTokenAndUserId()
        val freeChaptersCount = GlobalVariableManager.getFreeChapterCount()
        val chapterSqc = chapter.sqc?.toIntOrNull() ?: 0
        
        val isFreeChapter = chapterSqc <= freeChaptersCount
        val isBought = chapter.isBuyed == "1"
        val isOwner = chapter.userId == userId
        val hasReadingPack = GlobalVariableManager.hasReadingSubscriptionPack()

        return isFreeChapter || isBought || isOwner || hasReadingPack
    }

    private fun showUnlockBottomSheet() {
        if (unlockBottomSheet?.isAdded == true) return
        
        // Ensure selected chapter is set for the sheet
        currentChapter?.let { GlobalAppData.selectedChapter.value = it }
        
        unlockBottomSheet = UnlockChapterBottomSheetNew.newInstance(settings)
        unlockBottomSheet?.show(supportFragmentManager, "UnlockChapter")
    }

    private fun toggleLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.shimmerLayout.root.visibility = View.VISIBLE
            binding.shimmerLayout.shimmerViewContainer.startShimmer()
            binding.scrollView.visibility = View.GONE
        } else {
            binding.shimmerLayout.shimmerViewContainer.stopShimmer()
            binding.shimmerLayout.root.visibility = View.GONE
            // ScrollView visibility is handled by displayChapter and checkAndHandlePaywall
        }
    }

    private fun displayChapter(chapter: WoChapter) {
        binding.tvChapterTitle.text = decodeHtmlToTextV2(chapter.chapterName)
        binding.tvContent.text = decodeHtmlToTextV2(chapter.content)
        supportActionBar?.title = decodeHtmlToTextV2(chapter.novelTitle) ?: "Reading"
        
        binding.scrollView.visibility = View.VISIBLE
    }

    private fun navigateChapter(next: Boolean) {
        val targetChapterIdStr = if (next) currentChapter?.nextChapterId else currentChapter?.previousChapterId
        
        if (!targetChapterIdStr.isNullOrEmpty() && targetChapterIdStr != "0") {
            val nextId = targetChapterIdStr.toIntOrNull()
            if (nextId != null) {
                chapterId = nextId
                loadChapter()
            } else {
                ToastUtil.showToast(this, "Invalid chapter ID")
            }
        } else {
            val message = if (next) "This is the last chapter" else "This is the first chapter"
            ToastUtil.showToast(this, message)
        }
    }

    private fun showSettingsDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = BottomSheetReadingSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Initialize Font Size
        dialogBinding.sbFontSize.progress = settings.fontSize.toInt()
            .coerceIn(dialogBinding.sbFontSize.min, dialogBinding.sbFontSize.max)
        dialogBinding.sbFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settings.fontSize = progress.toFloat().coerceAtLeast(12f)
                    applySettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                splashViewModel.saveReadingSettings(settings)
            }
        })

        // Background Colors
        val swatchOutline = android.util.TypedValue().also {
            dialogBinding.root.context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface, it, true
            )
        }.data
        val swatchStrokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
        listOf(
            dialogBinding.viewColorWhite to Color.WHITE,
            dialogBinding.viewColorSepia to Color.parseColor("#F4ECD8"),
            dialogBinding.viewColorGrey to Color.parseColor("#333333"),
            dialogBinding.viewColorBlack to Color.BLACK
        ).forEach { (swatch, color) ->
            swatch.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                setStroke(swatchStrokeWidth, swatchOutline)
            }
        }
        dialogBinding.viewColorWhite.setOnClickListener { updateTheme("#FFFFFF", "#000000", "LIGHT"); dialog.dismiss() }
        dialogBinding.viewColorSepia.setOnClickListener { updateTheme("#F4ECD8", "#5B4636", "SEPIA"); dialog.dismiss() }
        dialogBinding.viewColorGrey.setOnClickListener { updateTheme("#333333", "#E0E0E0", "GREY"); dialog.dismiss() }
        dialogBinding.viewColorBlack.setOnClickListener { updateTheme("#000000", "#FFFFFF", "DARK"); dialog.dismiss() }

        // Line Spacing
        dialogBinding.sbLineSpacing.progress = (settings.lineSpacing * 5).toInt()
            .coerceIn(dialogBinding.sbLineSpacing.min, dialogBinding.sbLineSpacing.max)
        dialogBinding.sbLineSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settings.lineSpacing = progress / 5f
                    applySettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                splashViewModel.saveReadingSettings(settings)
            }
        })

        dialog.show()
    }

    private fun updateTheme(bgColor: String, textColor: String, type: String) {
        settings.themeBackgroundColor = Color.parseColor(bgColor)
        settings.themeTextColor = Color.parseColor(textColor)
        settings.themeType = type
        applySettings()
        splashViewModel.saveReadingSettings(settings)
    }

    private fun showRateCommentDialog() {
        val chapter = currentChapter ?: return
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_rate_comment)

        val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar)
        val etComment = dialog.findViewById<TextInputEditText>(R.id.etComment)
        val btnSubmit = dialog.findViewById<Button>(R.id.btnSubmit)

        btnSubmit?.setOnClickListener {
            val rating = ratingBar?.rating?.toInt() ?: 0
            val comment = etComment?.text?.toString()?.trim() ?: ""

            if (rating > 0 || comment.isNotEmpty()) {
                // Ensure novel ID is set in ViewModel
                chapterViewModel?.setSelectedNovelId(chapter.novelId ?: "")
                
                if (rating > 0) {
                    submitRating(chapter.id, rating)
                }

                if (comment.isNotEmpty()) {
                    submitComment(chapter.id, comment)
                }
                
                dialog.dismiss()
            } else {
                ToastUtil.showToast(this, "Please provide a rating or a comment")
            }
        }

        dialog.show()
    }

    private fun submitRating(chapterId: String?, rating: Int) {
        lifecycleScope.launch {
            chapterViewModel?.updateChapterDetails(
                wordCount = null,
                chapterId = chapterId,
                type = "rating",
                value = rating.toString(),
                paragraphId = null,
                id = null
            )
        }
    }

    private fun submitComment(chapterId: String?, comment: String) {
        chapterId?.let {
            chapterViewModel?.addnewChapterComment(
                chapterId = it,
                type = "comment",
                value = comment,
                paragraphId = null
            )
        }
    }

    private fun showChaptersBottomSheet() {
        val novelId = currentChapter?.novelId ?: return
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetChaptersBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Apply Reading Theme to the Bottom Sheet
        sheetBinding.root.setBackgroundColor(settings.themeBackgroundColor)
        sheetBinding.tvSheetTitle.setTextColor(settings.themeTextColor)
        sheetBinding.btnSort.imageTintList = ColorStateList.valueOf(settings.themeTextColor)
        sheetBinding.progressBar.progressTintList = ColorStateList.valueOf(settings.themeTextColor)

        var currentOrder = "ASC"
        var currentPage = 1
        var isLastPage = false
        var isLoadingNextPage = false
        
        val adapter = ChapterAdapter(
            chapters = emptyList(),
            settings = settings,
            onChapterClick = { chapter ->
                chapter.id?.let {
                    chapterId = it.toInt()
                    loadChapter()
                    dialog.dismiss()
                }
            }
        )

        sheetBinding.rvChapters.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvChapters.adapter = adapter

        fun loadSheetChapters(page: Int, order: String) {
            if (page == 1) sheetBinding.progressBar.visibility = View.VISIBLE
            isLoadingNextPage = true
            lifecycleScope.launch {
                chapterViewModel?.fetchChapters(
                    order = order,
                    offset = page,
                    status = "published",
                    pagination = 1,
                    scrolling = page > 1,
                    extra = true,
                    currentReading = "0",
                    fromPage = "read_chapter_sheet"
                )
                isLoadingNextPage = false
            }
        }

        val observer = Observer<List<ChapterModel>> { pageList ->
            sheetBinding.progressBar.visibility = View.GONE
            if (!pageList.isNullOrEmpty()) {
                val allChapters = pageList.flatMap { it.chapters }
                adapter.updateChapters(allChapters)
                
                // Update pagination status from the last fetched page
                val lastPageModel = pageList.lastOrNull()
                currentPage = lastPageModel?.current_page ?: currentPage
                isLastPage = currentPage >= (lastPageModel?.total_pages ?: 0)
                
                // Scroll to current chapter only on initial load
                if (currentPage == 1 && adapter.itemCount > 0) {
                    val currentIndex = allChapters.indexOfFirst { it.id == currentChapter?.id }
                    if (currentIndex != -1) {
                        sheetBinding.rvChapters.scrollToPosition(currentIndex)
                    }
                }
            }
        }

        chapterViewModel?._chapterModelsList?.observe(this, observer)
        
        dialog.setOnDismissListener {
            chapterViewModel?._chapterModelsList?.removeObserver(observer)
        }

        sheetBinding.rvChapters.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                // Load more when user is near the end of the list (e.g., 5 items left)
                if (!isLoadingNextPage && !isLastPage && totalItemCount <= (lastVisibleItem + 5)) {
                    loadSheetChapters(currentPage + 1, currentOrder)
                }
            }
        })

        sheetBinding.btnSort.setOnClickListener {
            currentOrder = if (currentOrder == "ASC") "DESC" else "ASC"
            chapterViewModel?.clearChapterCache()
            loadSheetChapters(1, currentOrder)
        }

        // Initialize with first page
        chapterViewModel?.clearChapterCache()
        loadSheetChapters(1, currentOrder)
        dialog.show()
    }
}
