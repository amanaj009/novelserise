/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.models.DailyCheckInTimelineItem
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemStreakDayBinding

/**
 * Horizontal check-in strip. Each day shows its weekday, the points it pays (or a check once
 * claimed) and the date; today is highlighted and milestone days carry a BONUS tag.
 */
class StreakDayAdapter(
    private val days: List<DailyCheckInTimelineItem>
) : RecyclerView.Adapter<StreakDayAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemStreakDayBinding) : RecyclerView.ViewHolder(binding.root)

    /** Index of today in [days], or -1. */
    val todayIndex: Int = days.indexOfFirst { it.dayName.equals("Today", ignoreCase = true) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStreakDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val day = days[position]
        val isToday = position == todayIndex
        val onSurface = holder.binding.tvDayNumber.context.let { ctx ->
            val tv = android.util.TypedValue()
            ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true)
            tv.data
        }

        holder.binding.apply {
            tvDayNumber.text = day.date.substringAfterLast('-').trimStart('0').ifEmpty { "${position + 1}" }
            tvWeekday.text = if (isToday) "TODAY" else day.dayName.take(3).uppercase()
            // A missed (or zero-point) day has nothing to claim.
            tvPoints.text = if (day.status == "missed" || day.points <= 0) "✕" else "+${day.points}"
            tvBonus.visibility = if (day.isMilestone) View.VISIBLE else View.GONE

            val checked = day.status == "checked"
            val missed = day.status == "missed"
            ivChecked.visibility = if (checked) View.VISIBLE else View.GONE
            tvPoints.visibility = if (checked) View.GONE else View.VISIBLE

            coin.setBackgroundResource(
                when {
                    checked -> R.drawable.bg_coin_checked
                    missed -> R.drawable.bg_coin_missed
                    else -> R.drawable.bg_coin
                }
            )
            tvPoints.setTextColor(if (missed) MISSED_TEXT else COIN_TEXT)

            cardDay.setBackgroundResource(
                when {
                    isToday -> R.drawable.bg_day_today
                    checked -> R.drawable.bg_day_checked
                    day.isMilestone -> R.drawable.bg_day_milestone
                    else -> R.drawable.bg_day_default
                }
            )
            val textColor = if (isToday) Color.WHITE else onSurface
            tvDayNumber.setTextColor(textColor)
            tvWeekday.setTextColor(if (isToday) Color.WHITE else WEEKDAY_TEXT)
            cardDay.alpha = if (missed) 0.55f else 1f
        }
    }

    override fun getItemCount(): Int = days.size

    private companion object {
        val COIN_TEXT = Color.parseColor("#6B4400")
        val MISSED_TEXT = Color.parseColor("#99FFFFFF")
        val WEEKDAY_TEXT = Color.parseColor("#9CA3AF")
    }
}
