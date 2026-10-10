/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.DailyCheckInV2Response
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.DailyCheckInViewModel
import com.engine.core.viewmodel.UserViewModel
import com.writer_hub.novelserise.databinding.ActivityDailyBonusBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class DailyBonusActivity : BaseActivity() {

    private lateinit var binding: ActivityDailyBonusBinding
    private lateinit var userViewModel: UserViewModel
    private lateinit var dailyCheckInViewModel: DailyCheckInViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyBonusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)
        dailyCheckInViewModel = AppProvider.getViewModel(application, DailyCheckInViewModel::class.java)

        setupToolbar()
        setupListeners()
        observeViewModel()
        loadData()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadData()
        }

        binding.tvPointHistory.setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }

        binding.dailyBonusWidget.setViewModel(dailyCheckInViewModel)
        binding.dailyBonusWidget.setOnCheckInSuccessListener {
            dailyCheckInViewModel.fetchDailyCheckInData()
        }

        binding.cardEarnByAds.setOnClickListener {
            startActivity(Intent(this, EarnCoinsActivity::class.java))
        }
        binding.cardInvite.setOnClickListener {
            startActivity(Intent(this, ReferEarnActivity::class.java))
        }
        binding.cardGetDiamonds.setOnClickListener {
            startActivity(Intent(this, ShopActivity::class.java))
        }
    }

    /** Tomorrow's points and the next milestone day, straight from the check-in timeline. */
    private fun bindComingUp(data: DailyCheckInV2Response) {
        val timeline = data.data?.timeline.orEmpty()
        val todayIndex = timeline.indexOfFirst { it.dayName.equals("Today", ignoreCase = true) }
        val tomorrow = timeline.getOrNull(todayIndex + 1)
        if (todayIndex < 0 || tomorrow == null) {
            binding.cardComingUp.visibility = View.GONE
            return
        }
        binding.cardComingUp.visibility = View.VISIBLE
        binding.tvTomorrowPoints.text = "🪙 +${tomorrow.points}"

        val nextIndex = (todayIndex + 1 until timeline.size).firstOrNull { timeline[it].isMilestone }
        if (nextIndex == null) {
            binding.tvNextBonus.text = "—"
            binding.tvNextBonusDetail.text = "Keep your streak going"
        } else {
            val days = nextIndex - todayIndex
            val next = timeline[nextIndex]
            binding.tvNextBonus.text = if (days == 1) "Tomorrow" else "In $days days"
            binding.tvNextBonusDetail.text = "${shortDate(next.date)} · +${next.points} points"
        }
    }

    private fun shortDate(date: String): String = runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!
        // English month to match the rest of the screen, whatever the device language.
        SimpleDateFormat("MMM d", Locale.US).format(parsed)
    }.getOrDefault(date)

    private fun observeViewModel() {
        GlobalAppData.userDetailsLiveData.observe(this) { userDetails ->
            binding.tvPointCount.text = userDetails?.userData?.points ?: "0"
        }
        dailyCheckInViewModel.checkInData.observe(this) { data ->
            data?.let {
                binding.dailyBonusWidget.setData(it)
                bindComingUp(it)
            }
            showContent()
        }
    }

    private fun loadData() {
        if (!binding.swipeRefreshLayout.isRefreshing) {
            binding.nestedScrollView.visibility = View.GONE
            binding.shimmerViewContainer.visibility = View.VISIBLE
            binding.shimmerViewContainer.startShimmer()
        }

        lifecycleScope.launch {
            userViewModel.getUserDataDirect(true)
        }

        dailyCheckInViewModel.fetchDailyCheckInData()
    }

    private fun showContent() {
        binding.shimmerViewContainer.stopShimmer()
        binding.shimmerViewContainer.visibility = View.GONE
        binding.nestedScrollView.visibility = View.VISIBLE
        binding.swipeRefreshLayout.isRefreshing = false
    }
}
