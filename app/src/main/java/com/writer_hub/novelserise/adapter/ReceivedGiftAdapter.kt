/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.getTimeAgo
import com.engine.core.models.GiftHistoryItem
import com.writer_hub.novelserise.databinding.ItemReceivedGiftBinding
import com.engine.core.helper.ImageHelper
import com.writer_hub.novelserise.UserDetailsActivity

class ReceivedGiftAdapter : ListAdapter<GiftHistoryItem, ReceivedGiftAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReceivedGiftBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemReceivedGiftBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GiftHistoryItem) {
            val senderName = item.sender?.name ?: "User"
            val giftName = item.giftDetails?.name ?: "Gift"
            val quantity = item.quantity ?: 1
            
            val fullText = "$senderName sent $giftName x$quantity"
            val spannable = SpannableStringBuilder(fullText)
            
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                senderName.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            binding.tvNotificationText.text = spannable
            binding.tvTime.text = getTimeAgo(item.time)

            ImageHelper.loadCircleImage(binding.ivSenderAvatar.context, item.sender?.avatar, binding.ivSenderAvatar)
            ImageHelper.loadImage(binding.ivGiftIcon.context, item.giftDetails?.mediaFile, binding.ivGiftIcon)

            val onUserClick = {
                item.sender?.userId?.let { userId ->
                    UserDetailsActivity.start(binding.root.context, userId)
                }
            }

            binding.root.setOnClickListener { onUserClick() }
            binding.ivSenderAvatar.setOnClickListener { onUserClick() }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<GiftHistoryItem>() {
        override fun areItemsTheSame(oldItem: GiftHistoryItem, newItem: GiftHistoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GiftHistoryItem, newItem: GiftHistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
