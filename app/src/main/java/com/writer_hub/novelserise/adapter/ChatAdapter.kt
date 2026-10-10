/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.models.GetAllMessageModel
import com.engine.core.models.UserData
import com.engine.core.utill.ThemeUtils
import com.writer_hub.novelserise.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val context: Context,
    private var messages: MutableList<GetAllMessageModel>,
    private val currentUserId: String,
    private val onUserNameClick: (UserData) -> Unit
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageContainer: LinearLayout = view.findViewById(R.id.messageContainer)
        private val messageWrapper: LinearLayout = view.findViewById(R.id.messageWrapper)
        private val userName: TextView = view.findViewById(R.id.userName)
        private val textViewMessage: TextView = view.findViewById(R.id.textViewMessage)
        private val imageViewMessage: ImageView = view.findViewById(R.id.imageViewMessage)
        private val textViewSendTime: TextView = view.findViewById(R.id.textViewSendTime)
        private val textViewSeen: TextView = view.findViewById(R.id.textViewSeen)
        private val leftEmptySpace: View = view.findViewById(R.id.leftEmptySpace)
        private val rightEmptySpace: View = view.findViewById(R.id.rightEmptySpace)

        fun bind(message: GetAllMessageModel, isLastMessage: Boolean) {
            val isSentByMe = message.fromId == currentUserId

            if (message.text.isNullOrEmpty() && !message.media.isNullOrEmpty()) {
                textViewMessage.visibility = View.GONE
                imageViewMessage.visibility = View.VISIBLE
                // Use ImageHelper (Coil) to load message media
                ImageHelper.loadImage(itemView.context, message.media, imageViewMessage)
            } else {
                textViewMessage.text = decodeHtmlToTextV2(message.text)
                textViewMessage.visibility = View.VISIBLE
                imageViewMessage.visibility = View.GONE
            }

            val groupId = message.groupId?.toIntOrNull()
            if ((groupId != null && groupId > 0) && !isSentByMe) {
                userName.text = message.messageUser?.name ?: ""
                userName.visibility = View.VISIBLE
                userName.setOnClickListener {
                    message.messageUser?.let { user -> onUserNameClick(user) }
                }
            } else {
                userName.visibility = View.GONE
            }
            
            if (isSentByMe) {
                leftEmptySpace.visibility = View.VISIBLE
                rightEmptySpace.visibility = View.GONE
                messageContainer.gravity = Gravity.END
                messageWrapper.background = ContextCompat.getDrawable(context, R.drawable.bg_chat_bubble_sent)
                
                val sentColor = ThemeUtils.getThemeColor(context, com.google.android.material.R.attr.colorOnPrimary)
                textViewMessage.setTextColor(sentColor)
                // Use colorOnPrimary for time on sent messages to avoid blending with the dark bubble background
                textViewSendTime.setTextColor(sentColor)
                textViewSendTime.alpha = 0.8f // Slightly dim the time for visual hierarchy
                userName.setTextColor(sentColor)
                textViewMessage.setLinkTextColor(sentColor)

            } else {
                leftEmptySpace.visibility = View.GONE
                rightEmptySpace.visibility = View.VISIBLE
                messageContainer.gravity = Gravity.START
                messageWrapper.background = ContextCompat.getDrawable(context, R.drawable.bg_chat_bubble_received)
                
                val receivedColor = ThemeUtils.getThemeColor(context, android.R.attr.textColorPrimary)
                val secondaryTextColor =
                    ThemeUtils.getThemeColor(context, android.R.attr.textColorSecondary)
                
                textViewMessage.setTextColor(receivedColor)
                textViewSendTime.setTextColor(secondaryTextColor)
                textViewSendTime.alpha = 1.0f
                userName.setTextColor(ThemeUtils.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary))
                textViewMessage.setLinkTextColor(ThemeUtils.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary))
            }
            
            updateStatus(message, isLastMessage, isSentByMe)
        }
        
        private fun updateStatus(message: GetAllMessageModel, isLastMessage: Boolean, isSentByMe: Boolean) {
            textViewSendTime.text = formatTime(message.time)

            if (isSentByMe) {
                textViewSeen.visibility = if (isLastMessage) {
                    when {
                        "0".equals(message.isSending) -> {
                            textViewSeen.text = "Sending"
                            View.VISIBLE
                        }
                        "1".equals(message.seen) -> {
                            textViewSeen.text = "Seen"
                            View.VISIBLE
                        }
                        else -> {
                            textViewSeen.text = "Sent"
                            View.VISIBLE
                        }
                    }
                } else View.GONE
            } else {
                textViewSeen.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val isLastMessage = position == 0
        holder.bind(message, isLastMessage)
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: GetAllMessageModel) {
        messages.add(0, message)
        notifyItemInserted(0)
        if (messages.size > 1) {
            notifyItemChanged(1)
        }
    }

    fun setMessages(newMessages: List<GetAllMessageModel>) {
        val diffCallback = MessageDiffCallback(this.messages, newMessages)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        this.messages.clear()
        this.messages.addAll(newMessages)
        diffResult.dispatchUpdatesTo(this)
    }
    
    private fun formatTime(timeMillis: String?): String {
        if (timeMillis.isNullOrEmpty()) return ""
        return try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = Date(timeMillis.toLong())
            sdf.format(date)
        } catch (e: Exception) {
            ""
        }
    }

    class MessageDiffCallback(
        private val oldList: List<GetAllMessageModel>,
        private val newList: List<GetAllMessageModel>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return (oldItem.id != null && oldItem.id == newItem.id) || 
                   (oldItem.localId != null && oldItem.localId == newItem.localId)
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
