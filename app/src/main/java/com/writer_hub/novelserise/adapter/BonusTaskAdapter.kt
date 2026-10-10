/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.databinding.ItemBonusTaskBinding
import com.engine.core.models.UserTask
import com.engine.core.helper.ImageHelper

class BonusTaskAdapter(
    private val tasks: List<UserTask>,
    private val onTaskClick: (UserTask) -> Unit
) : RecyclerView.Adapter<BonusTaskAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemBonusTaskBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBonusTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        holder.binding.apply {
            tvTaskTitle.text = task.title
            tvTaskSubtitle.text = task.subtitle
            tvReward.text = "🪙 +${task.coinReward} points"
            
            // Use ImageHelper (Coil) to load task icon
            ImageHelper.loadImage(ivTaskIcon.context, task.icon, ivTaskIcon)

            val isCompleted = task.userTaskHistory?.isCompleted == "1"
            if (isCompleted) {
                layoutReward.visibility = View.GONE
                ivCompleted.visibility = View.VISIBLE
                root.alpha = 0.75f
            } else {
                layoutReward.visibility = View.VISIBLE
                ivCompleted.visibility = View.GONE
                root.alpha = 1.0f
            }

            btnTaskAction.setOnClickListener { onTaskClick(task) }
            root.setOnClickListener { if (!isCompleted) onTaskClick(task) }
        }
    }

    override fun getItemCount(): Int = tasks.size
}
