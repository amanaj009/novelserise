/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.writer_hub.novelserise.databinding.ActivityNovelStatsBinding
import com.engine.core.models.NovelStatisticsModel
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel
import kotlinx.coroutines.launch

class NovelStatsActivity : BaseActivity() {

    private lateinit var binding: ActivityNovelStatsBinding
    private lateinit var novelViewModel: NovelViewModel
    private var novelId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovelStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        novelId = intent.getStringExtra("NOVEL_ID")
        
        setupToolbar()
        
        if (novelId != null) {
            novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)
            fetchStats()
        } else {
            Toast.makeText(this, "Novel ID missing", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.tvTitle.text = "Novel Statistics"
        binding.toolbar.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun fetchStats() {
        binding.shimmerViewContainer.startShimmer()
        binding.shimmerViewContainer.visibility = View.VISIBLE
        binding.mainContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val stats = novelViewModel.fetchNovelStatistics(novelId = novelId)
                val graphData = novelViewModel.fetchNovelGraphData(novelId!!, "views", "daily")

                if (stats != null) {
                    displayStats(stats)
                }
                
                if (graphData != null && graphData.isNotEmpty()) {
                     binding.simpleGraphView.setData(graphData.firstOrNull())
                }

                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                binding.mainContent.visibility = View.VISIBLE

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@NovelStatsActivity, "Error loading stats", Toast.LENGTH_SHORT).show()
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
            }
        }
    }

    private fun displayStats(stats: NovelStatisticsModel) {
        binding.tvTotalViews.text = stats.views?.toString() ?: "0"
        binding.tvTotalComments.text = stats.comments?.toString() ?: "0"
        binding.tvRating.text = stats.ratings?.toString() ?: "0.0"
        binding.tvWritingPoints.text = stats.writingPoint?.toString() ?: "0"
        binding.tvCoinsEarning.text = String.format("₹%.2f", stats.coinsEarning ?: 0.0)
        binding.tvAdsEarning.text = String.format("₹%.2f", stats.adsViewEarning ?: 0.0)
    }
}
