/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.GiftViewModel
import com.writer_hub.novelserise.adapter.ReceivedGiftAdapter
import com.writer_hub.novelserise.databinding.ActivityNovelGiftsBinding

class NovelGiftsActivity : BaseActivity() {

    private lateinit var binding: ActivityNovelGiftsBinding
    private lateinit var giftViewModel: GiftViewModel
    private lateinit var giftAdapter: ReceivedGiftAdapter
    private var novelId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovelGiftsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        novelId = intent.getStringExtra("NOVEL_ID")

        giftViewModel = AppProvider.getViewModel(application, GiftViewModel::class.java)

        setupToolbar()
        setupRecycler()
        observeViewModel()
        fetchData()
    }

    private fun setupToolbar() {
        binding.toolbar.tvTitle.text = "Gifts Received"
        binding.toolbar.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecycler() {
        giftAdapter = ReceivedGiftAdapter()
        binding.rvGifts.layoutManager = LinearLayoutManager(this)
        binding.rvGifts.adapter = giftAdapter
    }

    private fun observeViewModel() {
        giftViewModel.giftHistory.observe(this) { history ->
            binding.shimmerGifts.stopShimmer()
            binding.shimmerGifts.visibility = View.GONE
            
            if (history.isNullOrEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvGifts.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvGifts.visibility = View.VISIBLE
                giftAdapter.submitList(history)
            }
        }
    }

    private fun fetchData() {
        val id = novelId ?: return
        binding.shimmerGifts.startShimmer()
        giftViewModel.fetchGiftHistory("send_to_novel", id)
    }
}
