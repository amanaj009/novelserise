/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.GraphDataSet
import com.engine.core.models.Novel
import com.engine.core.models.NovelStatisticsModel
import com.engine.core.models.WoChapter
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel
import com.engine.core.viewmodel.WriterChapterViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.writer_hub.novelserise.adapter.WriterChapterAdapter
import com.writer_hub.novelserise.databinding.ActivityWriterChapterBinding
import com.writer_hub.novelserise.ui.custom.OptionsDialog
import com.writer_hub.novelserise.util.DisplayText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WriterChapterActivity : BaseActivity() {

    private val TAG = "WriterChapter"
    private lateinit var binding: ActivityWriterChapterBinding
    private lateinit var chapterViewModel: WriterChapterViewModel
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var adapter: WriterChapterAdapter
    private var novel: Novel? = null

    // Pagination state
    private var isLoading = false
    private var isLastPage = false
    private var currentPage = 0
    private val pageSize = 20

    // Sorting & Filtering state
    private var currentOrder = "DESC"
    private var currentStatus = "all"

    // To track which chapters have already started their "clear timer"
    private val trackedForCompletion = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriterChapterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get from GlobalAppData only
        novel = GlobalAppData.selectedNovel.value
        
        if (novel == null) {
            Log.e(TAG, "Novel is null, finishing activity")
            finish()
            return
        }

        chapterViewModel = AppProvider.getViewModel(application, WriterChapterViewModel::class.java)
        novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)
        novel?.id?.let { chapterViewModel.setSelectedNovelId(it) }

        setupUI()
        setupRecyclerView()
        setupListeners()
        
        fetchChapters(isInitialLoad = true)
        observeViewModel()
        observeGlobalSelection()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.tvNovelTitle.text = DisplayText.of(novel?.title)
        renderFilterChips()
        // Use ImageHelper (Coil)
        ImageHelper.loadImage(this, novel?.imageLink, binding.ivNovelCover, R.drawable.shimmer_placeholder)
    }

    private fun setupRecyclerView() {
        adapter = WriterChapterAdapter(
            chapters = mutableListOf(),
            onToggleStatus = { chapter ->
                if (chapter.status != "published") {
                    if (validateChapter(chapter)) {
                        toggleChapterStatus(chapter)
                    } else {
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    toggleChapterStatus(chapter)
                }
            },
            onChapterClick = { chapter ->
                // Set to global data
                GlobalAppData.selectedChapter.value = chapter
                
                if (GlobalAppData.isAudioMode()) {
                    val intent = Intent(this, WriterAudioUploadActivity::class.java)
                    intent.putExtra("chapter", chapter)
                    startActivity(intent)
                } else {
                    val intent = Intent(this, ChapterWritingActivity::class.java)
                    startActivity(intent)
                }
            },
            onScheduleChapter = { chapter ->
                if (validateChapter(chapter)) {
                    showDateTimePicker(chapter)
                }
            },
            onViewStatistics = { chapter ->
                Toast.makeText(this, "Views: ${chapter.views}\nComments: ${chapter.totalComments}\nRatings: ${chapter.totalRatings}", Toast.LENGTH_LONG).show()
            },
            onViewComments = { chapter ->
                val novelId = novel?.id.orEmpty()
                val chapterId = chapter.id.orEmpty()
                if (novelId.isBlank() || chapterId.isBlank()) {
                    Toast.makeText(this, "Unable to open comments", Toast.LENGTH_SHORT).show()
                } else {
                    GlobalAppData.selectedChapter.value = chapter
                    val chapterTitle = decodeHtmlToTextV2(chapter.chapterName ?: "Chapter")
                    ChapterCommentsActivity.start(
                        context = this,
                        novelId = novelId,
                        chapterId = chapterId,
                        title = "Comments · $chapterTitle"
                    )
                }
            }
        )
        binding.rvChapters.layoutManager = LinearLayoutManager(this)
        binding.rvChapters.itemAnimator = null
        binding.rvChapters.adapter = adapter
    }
    
    private fun setupListeners() {
        binding.fabAddChapter.setOnClickListener {
            addNewChapter()
        }

        // Access via the include's ID
        binding.layoutEmpty.btnAddEmpty.setOnClickListener {
            Log.d(TAG, "Empty view Add button clicked")
            addNewChapter()
        }

        binding.nestedScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
            // Load the next page a little before the very bottom.
            if (scrollY >= v.getChildAt(0).measuredHeight - v.measuredHeight - 300) {
                if (!isLoading && !isLastPage) {
                    currentPage++
                    fetchChapters(isInitialLoad = false)
                }
            }
        })
        
        binding.btnFilter.setOnClickListener { showSortSheet() }
        binding.btnStats.setOnClickListener { fetchAndShowStats() }

        binding.chipAll.setOnClickListener { applyStatusFilter("all") }
        binding.chipPublished.setOnClickListener { applyStatusFilter("published") }
        binding.chipDrafts.setOnClickListener { applyStatusFilter("unpublished") }
    }

    private fun applyStatusFilter(status: String) {
        if (currentStatus == status) return
        currentStatus = status
        renderFilterChips()
        fetchChapters(isInitialLoad = true)
    }

    private fun renderFilterChips() {
        val muted = ContextCompat.getColor(this, R.color.v2_text_muted)
        val onPill = ContextCompat.getColor(this, R.color.v2_on_pill_dark)
        listOf(
            binding.chipAll to "all",
            binding.chipPublished to "published",
            binding.chipDrafts to "unpublished"
        ).forEach { (chip, status) ->
            val selected = status == currentStatus
            chip.setBackgroundResource(if (selected) R.drawable.v2_seg_selected else 0)
            chip.setTextColor(if (selected) onPill else muted)
        }
        binding.tvSortLabel.text = if (currentOrder == "ASC") "Oldest first" else "Newest first"
    }

    private fun showSortSheet() {
        OptionsDialog.show(
            context = this,
            title = "Sort chapters",
            options = listOf(
                OptionsDialog.Option("Newest first", "Latest chapter on top", R.drawable.ic_sort_newest),
                OptionsDialog.Option("Oldest first", "Start from chapter 1", R.drawable.ic_sort_oldest)
            ),
            selectedIndex = if (currentOrder == "ASC") 1 else 0
        ) { index ->
            currentOrder = if (index == 1) "ASC" else "DESC"
            renderFilterChips()
            fetchChapters(isInitialLoad = true)
        }
    }

    private fun observeViewModel() {
        chapterViewModel.scheduleStatus.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Chapter scheduled successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to schedule chapter", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeGlobalSelection() {
        // Observe changes to the selected chapter from GlobalAppData (for active editing)
        GlobalAppData.selectedChapter.observe(this) { updatedChapter ->
            updatedChapter?.let {
                Log.d(TAG, "Global selectedChapter updated: ${it.id}")
                adapter.updateChapter(it)
                updateNovelStats()
            }
        }
        
        // OBSERVE BACKGROUND PROGRESS MAP
        GlobalAppData.uploadProgressMap.observe(this) {
            // Efficiently refresh list to update progress bars
            adapter.notifyDataSetChanged()
        }

        // OBSERVE FINISHED PUBLISHING JOBS
        GlobalAppData.finishedPublishingMap.observe(this) { finishedMap ->
            finishedMap.forEach { (chapterId, updatedChapter) ->
                if (!trackedForCompletion.contains(chapterId)) {
                    Log.d(TAG, "Background publish finished for chapter: $chapterId")
                    trackedForCompletion.add(chapterId)
                    adapter.updateChapter(updatedChapter)
                    updateNovelStats()
                    clearFromBackgroundMaps(chapterId)
                }
            }
        }
        
        // OBSERVE CHAPTER SYNC STREAM
        GlobalAppData.chapterSync.observe(this) { isSyncNeeded ->
            if (isSyncNeeded == true) {
                GlobalAppData.chapterSync.postValue(false) // Reset
            }
        }
    }

    private fun clearFromBackgroundMaps(chapterId: String) {
        lifecycleScope.launch {
            delay(5000) // Keep 100% visible for 5 seconds
            
            // Clear Progress
            val progressMap = GlobalAppData.uploadProgressMap.value.orEmpty().toMutableMap()
            if (progressMap.containsKey(chapterId)) {
                progressMap.remove(chapterId)
                GlobalAppData.uploadProgressMap.postValue(progressMap)
            }
            
            // Clear Finished
            val finishedMap = GlobalAppData.finishedPublishingMap.value.orEmpty().toMutableMap()
            if (finishedMap.containsKey(chapterId)) {
                finishedMap.remove(chapterId)
                GlobalAppData.finishedPublishingMap.postValue(finishedMap)
            }

            // Remove from tracking so it can be handled again if needed (unlikely in same session)
            trackedForCompletion.remove(chapterId)
        }
    }

    private fun fetchChapters(isInitialLoad: Boolean) {
        val novelId = novel?.id ?: return
        if (isLoading) return

        isLoading = true
        Log.d(TAG, "Fetching chapters. initialLoad: $isInitialLoad, order: $currentOrder, status: $currentStatus")
        
        if (isInitialLoad) {
            currentPage = 0
            isLastPage = false
        }
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val chapters = chapterViewModel.fetchWriterChaptersDirectv3(
                    novelId = novelId,
                    order = currentOrder,
                    offset = currentPage * pageSize,
                    status = currentStatus
                )

                Log.d(TAG, "Fetched ${chapters.size} chapters")

                if (chapters.isNotEmpty()) {
                    if (isInitialLoad) {
                        adapter.updateData(chapters)
                    } else {
                        adapter.addChapters(chapters)
                    }
                } else {
                    if (isInitialLoad) {
                        adapter.updateData(emptyList()) // Clear list if no results on initial load
                        isLastPage = true
                    }
                }
                updateNovelStats()

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching chapters", e)
                Toast.makeText(this@WriterChapterActivity, "Failed to load chapters", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                isLoading = false
            }
        }
    }

    private fun updateNovelStats() {
        val count = adapter.itemCount
        Log.d(TAG, "updateNovelStats - Adapter item count: $count")
        val published = adapter.items.count { it.status == "published" }
        // Only part of the list may be loaded; the novel's own count covers the rest.
        val total = maxOf(count, novel?.chapterCount ?: 0)
        val chapterWord = if (total == 1) "chapter" else "chapters"
        binding.tvChapterStats.text = when {
            currentStatus == "published" -> "Showing published chapters"
            currentStatus == "unpublished" -> "Showing drafts"
            count == total && count > 0 -> "$total $chapterWord · $published published · ${count - published} drafts"
            else -> "$total $chapterWord"
        }
        binding.layoutEmpty.tvEmptyTitle.text = when (currentStatus) {
            "published" -> "Nothing published yet"
            "unpublished" -> "No drafts"
            else -> "No chapters yet"
        }
        
        if (count > 0) {
            Log.d(TAG, "HIDING layoutEmptyRoot and SHOWING rvChapters")
            binding.layoutEmptyRoot.visibility = View.GONE
            binding.rvChapters.visibility = View.VISIBLE
        } else {
            Log.d(TAG, "SHOWING layoutEmptyRoot and HIDING rvChapters")
            binding.layoutEmptyRoot.visibility = View.VISIBLE
            binding.rvChapters.visibility = View.GONE
        }
        
        // Log final visibility state for debugging
        Log.d(TAG, "layoutEmptyRoot final visibility: ${binding.layoutEmptyRoot.visibility}")
    }

    private fun addNewChapter() {
        lifecycleScope.launch {
            Log.d(TAG, "addNewChapter started")
            binding.creatingLoader.visibility = View.VISIBLE
            val newChapter = chapterViewModel.publishChapter()
            binding.creatingLoader.visibility = View.GONE
            
            if (newChapter != null) {
                Log.d(TAG, "New chapter received: ${newChapter.id}")
                adapter.addChapter(newChapter, atTop = true)
                Log.d(TAG, "Adapter count after add: ${adapter.itemCount}")
                
                // CRITICAL: Call visibility update
                updateNovelStats()
                
                // Ensure the list is scrolled to top to show the new item
                binding.rvChapters.scrollToPosition(0)
                binding.nestedScrollView.smoothScrollTo(0, 0)

                Toast.makeText(this@WriterChapterActivity, "New chapter added", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Failed to create new chapter")
            }
        }
    }

    private fun toggleChapterStatus(chapter: WoChapter) {
        val newStatus = if (chapter.status == "published") "unpublished" else "published"
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val resultStatus = chapterViewModel.changeChapterStatus(chapter.id, newStatus)
            binding.progressBar.visibility = View.GONE
            if (resultStatus == newStatus) {
                chapter.status = newStatus
                adapter.notifyDataSetChanged()
                Toast.makeText(this@WriterChapterActivity, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
                
                if (GlobalAppData.selectedChapter.value?.id == chapter.id) {
                    GlobalAppData.selectedChapter.value = chapter
                }
            } else {
                adapter.notifyDataSetChanged()
                Toast.makeText(this@WriterChapterActivity, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateChapter(chapter: WoChapter): Boolean {
        val wordLimit = GlobalAppData.siteSettings?.config?.wordLimit?.toIntOrNull() ?: 0
        val currentWordCount = chapter.wordsCount?.toIntOrNull() ?: 0
        
        // Skip word limit check for audio novels
        if (!GlobalAppData.isAudioMode()) {
            if (currentWordCount < wordLimit) {
                Toast.makeText(this, "Minimum $wordLimit words required to publish", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        
        if (GlobalAppData.isAudioMode()) {
            if (chapter.audioFilePath.isNullOrBlank()) {
                Toast.makeText(this, "Audio file is required for audio novels", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        
        return true
    }

    private fun showDateTimePicker(chapter: WoChapter) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val timePickerDialog = TimePickerDialog(this, { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val formattedDate = sdf.format(calendar.time)

                scheduleChapter(chapter, formattedDate)

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
            timePickerDialog.show()

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun scheduleChapter(chapter: WoChapter, scheduledDate: String) {
        val data = arrayOf(
            novel?.id,
            chapter.id,
            scheduledDate
        )
        chapterViewModel.scheduleChapter(data)
        
        if (GlobalAppData.selectedChapter.value?.id == chapter.id) {
            val updated = chapter.copy(isSchedule = "1", scheduleAt = scheduledDate)
            GlobalAppData.selectedChapter.value = updated
        }
    }

    private fun fetchAndShowStats() {
        val novelId = novel?.id ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // Fetch stats and graph data
                var stats: NovelStatisticsModel? = null
                var graphData: List<GraphDataSet>? = null
                
                stats = novelViewModel.fetchNovelStatistics(novelId = novelId)
                graphData = novelViewModel.fetchNovelGraphData(novelId, "views", "daily")

                if (stats != null) {
                    showStatsDialog(stats, graphData)
                } else {
                    Toast.makeText(this@WriterChapterActivity, "Failed to load statistics", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@WriterChapterActivity, "Error loading stats", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showStatsDialog(stats: NovelStatisticsModel, graphData: List<GraphDataSet>?) {
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_novel_stats, null)
        dialog.setContentView(dialogView)

        // Bind data
        dialogView.findViewById<TextView>(R.id.tvTotalViews).text = stats.views?.toString() ?: "0"
        dialogView.findViewById<TextView>(R.id.tvTotalComments).text = stats.comments?.toString() ?: "0"
        dialogView.findViewById<TextView>(R.id.tvRating).text = stats.ratings?.toString() ?: "0.0"
        dialogView.findViewById<TextView>(R.id.tvWritingPoints).text = stats.writingPoint?.toString() ?: "0"
        
        // Earnings
        dialogView.findViewById<TextView>(R.id.tvCoinsEarning).text = String.format("₹%.2f", stats.coinsEarning ?: 0.0)
        dialogView.findViewById<TextView>(R.id.tvAdsEarning).text = String.format("₹%.2f", stats.adsViewEarning ?: 0.0)
        
        // Graph status
        val graphStatusTv = dialogView.findViewById<TextView>(R.id.tvGraphStatus)
        if (graphData.isNullOrEmpty()) {
            graphStatusTv.text = "No graph data available."
        } else {
            val pointsCount = graphData.sumOf { it.points.size }
            graphStatusTv.text = "Graph data loaded: ${graphData.size} datasets with $pointsCount points."
        }
        
        dialogView.findViewById<View>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
