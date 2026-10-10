/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.adapter.HomeSectionNovelByIdAdapter
import com.writer_hub.novelserise.databinding.ActivityHomeSectionNovelByIdBinding
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.models.HomepageNovelLite
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel
import kotlinx.coroutines.launch

class HomeSectionNovelByIdActivity : BaseActivity() {

    private lateinit var binding: ActivityHomeSectionNovelByIdBinding
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var novelAdapter: HomeSectionNovelByIdAdapter

    private var sectionId: Int = 0
    private var sectionKey: String = ""
    private var sectionTitle: String = ""

    private var offset = 0
    private val limit = 21
    private var isLoading = false
    private var hasMore = true

    companion object {
        private const val EXTRA_SECTION_ID = "extra_section_id"
        private const val EXTRA_SECTION_KEY = "extra_section_key"
        private const val EXTRA_SECTION_TITLE = "extra_section_title"

        fun start(context: Context, sectionId: Int, title: String, key: String = "") {
            val intent = Intent(context, HomeSectionNovelByIdActivity::class.java).apply {
                putExtra(EXTRA_SECTION_ID, sectionId)
                putExtra(EXTRA_SECTION_TITLE, title)
                putExtra(EXTRA_SECTION_KEY, key)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeSectionNovelByIdBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root)

        sectionId = intent.getIntExtra(EXTRA_SECTION_ID, 0)
        sectionKey = intent.getStringExtra(EXTRA_SECTION_KEY) ?: ""
        sectionTitle = intent.getStringExtra(EXTRA_SECTION_TITLE) ?: "Novels"

        novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        loadNovels(refresh = true)

        binding.swipeRefresh.setOnRefreshListener {
            loadNovels(refresh = true)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = sectionTitle
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        novelAdapter = HomeSectionNovelByIdAdapter()
        binding.rvNovels.apply {
            layoutManager = LinearLayoutManager(this@HomeSectionNovelByIdActivity)
            adapter = novelAdapter
            
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && hasMore) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                        ) {
                            loadNovels(refresh = false)
                        }
                    }
                }
            })
        }
    }

    private fun loadNovels(refresh: Boolean) {
        if (isLoading) return
        
        // If both are missing, we can't load anything.
        if (sectionId == 0 && sectionKey.isEmpty()) {
            if (refresh) {
                binding.layoutEmpty.root.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = false
            }
            return
        }

        isLoading = true

        if (refresh) {
            offset = 0
            hasMore = true
            binding.shimmerView.visibility = View.VISIBLE
            binding.shimmerView.startShimmer()
            binding.rvNovels.visibility = View.GONE
            binding.layoutEmpty.root.visibility = View.GONE
        } else {
            binding.progressBarLoadMore.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val novels = if (sectionId != 0) {
                    val section = novelViewModel.fetchHomeSectionById(sectionId, limit, offset)
                    section?.novels ?: emptyList()
                } else {
                    // Fallback to fetching by key/type
                    val fetchedNovels = novelViewModel.fetchNovelsByCategoryAndSubcategory(
                        categoryId = "0",
                        subcategoryId = "0",
                        type = sectionKey,
                        offset = offset,
                        limit = limit,
                        timeSlot = "0",
                        resourceId = "0"
                    )
                    // Convert Novel to HomepageNovelLite
                    fetchedNovels.map { novel ->
                        HomepageNovelLite(
                            id = novel.id ?: "",
                            isPremium = novel.isPremium ?: "0",
                            title = novel.title ?: "",
                            description = novel.description ?: "",
                            imageLinkCover = novel.imageLinkCover ?: novel.imageLink,
                            imageLink = novel.imageLink,
                            chapterCount = novel.chapterCount?.toString(),
                            avgRating = novel.avgRating.toString(),
                            viewCount = novel.views ?: novel.view,
                            authorName = novel.userName ?: novel.writer,
                            categoryName = novel.categoryName
                        )
                    }
                }

                isLoading = false
                binding.swipeRefresh.isRefreshing = false
                binding.shimmerView.stopShimmer()
                binding.shimmerView.visibility = View.GONE
                binding.progressBarLoadMore.visibility = View.GONE

                if (novels.isNotEmpty()) {
                    val newList = if (refresh) novels else novelAdapter.currentList + novels
                    novelAdapter.submitList(newList)
                    binding.rvNovels.visibility = View.VISIBLE
                    
                    offset += novels.size
                    hasMore = novels.size >= limit
                } else {
                    hasMore = false
                    if (refresh && novelAdapter.itemCount == 0) {
                        binding.layoutEmpty.root.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                AppLoggerHelper.e("HomeSectionNovelById", "Error loading novels", e)
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
                binding.shimmerView.stopShimmer()
                binding.shimmerView.visibility = View.GONE
                binding.progressBarLoadMore.visibility = View.GONE
                if (refresh && novelAdapter.itemCount == 0) {
                    binding.layoutEmpty.root.visibility = View.VISIBLE
                }
            }
        }
    }
}
