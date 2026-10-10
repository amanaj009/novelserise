/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import com.engine.core.manager.GlobalAppData
import ToastUtil
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.writer_hub.novelserise.adapter.GiftAdapter
import com.writer_hub.novelserise.databinding.ActivityRewardsBinding
import com.writer_hub.novelserise.databinding.BottomSheetBuyGiftBinding
import com.engine.core.models.Gift
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.GiftViewModel
import com.engine.core.helper.ImageHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RewardsActivity : BaseActivity() {

    private lateinit var binding: ActivityRewardsBinding
    private lateinit var giftViewModel: GiftViewModel
    private lateinit var freeGiftAdapter: GiftAdapter
    private lateinit var allGiftAdapter: GiftAdapter
    private var userDiamonds: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRewardsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        giftViewModel = AppProvider.getViewModel(application, GiftViewModel::class.java)

        setupToolbar()
        setupRecyclers()
        observeViewModel()
        fetchData()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMyGifts.setOnClickListener {
            startActivity(Intent(this@RewardsActivity, MyGiftsActivity::class.java))
        }
        // Coloured gift artwork from core (not the tinted app icon).
        binding.ivMyGifts.setImageResource(com.engine.core.R.drawable.ic_gift)
        binding.ivHeroGift.setImageResource(com.engine.core.R.drawable.ic_gift)
    }

    private fun setupRecyclers() {
        freeGiftAdapter = GiftAdapter(emptyList(), GiftAdapter.GiftMode.FREE) { gift ->
            giftViewModel.grabTodayFreeGift(gift)
        }
        binding.rvFreeGifts.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFreeGifts.adapter = freeGiftAdapter

        allGiftAdapter = GiftAdapter(emptyList(), GiftAdapter.GiftMode.STORE) { gift ->
            showBuyGiftDialog(gift)
        }
        binding.rvAllGifts.layoutManager = GridLayoutManager(this, 3)
        binding.rvAllGifts.adapter = allGiftAdapter
    }

    private fun showBuyGiftDialog(gift: Gift) {
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_NovelSerise_TransparentBottomSheet)
        val dialogBinding = BottomSheetBuyGiftBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        var quantity = 1
        val pricePerUnit = gift.buyingPrice ?: 0

        fun updateUI() {
            val total = quantity * pricePerUnit
            val affordable = total <= userDiamonds
            dialogBinding.tvQuantity.text = quantity.toString()
            dialogBinding.tvTotalDiamonds.text = "Total: 💎 $total"
            dialogBinding.btnBuy.isEnabled = affordable
            dialogBinding.btnBuy.text = if (affordable) "Buy for 💎 $total" else "Not enough diamonds"
            dialogBinding.btnMinus.alpha = if (quantity > 1) 1f else 0.4f
        }

        dialogBinding.tvGiftName.text = gift.name
        dialogBinding.tvPricePerUnit.text = "💎 $pricePerUnit each"
        dialogBinding.tvUserDiamonds.text = "Balance: 💎 $userDiamonds"
        
        ImageHelper.loadImage(this, gift.mediaFile, dialogBinding.ivGift)

        dialogBinding.btnPlus.setOnClickListener {
            quantity++
            updateUI()
        }

        dialogBinding.btnMinus.setOnClickListener {
            if (quantity > 1) {
                quantity--
                updateUI()
            }
        }

        dialogBinding.btnBuy.setOnClickListener {
            gift.counts = quantity
            giftViewModel.buyGiftWithDiamonds(gift) { success, message ->
                if (success) {
                    lifecycleScope.launch {
                        dialogBinding.lottieSuccess.visibility = View.VISIBLE
                        dialogBinding.lottieSuccess.playAnimation()
                        delay(2000)
                        dialog.dismiss()
                        ToastUtil.showToast(this@RewardsActivity, "Gift purchased!")
                    }
                } else {
                    ToastUtil.showToast(this@RewardsActivity, message ?: "Failed to purchase")
                }
            }
        }

        updateUI()
        dialog.show()
    }

    private fun observeViewModel() {
        giftViewModel.todayFreeGifts.observe(this) { gifts ->
            binding.shimmerFreeGifts.stopShimmer()
            binding.shimmerFreeGifts.visibility = View.GONE
            
            if (gifts.isNullOrEmpty()) {
                binding.rvFreeGifts.visibility = View.GONE
                binding.layoutNoFreeGifts.visibility = View.VISIBLE
            } else {
                binding.layoutNoFreeGifts.visibility = View.GONE
                binding.rvFreeGifts.visibility = View.VISIBLE
                freeGiftAdapter.updateData(gifts)
            }
        }

        giftViewModel.gifts.observe(this) { gifts ->
            binding.shimmerAllGifts.stopShimmer()
            binding.shimmerAllGifts.visibility = View.GONE
            
            val filteredGifts = gifts?.filter { (it.buyingPrice ?: 0) > 0 }
            
            if (filteredGifts.isNullOrEmpty()) {
                binding.rvAllGifts.visibility = View.GONE
                binding.layoutNoAllGifts.visibility = View.VISIBLE
            } else {
                binding.layoutNoAllGifts.visibility = View.GONE
                binding.rvAllGifts.visibility = View.VISIBLE
                allGiftAdapter.updateData(filteredGifts)
            }
        }

        giftViewModel.grabGiftResult.observe(this) { success ->
            if (success) {
                ToastUtil.showToast(this, "Gift grabbed!")
            }
        }

        GlobalAppData.userDetailsLiveData.observe(this) { userDetails ->
            userDiamonds = userDetails?.userData?.diamond?.toIntOrNull() ?: 0
            binding.tvDiamondBalance.text = "$userDiamonds diamonds"
        }
    }

    private fun fetchData() {
        lifecycleScope.launch {
            binding.shimmerFreeGifts.startShimmer()
            binding.shimmerAllGifts.startShimmer()
            giftViewModel.fetchTodayFreeGifts()
            giftViewModel.fetchGifts()
        }
    }
}