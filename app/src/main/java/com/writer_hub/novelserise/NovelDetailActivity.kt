/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import com.writer_hub.novelserise.ui.deepLink.DeepLinkRouter
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.ChapterModel
import com.engine.core.models.Novel
import com.engine.core.models.WoChapter
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.GiftViewModel
import com.engine.core.viewmodel.LibraryViewModel
import com.engine.core.viewmodel.NovelViewModel
import com.engine.core.viewmodel.WoChapterUpdateViewModel
import com.engine.core.viewmodel.WoChpterViewModel
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.writer_hub.novelserise.adapter.ChapterAdapter
import com.writer_hub.novelserise.adapter.GiftAdapter
import com.writer_hub.novelserise.adapter.SupporterAdapter
import com.writer_hub.novelserise.databinding.ActivityNovelDetailBinding
import com.writer_hub.novelserise.databinding.LayoutGiveGiftDialogBinding
import com.writer_hub.novelserise.util.DisplayText
import com.writer_hub.novelserise.ui.custom.OptionsDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NovelDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityNovelDetailBinding
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var chapterViewModel: WoChpterViewModel
    private lateinit var giftViewModel: GiftViewModel
    private lateinit var chapterUpdateViewModel: WoChapterUpdateViewModel
    
    private var novelId: String? = null
    private var currentNovel: Novel? = null
    private lateinit var chapterAdapter: ChapterAdapter
    private lateinit var supporterAdapter: SupporterAdapter
    
    private var currentOrder: String = "ASC"
    private var currentPage: Int = 1
    private var totalPages: Int = 1
    private var isLoadMoreMode: Boolean = false
    // Page the list starts at: 1, or the page picked with the page controls.
    private var firstShownPage: Int = 1

    companion object {
        private const val EXTRA_NOVEL_ID = "extra_novel_id"

        fun start(context: Context, novelId: String) {
            val intent = Intent(context, NovelDetailActivity::class.java)
            intent.putExtra(EXTRA_NOVEL_ID, novelId)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovelDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        novelId = intent.getStringExtra(EXTRA_NOVEL_ID)

        // Initialize ViewModels using AppProvider
        novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)
        libraryViewModel = AppProvider.getViewModel(application, LibraryViewModel::class.java)
        
        // Use Unique ViewModel for Chapters to sync unlock/buy status across all screens for this novel
        chapterViewModel = if (!novelId.isNullOrEmpty()) {
            AppProvider.getUniqueViewModel(application, WoChpterViewModel::class.java, novelId!!)
        } else {
            AppProvider.getViewModel(application, WoChpterViewModel::class.java)
        }
        
        giftViewModel = AppProvider.getViewModel(application, GiftViewModel::class.java)
        chapterUpdateViewModel = AppProvider.getViewModel(application, WoChapterUpdateViewModel::class.java)

        novelId?.let { chapterViewModel.setSelectedNovelId(it) }

        setupToolbar()
        setupRecyclerView()
        setupAutoLoadChapters()
        setupObservers()
        setupListeners()
        loadNovelDetails()
        
        // Initial load
        refreshChapters()
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

    private fun setupRecyclerView() {
        // Rows align with the page's 16dp padding (no extra side margin).
        chapterAdapter = ChapterAdapter(emptyList(), flushRows = true) { chapter ->
            // On chapter click handling
            val novel = currentNovel ?: return@ChapterAdapter
            
            if (GlobalAppData.isAudioMode()) {
                 chapter.id?.let { chapterId ->
                    PlayChapterActivity.start(this, chapterId, novel.id ?: "")
                }
            } else {
                ReadChapterActivity.start(this, chapter.id?.toInt() ?: 0)
            }
        }
        binding.rvChapters.apply {
            layoutManager = LinearLayoutManager(this@NovelDetailActivity)
            adapter = chapterAdapter
            addItemDecoration(
                MaterialDividerItemDecoration(
                    this@NovelDetailActivity,
                    LinearLayoutManager.VERTICAL
                ).apply {
                    dividerThickness = (resources.displayMetrics.density * 1.25f).toInt().coerceAtLeast(1)
                    // Starts under the title (12dp row padding + 36dp number + no side margin).
                    dividerInsetStart = (resources.displayMetrics.density * 48f).toInt()
                    dividerInsetEnd = 0
                    dividerColor = ContextCompat.getColor(this@NovelDetailActivity, R.color.option_card_stroke)
                    isLastItemDecorated = false
                }
            )
        }

        supporterAdapter = SupporterAdapter(emptyList()) { supporter ->
            supporter.sender?.userId?.let { UserDetailsActivity.start(this, it) }
        }
        binding.rvSupporters.apply {
            layoutManager = LinearLayoutManager(this@NovelDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = supporterAdapter
        }
    }

    private fun setupObservers() {
        chapterViewModel._chapterModelsList.observe(this) { pageList ->
            if (pageList.isNullOrEmpty()) {
                chapterAdapter.updateChapters(emptyList())
                binding.btnLoadMore.visibility = View.GONE
                updatePaginationUI()
                return@observe
            }

            val shownPages = shownPages(pageList)
            currentPage = shownPages.lastOrNull()?.current_page ?: currentPage
            val allChapters = shownPages.flatMap { it.chapters }
            
            // Sync with session status maps
            syncChaptersWithSessionStatus(allChapters)
            
            // Populate initial progress into the UpdateViewModel
            val initialProgress = allChapters.mapNotNull { ch ->
                val id = ch.id ?: return@mapNotNull null
                val status = ch.readingStatus?.toIntOrNull() ?: 0
                if (status > 0) id to status else null
            }.toMap()
            chapterUpdateViewModel.setInitialProgress(initialProgress)
            
            chapterAdapter.updateChapters(allChapters)
            
            pageList.lastOrNull()?.let { lastPage ->
                totalPages = lastPage.total_pages
                updatePaginationUI()
                
                // Further pages load automatically on scroll (see setupAutoLoadChapters).
                binding.btnLoadMore.visibility = View.GONE
            }
        }

        chapterViewModel.isLoading.observe(this) { isLoading ->
            toggleLoading(isLoading)
        }

        // Observe local progress updates from the shared ViewModel
        chapterUpdateViewModel.chapterProgressMap.observe(this) { progressMap ->
            chapterAdapter.updateProgressMap(progressMap)
        }

        // Observe the Session Buy Status Map
        chapterViewModel.chapterIsBuyedStatus.observe(this) { statusMap ->
            val pageList = chapterViewModel._chapterModelsList.value
            val allChapters = pageList?.let { shownPages(it) }?.flatMap { it.chapters } ?: return@observe
            
            var changed = false
            allChapters.forEach { chapter ->
                val id = chapter.id ?: return@forEach
                statusMap[id]?.let { 
                    if (chapter.isBuyed != it) {
                        chapter.isBuyed = it
                        changed = true
                    }
                }
            }
            
            if (changed) {
                chapterAdapter.updateChapters(allChapters)
            }
        }

        // Observe the Session Temporary Unlock Status Map
        chapterViewModel.chapterTemporaryUnlockStatus.observe(this) { statusMap ->
            val pageList = chapterViewModel._chapterModelsList.value
            val allChapters = pageList?.let { shownPages(it) }?.flatMap { it.chapters } ?: return@observe
            
            var changed = false
            allChapters.forEach { chapter ->
                val id = chapter.id ?: return@forEach
                statusMap[id]?.let { 
                    val isUnlocked = it == "true"
                    if (chapter.temporaryUnlock != isUnlocked) {
                        chapter.temporaryUnlock = isUnlocked
                        changed = true
                    }
                }
            }
            
            if (changed) {
                chapterAdapter.updateChapters(allChapters)
            }
        }
        
        // Observe currently playing chapter to highlight it in the list
        GlobalAppData.currentlyPlayingChapter.observe(this) {
            val pageList = chapterViewModel._chapterModelsList.value
            val allChapters = pageList?.let { shownPages(it) }?.flatMap { it.chapters } ?: return@observe
            chapterAdapter.updateChapters(allChapters)
        }

        giftViewModel.giftHistory.observe(this) { history ->
            if (history.isNullOrEmpty()) {
                binding.layoutSupporters.visibility = View.GONE
            } else {
                binding.layoutSupporters.visibility = View.VISIBLE
                supporterAdapter.updateData(history.take(10)) // Show top 10
            }
        }
    }

    /**
     * The chapter cache is shared with the player screens, which load their own pages (e.g. page 1
     * while this list was jumped to page 3). Show only the run of consecutive pages from the page
     * this list starts at, so it neither jumps back nor shows gaps.
     */
    private fun shownPages(pageList: List<ChapterModel>): List<ChapterModel> {
        val byPage = pageList.associateBy { it.current_page }
        return generateSequence(firstShownPage) { it + 1 }.map { byPage[it] }.takeWhile { it != null }.filterNotNull().toList()
    }

    private fun jumpToPage(page: Int) {
        currentPage = page
        firstShownPage = page
        isLoadMoreMode = false
        chapterViewModel.clearChapterCache()
        loadChapters()
    }

    private fun syncChaptersWithSessionStatus(chapters: List<WoChapter>) {
        val buyMap = chapterViewModel.chapterIsBuyedStatus.value ?: emptyMap()
        val unlockMap = chapterViewModel.chapterTemporaryUnlockStatus.value ?: emptyMap()
        
        chapters.forEach { chapter ->
            val id = chapter.id ?: return@forEach
            buyMap[id]?.let { chapter.isBuyed = it }
            unlockMap[id]?.let { chapter.temporaryUnlock = it == "true" }
        }
    }

    /**
     * Loads the next page of chapters when the user scrolls near the end of the list, instead of
     * needing a "Load More" tap. One request at a time; stops at the last page.
     */
    private fun setupAutoLoadChapters() {
        binding.nestedScrollView.setOnScrollChangeListener { v: androidx.core.widget.NestedScrollView, _, scrollY, _, _ ->
            val content = v.getChildAt(0) ?: return@setOnScrollChangeListener
            val distanceToEnd = content.height - (scrollY + v.height)
            if (distanceToEnd < v.height * 1.5f) loadNextChapterPage()
        }
    }

    private fun loadNextChapterPage() {
        if (chapterViewModel.isLoading.value == true || currentPage >= totalPages) return
        currentPage++
        isLoadMoreMode = true
        loadChapters()
    }

    private fun toggleLoading(isLoading: Boolean) {
        val fullShimmerContainer: ShimmerFrameLayout = binding.novelDetailShimmer.shimmerViewContainer
        binding.progressLoadMore.visibility = if (isLoading && isLoadMoreMode && currentNovel != null) View.VISIBLE else View.GONE

        if (isLoading) {
            if (currentNovel == null) {
                // First time loading - show full screen shimmer
                binding.novelDetailShimmer.root.visibility = View.VISIBLE
                fullShimmerContainer.startShimmer()
                
                binding.appBarLayout.visibility = View.GONE
                binding.nestedScrollView.visibility = View.GONE
                binding.bottomActionLayout.visibility = View.GONE
            } else if (!isLoadMoreMode) {
                // Changing page/sort - only show chapter list shimmer
                binding.shimmerChapters.visibility = View.VISIBLE
                binding.shimmerChapters.startShimmer()
                binding.layoutChaptersContent.visibility = View.GONE
            }
        } else {
            // Hide all shimmers and show content
            fullShimmerContainer.stopShimmer()
            binding.novelDetailShimmer.root.visibility = View.GONE
            
            binding.shimmerChapters.stopShimmer()
            binding.shimmerChapters.visibility = View.GONE
            
            binding.appBarLayout.visibility = View.VISIBLE
            binding.nestedScrollView.visibility = View.VISIBLE
            binding.bottomActionLayout.visibility = View.VISIBLE
            binding.layoutChaptersContent.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        binding.tvAuthor.setOnClickListener {
            val userId = currentNovel?.userId
            if (!userId.isNullOrEmpty()) {
                UserDetailsActivity.start(this, userId)
            }
        }

        binding.btnReadNow.setOnClickListener {
            val novel = currentNovel ?: return@setOnClickListener
            
            // A chapter of this novel still loaded in the player is fresher than the server's last-read id.
            val playingId = GlobalAppData.currentlyPlayingChapter.value
                ?.takeIf { GlobalAppData.isAudioMode() && it.novelId == novel.id }?.id?.toIntOrNull()
            val lastReadId = playingId ?: novel.lastReadChapterId?.toIntOrNull()
            if (lastReadId != null && lastReadId > 0) {
                if (GlobalAppData.isAudioMode()) {
                    // Same path as Home's "Continue Listening": the player sheet resumes a paused/stopped chapter.
                    AudioPlayerActivity.start(this, lastReadId.toString(), novel.id ?: "")
                } else {
                    ReadChapterActivity.start(this, lastReadId)
                }
            } else {
                val chapters = chapterViewModel._chapterModelsList.value?.firstOrNull { it.current_page == 1 }?.chapters
                val firstChapter = chapters?.firstOrNull()
                val firstChapterId = firstChapter?.id
                
                if (firstChapterId != null) {
                    if (GlobalAppData.isAudioMode()) {
                        PlayChapterActivity.start(this, firstChapterId, novel.id ?: "")
                    } else {
                        ReadChapterActivity.start(this, firstChapterId.toInt())
                    }
                } else {
                    Toast.makeText(this, "No chapters available", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.layoutChapterHeader.setOnClickListener {
            showPageSelectionDialog()
        }

        binding.btnSort.setOnClickListener { showSortDialog() }

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 1) jumpToPage(currentPage - 1)
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPage < totalPages) jumpToPage(currentPage + 1)
        }

        binding.tvPageIndicator.setOnClickListener {
            showPageSelectionDialog()
        }

        binding.btnLoadMore.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                isLoadMoreMode = true
                loadChapters()
            }
        }

        binding.btnShare.setOnClickListener {
            shareNovel()
        }

        binding.btnLibrary.setOnClickListener {
            toggleLibrary()
        }

        binding.btnGiveGift.setOnClickListener {
            showGiveGiftDialog()
        }

        binding.btnViewAllSupporters.setOnClickListener {
            val id = novelId ?: return@setOnClickListener
            // Start NovelGiftsActivity (to be created)
            val intent = Intent(this, NovelGiftsActivity::class.java)
            intent.putExtra("NOVEL_ID", id)
            startActivity(intent)
        }
    }

    private fun showGiveGiftDialog() {
        val novel = currentNovel ?: return
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
                    // Refresh gift history
                    novelId?.let { giftViewModel.fetchGiftHistory("send_to_novel", it) }
                } else {
                    Toast.makeText(this@NovelDetailActivity, "Failed to send gift", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialogBinding.rvGifts.layoutManager = GridLayoutManager(this, 3)
        dialogBinding.rvGifts.adapter = adapter

        dialogBinding.btnBuyGift.setOnClickListener {
            dialog.dismiss()
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

    private fun toggleLibrary() {
        val novel = currentNovel ?: return
        val id = novel.id ?: return
        val isInLibrary = novel.isInLibrary == "1"
        
        if (isInLibrary) {
            libraryViewModel.removeNovelFromServerLibrary(id)
            novel.isInLibrary = "0"
            Toast.makeText(this, "Removed from library", Toast.LENGTH_SHORT).show()
        } else {
            libraryViewModel.addNovelToServerLibrary(id)
            novel.isInLibrary = "1"
            Toast.makeText(this, "Added to library", Toast.LENGTH_SHORT).show()
        }
        updateLibraryUI()
    }

    private fun updateLibraryUI() {
        val novel = currentNovel ?: return
        val isInLibrary = novel.isInLibrary == "1"
        
        val primaryColor = getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
        val goldColor = getColorFromAttr(R.attr.novel_app_gold_accent)
        
        if (isInLibrary) {
            binding.tvLibraryText.text = "In Library"
            binding.ivLibraryIcon.imageTintList = ColorStateList.valueOf(goldColor)
            binding.tvLibraryText.setTextColor(goldColor)
            outline(binding.btnLibrary, goldColor, Color.TRANSPARENT, radiusDp = 12f, strokePx = strokePx(1.5f))
        } else {
            binding.tvLibraryText.text = "Library"
            binding.ivLibraryIcon.imageTintList = ColorStateList.valueOf(primaryColor)
            binding.tvLibraryText.setTextColor(primaryColor)
            outline(binding.btnLibrary, primaryColor, Color.TRANSPARENT, radiusDp = 12f, strokePx = strokePx(1.5f))
        }
    }

    private fun strokePx(dp: Float) = (dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    /** Rounded, outlined background as a plain GradientDrawable (replaces MaterialCardView strokes). */
    private fun outline(view: View, strokeColor: Int, fillColor: Int, radiusDp: Float, strokePx: Int) {
        val shape = view.background as? android.graphics.drawable.GradientDrawable
            ?: android.graphics.drawable.GradientDrawable().also { view.background = it }
        shape.cornerRadius = radiusDp * resources.displayMetrics.density
        shape.setColor(fillColor)
        shape.setStroke(strokePx, strokeColor)
    }

    private fun getColorFromAttr(@AttrRes attrColor: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrColor, typedValue, true)
        return typedValue.data
    }

    private fun shareNovel() {
        val novel = currentNovel ?: return
        val novelId = novel.id?.takeIf { it.isNotBlank() } ?: return
        val shareUrl = DeepLinkRouter.novelLink(novelId)
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, decodeHtmlToTextV2(novel.title))
            putExtra(Intent.EXTRA_TEXT, "Check out this amazing novel: ${decodeHtmlToTextV2(novel.title)}\n\nRead here: $shareUrl")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Novel"))
    }

    private fun showSortDialog() {
        val options = listOf(
            OptionsDialog.Option("Oldest to Newest", "Start from Chapter 1", R.drawable.ic_sort_oldest),
            OptionsDialog.Option("Newest to Oldest", "Latest chapters first", R.drawable.ic_sort_newest)
        )
        OptionsDialog.show(this, "Sort Chapters", options, if (currentOrder == "ASC") 0 else 1) { which ->
            currentOrder = if (which == 0) "ASC" else "DESC"
            refreshChapters()
        }
    }

    private fun showPageSelectionDialog() {
        if (totalPages <= 0) return
        OptionsDialog.showGrid(this, "Go to Page", totalPages, currentPage, subtitle = "$totalPages pages of chapters") { page ->
            jumpToPage(page)
        }
    }

    private fun updatePaginationUI() {
        binding.tvPageIndicator.text = "$currentPage / $totalPages"
        binding.btnPrevPage.isEnabled = currentPage > 1
        binding.btnNextPage.isEnabled = currentPage < totalPages
        binding.btnPrevPage.alpha = if (currentPage > 1) 1.0f else 0.5f
        binding.btnNextPage.alpha = if (currentPage < totalPages) 1.0f else 0.5f
        
        binding.layoutPagination.visibility = if (totalPages > 1) View.VISIBLE else View.GONE
    }

    private fun loadNovelDetails() {
        val id = novelId ?: return
        lifecycleScope.launch {
            val localNovel = novelViewModel.fetchSingleNovelById(id,"")
            localNovel?.let { displayNovel(it) }

            val novel = novelViewModel.fetchSingleNovelById(id, "detail")
            if (novel != null && novel.id != null) {
                displayNovel(novel)
                // Fetch gift history once novel details are loaded
                giftViewModel.fetchGiftHistory("send_to_novel", novel.id)
            }
        }
    }

    private fun refreshChapters() = jumpToPage(1)

    private fun loadChapters() {
        val id = novelId ?: return
        lifecycleScope.launch {
            chapterViewModel.fetchChapters(
                order = currentOrder,
                offset = currentPage,
                status = "published",
                pagination = currentPage,
                scrolling = false,
                extra = true,
                currentReading = "0",
                fromPage = "novel_detils_chapter"
            )
        }
    }

    private fun displayNovel(novel: Novel) {
        currentNovel = novel
        GlobalAppData.selectedNovel.value = novel

        binding.tvNovelTitle.text = DisplayText.of(novel.title)
        binding.tvToolbarTitle.text = DisplayText.of(novel.title)
        val authorName = novel.userName ?: novel.writer ?: "Unknown Author"
        binding.tvAuthor.text = "by ${DisplayText.of(authorName)}"
        
        // Update Status Badge
        if (!novel.isCompleted.isNullOrEmpty()) {
            binding.cardStatus.visibility = View.VISIBLE
            val completed = novel.isCompleted == "1"
            binding.tvStatus.text = if (completed) "Complete" else "Ongoing"
            val statusColor = ContextCompat.getColor(
                this,
                if (completed) R.color.green_500 else R.color.status_ongoing_red
            )
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.novel_detail_hero_text))
            outline(
                binding.cardStatus, statusColor,
                androidx.core.graphics.ColorUtils.setAlphaComponent(statusColor, 28),
                radiusDp = 6f, strokePx = strokePx(1f)
            )
        } else {
            binding.cardStatus.visibility = View.GONE
        }

        // Genre tags next to the status; the sub-category is skipped when it repeats the category.
        val category = DisplayText.of(novel.categoryName).takeIf { it.isNotBlank() }
        val subCategory = DisplayText.of(novel.subCategoryName)
            .takeIf { it.isNotBlank() && !it.equals(category, ignoreCase = true) }
        binding.tvCategory.text = category.orEmpty()
        binding.tvCategory.visibility = if (category != null) View.VISIBLE else View.GONE
        binding.tvSubCategory.text = subCategory.orEmpty()
        binding.tvSubCategory.visibility = if (subCategory != null) View.VISIBLE else View.GONE

        binding.tvRating.text = String.format("%.1f", novel.avgRating)
        
        binding.tvViews.text = formatCount(novel.view)
        
        binding.tvDescription.text = decodeHtmlToTextV2(novel.description)
        
        binding.tvChaptersCount.text = (novel.chapterCount ?: 0).toString()
        
        // Cover decoded at its on-screen 104x156dp as a hardware bitmap.
        val density = resources.displayMetrics.density
        ImageHelper.loadImage(
            this, novel.imageLink, binding.ivNovelCover, allowHardware = true,
            targetWidthPx = (104 * density).toInt(), targetHeightPx = (156 * density).toInt()
        )

        if ((novel.description?.length ?: 0) > 200) {
            binding.tvDescription.maxLines = 4
            binding.btnReadMore.visibility = View.VISIBLE
            binding.btnReadMore.setOnClickListener {
                if (binding.tvDescription.maxLines == 4) {
                    binding.tvDescription.maxLines = Int.MAX_VALUE
                    binding.btnReadMore.text = "Show Less"
                } else {
                    binding.tvDescription.maxLines = 4
                    binding.btnReadMore.text = "Read More"
                }
            }
        } else {
            binding.btnReadMore.visibility = View.GONE
        }

        if (!novel.lastReadChapterId.isNullOrEmpty()) {
            binding.tvReadNow.text = if (GlobalAppData.isAudioMode()) "Resume" else "Continue"
        } else {
            binding.tvReadNow.text = if (GlobalAppData.isAudioMode()) "Play" else "Read"
        }
        
        updateLibraryUI()
    }

    private fun formatCount(countStr: String?): String {
        val count = countStr?.toIntOrNull() ?: 0
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
}
