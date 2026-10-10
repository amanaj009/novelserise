/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.ImageHelper
import com.engine.core.models.GiftHistoryItem
import com.writer_hub.novelserise.databinding.ItemSupporterPreviewBinding

class SupporterAdapter(
    private var supporters: List<GiftHistoryItem>,
    private val onItemClick: (GiftHistoryItem) -> Unit
) : RecyclerView.Adapter<SupporterAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemSupporterPreviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GiftHistoryItem, onItemClick: (GiftHistoryItem) -> Unit) {
            binding.tvSupporterName.text = item.sender?.name ?: "User"
            ImageHelper.loadCircleImage(binding.root.context, item.sender?.avatar, binding.ivSupporterAvatar)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSupporterPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(supporters[position], onItemClick)
    }

    override fun getItemCount(): Int = supporters.size

    fun updateData(newData: List<GiftHistoryItem>) {
        supporters = newData
        notifyDataSetChanged()
    }
}
