/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.os.Bundle
import android.view.View
import com.writer_hub.novelserise.adapter.GiftAdapter
import com.writer_hub.novelserise.databinding.ActivityMyGiftsBinding
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.GiftViewModel

class MyGiftsActivity : BaseActivity() {

    private lateinit var binding: ActivityMyGiftsBinding
    private lateinit var giftViewModel: GiftViewModel
    private lateinit var giftAdapter: GiftAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyGiftsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        giftViewModel = AppProvider.getViewModel(application, GiftViewModel::class.java)

        setupToolbar()
        setupRecycler()
        observeViewModel()
        fetchData()
    }

    private fun setupToolbar() {
        binding.toolbar.tvTitle.text = "My Gifts"
        binding.toolbar.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecycler() {
        giftAdapter = GiftAdapter(emptyList(), GiftAdapter.GiftMode.OWNED) { }
        binding.rvMyGifts.adapter = giftAdapter
    }

    private fun observeViewModel() {
        giftViewModel.userGifts.observe(this) { gifts ->
            binding.shimmerMyGifts.stopShimmer()
            binding.shimmerMyGifts.visibility = View.GONE
            
            if (gifts.isNullOrEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvMyGifts.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvMyGifts.visibility = View.VISIBLE
                giftAdapter.updateData(gifts)
            }
        }
    }

    private fun fetchData() {
        binding.shimmerMyGifts.startShimmer()
        giftViewModel.fetchUserGifts(50, 0, true)
    }
}
