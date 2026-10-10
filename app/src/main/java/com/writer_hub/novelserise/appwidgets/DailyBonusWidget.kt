/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.appwidgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.writer_hub.novelserise.adapter.StreakDayAdapter
import com.writer_hub.novelserise.databinding.WidgetDailyBonusBinding
import com.engine.core.models.DailyCheckInV2Response
import com.engine.core.viewmodel.DailyCheckInViewModel

class DailyBonusWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: WidgetDailyBonusBinding = 
        WidgetDailyBonusBinding.inflate(LayoutInflater.from(context), this, true)

    private var viewModel: DailyCheckInViewModel? = null
    private var onCheckInSuccessListener: (() -> Unit)? = null

    private var todayPoints: Int = 0

    init {
        orientation = VERTICAL
        binding.recyclerViewDays.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerViewDays.itemAnimator = null
        binding.btnCheckIn.setOnClickListener { handleCheckIn() }
    }

    fun setViewModel(viewModel: DailyCheckInViewModel) {
        this.viewModel = viewModel
    }

    fun setOnCheckInSuccessListener(listener: () -> Unit) {
        this.onCheckInSuccessListener = listener
    }

    private fun handleCheckIn() {
        viewModel?.let { dailyCheckInViewModel ->
            showLoading()
            dailyCheckInViewModel.performCheckIn { success, message ->
                hideLoading()
                if (success) {
                    Toast.makeText(context, message ?: "Check-in successful!", Toast.LENGTH_SHORT).show()
                    updateButtonState(isEnabled = false)
                    onCheckInSuccessListener?.invoke()
                } else {
                    Toast.makeText(context, message ?: "Check-in failed.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun setData(data: DailyCheckInV2Response) {
        val streak = data.currentStreak
        binding.tvStreakCount.text = if (streak == 1) "1 day streak" else "$streak day streak"
        binding.tvStreakHint.text = when {
            data.isCheckedInToday -> "You're all set for today. See you tomorrow!"
            streak > 0 -> "Check in today to keep your streak alive"
            else -> "Check in daily to build a streak"
        }

        val timeline = data.data?.timeline.orEmpty()
        val adapter = StreakDayAdapter(timeline)
        binding.recyclerViewDays.adapter = adapter
        todayPoints = timeline.getOrNull(adapter.todayIndex)?.points ?: 0

        // Bring today to the middle of the strip.
        if (adapter.todayIndex >= 0) {
            binding.recyclerViewDays.post {
                val lm = binding.recyclerViewDays.layoutManager as? LinearLayoutManager ?: return@post
                val itemWidth = (60 * resources.displayMetrics.density).toInt()
                lm.scrollToPositionWithOffset(adapter.todayIndex, (binding.recyclerViewDays.width - itemWidth) / 2)
            }
        }
        updateButtonState(!data.isCheckedInToday)

        binding.tvBottomNote.text = "Milestone days pay a bonus — don't break the chain!"
    }

    private fun updateButtonState(isEnabled: Boolean) {
        binding.btnCheckIn.isEnabled = isEnabled
        binding.btnCheckIn.text = when {
            !isEnabled -> "✓  Checked in today"
            todayPoints > 0 -> "Check in  ·  +$todayPoints points"
            else -> "Check In"
        }
        binding.btnCheckIn.alpha = 1.0f
    }

    private fun showLoading() {
        binding.btnCheckIn.visibility = View.INVISIBLE
        binding.progressCheckIn.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.progressCheckIn.visibility = View.GONE
        binding.btnCheckIn.visibility = View.VISIBLE
    }
}
