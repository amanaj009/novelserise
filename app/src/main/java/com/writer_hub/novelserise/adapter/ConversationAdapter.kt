/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemChatConversationBinding
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.models.GetAllChatsModel
import com.engine.core.utill.ThemeUtils
import com.engine.core.helper.ImageHelper
import java.util.concurrent.TimeUnit

class ConversationAdapter(
    private var chats: List<GetAllChatsModel>,
    private val onChatClick: (GetAllChatsModel) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemChatConversationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = chats[position]
        val context = holder.itemView.context
        
        holder.binding.tvName.text = decodeHtmlToTextV2(chat.name ?: "Unknown")

        // Use lastMessage for text and time
        val lastMessage = chat.lastMessage
        holder.binding.tvLastMessage.text = decodeHtmlToTextV2(lastMessage?.text ?: chat.text ?: "No messages")
        holder.binding.tvTime.text = getTimeAgo(lastMessage?.time)

        // Set colors for read/unread state
        val isUnread = lastMessage?.position != "right" && lastMessage?.seen == "0"

        // Use textColorSecondary for read messages for better visibility in light mode
        val readColor = ThemeUtils.getThemeColor(context, android.R.attr.textColorSecondary)
        val unreadColor = ThemeUtils.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)

        if (isUnread) {
            holder.binding.tvLastMessage.setTextColor(unreadColor)
            holder.binding.tvLastMessage.setTypeface(null, Typeface.BOLD)
            holder.binding.tvTime.setTextColor(unreadColor)
            holder.binding.tvTime.setTypeface(null, Typeface.BOLD)
        } else {
            holder.binding.tvLastMessage.setTextColor(readColor)
            holder.binding.tvLastMessage.setTypeface(null, Typeface.NORMAL)
            holder.binding.tvTime.setTextColor(readColor)
            holder.binding.tvTime.setTypeface(null, Typeface.NORMAL)
        }
        
        // Unread count badge
        val count = chat.messageCount?.toIntOrNull() ?: 0
        if (count > 0) {
            holder.binding.tvUnreadCount.visibility = View.VISIBLE
            holder.binding.tvUnreadCount.text = if (count > 99) "99+" else count.toString()
        } else {
            holder.binding.tvUnreadCount.visibility = View.GONE
        }

        // Verified Icon
        holder.binding.ivVerified.visibility = if (chat.Verified == "1") View.VISIBLE else View.GONE
        
        // Use ImageHelper (Coil) to load avatar
        ImageHelper.loadCircleImage(
            context,
            chat.avatar,
            holder.binding.ivAvatar,
            com.engine.core.R.drawable.ic_profile
        )

        holder.itemView.setOnClickListener {
            onChatClick(chat)
        }
    }

    override fun getItemCount() = chats.size

    fun updateChats(newChats: List<GetAllChatsModel>) {
        chats = newChats
        notifyDataSetChanged()
    }
    
    private fun getTimeAgo(time: String?): String {
        if (time.isNullOrEmpty()) return ""
        try {
            // Assuming time is in seconds, convert to milliseconds
            val timeMillis = time.toLong() * 1000
            val now = System.currentTimeMillis()
            val diff = now - timeMillis

            val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val days = TimeUnit.MILLISECONDS.toDays(diff)

            return when {
                seconds < 60 -> "just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days < 7 -> "${days}d ago"
                else -> {
                    val weeks = days / 7
                    "${weeks}w ago"
                }
            }
        } catch (e: Exception) {
            return ""
        }
    }
}
