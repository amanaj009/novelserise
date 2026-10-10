/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.adapter.ViewAllNovelsAdapter
import com.writer_hub.novelserise.databinding.ActivityViewAllNovelsBinding
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel
import kotlinx.coroutines.launch

class ViewAllNovelsActivity : BaseActivity() {

    private lateinit var binding: ActivityViewAllNovelsBinding
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var novelAdapter: ViewAllNovelsAdapter

    private var type: String = ""
    private var userId: String = ""
    private var title: String = ""

    private var offset = 0
    private val limit = 21
    private var isLoading = false
    private var hasMore = true

    companion object {
        private const val EXTRA_TYPE = "extra_type"
        private const val EXTRA_USER_ID = "extra_user_id"
        private const val EXTRA_TITLE = "extra_title"

        const val TYPE_CONTINUE_READING = "continue_reading"
        const val TYPE_WRITER_NOVELS = "writer_novels"

        fun start(context: Context, type: String, userId: String, title: String) {
            val intent = Intent(context, ViewAllNovelsActivity::class.java).apply {
                putExtra(EXTRA_TYPE, type)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewAllNovelsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        type = intent.getStringExtra(EXTRA_TYPE) ?: ""
        userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        title = intent.getStringExtra(EXTRA_TITLE) ?: "Novels"

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
        supportActionBar?.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        novelAdapter = ViewAllNovelsAdapter()
        binding.rvNovels.apply {
            layoutManager = GridLayoutManager(this@ViewAllNovelsActivity, 3)
            adapter = novelAdapter
            
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val layoutManager = recyclerView.layoutManager as GridLayoutManager
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
        isLoading = true

        if (refresh) {
            offset = 0
            hasMore = true
            binding.shimmerView.visibility = View.VISIBLE
            binding.shimmerView.startShimmer()
            binding.rvNovels.visibility = View.GONE
            binding.layoutEmpty.visibility = View.GONE
        } else {
            binding.progressBarLoadMore.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            val novels = when (type) {
                TYPE_CONTINUE_READING -> {
                    novelViewModel.fetchNovelsByCategoryAndSubcategory(
                        "0", "0", "getcontinuereading", offset, limit, "0", "0", "0"
                    )
                }
                TYPE_WRITER_NOVELS -> {
                    novelViewModel.fetchNovelsByUserId(userId, limit, offset, "all") ?: emptyList()
                }
                else -> emptyList()
            }

            isLoading = false
            binding.swipeRefresh.isRefreshing = false
            binding.shimmerView.stopShimmer()
            binding.shimmerView.visibility = View.GONE
            binding.progressBarLoadMore.visibility = View.GONE

            if (novels.isNotEmpty()) {
                if (refresh) {
                    novelAdapter.updateData(novels)
                    binding.rvNovels.visibility = View.VISIBLE
                } else {
                    novelAdapter.addData(novels)
                }
                offset += novels.size
                hasMore = novels.size >= limit
            } else {
                hasMore = false
                if (refresh && novelAdapter.itemCount == 0) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                }
            }
        }
    }
}
