package com.writer_hub.novelserise.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.getTimeAgo
import com.engine.core.models.Notification
import com.engine.core.utill.ThemeUtils
import com.engine.core.viewmodel.MyNotificationViewModel
import com.writer_hub.novelserise.databinding.ItemNotificationBinding
import com.writer_hub.novelserise.helper.NotificationHandler
import com.writer_hub.novelserise.helper.generateNotificationText

class NotificationAdapter(
    private val viewModel: MyNotificationViewModel
) : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: Notification) {
            val context = binding.root.context
            binding.tvNotificationText.text = generateNotificationText(notification)
            binding.tvTime.text = notification.time?.let { getTimeAgo(it) }

            val avatarUrl = notification.userData?.avatar
            if (!avatarUrl.isNullOrEmpty()) {
                ImageHelper.loadImage(context, avatarUrl, binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(com.engine.core.R.drawable.placholder_profile)
            }

            val isUnseen = notification.seen == "0"
            binding.unreadDot.visibility = if (isUnseen) View.VISIBLE else View.GONE
            
            if (isUnseen) {
                binding.tvNotificationText.setTypeface(null, Typeface.BOLD)
                binding.mainLayout.setBackgroundResource(com.writer_hub.novelserise.R.drawable.bg_notif_unread)
            } else {
                binding.tvNotificationText.setTypeface(null, Typeface.NORMAL)
                binding.mainLayout.setBackgroundResource(com.writer_hub.novelserise.R.drawable.bg_notif_read)
            }

            binding.root.setOnClickListener {
                if (notification.seen == "0") {
                    notification.id?.let { viewModel.changeSeenStatus(it) }
                    notification.seen = "1"
                    notifyItemChanged(adapterPosition)
                }
                NotificationHandler.handleNotificationClick(context, notification)
            }
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem && oldItem.seen == newItem.seen
        }
    }
}
