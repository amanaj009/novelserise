/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemPostCommentBinding
import com.engine.core.AppCore.tokenManager
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.helper.getTimeAgo
import com.engine.core.models.CommentData
import com.engine.core.helper.ImageHelper

class PostCommentAdapter(
    private val onLikeClick: (CommentData) -> Unit,
    private val onReplyClick: (CommentData) -> Unit,
    private val onEditClick: (CommentData) -> Unit,
    private val onDeleteClick: (CommentData) -> Unit,
    private val onUserClick: (String) -> Unit
) : ListAdapter<CommentData, PostCommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemPostCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(private val binding: ItemPostCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: CommentData) {
            val context = binding.root.context
            binding.tvUserName.text = decodeHtmlToTextV2(comment.publisher?.name ?: "Unknown User")
            binding.tvComment.text = decodeHtmlToTextV2(comment.text ?: "")
            binding.tvTime.text = comment.commentTime?.let { getTimeAgo(it) }
            binding.tvLikeCount.text = comment.commentLikes ?: "0"
            
            val repliesCountInt = comment.repliesCount?.toIntOrNull() ?: 0
            binding.tvReplyCount.text = if (repliesCountInt > 0) "Replies ($repliesCountInt)" else "Reply"

            val avatarUrl = comment.publisher?.avatar
            if (!avatarUrl.isNullOrEmpty()) {
                ImageHelper.loadImage(context, avatarUrl, binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(com.engine.core.R.drawable.placholder_profile)
            }

            // Using icons from storymania library
            binding.ivLike.setImageResource(
                if (comment.isCommentLiked == true) com.engine.core.R.drawable.ic_thumb_up_liked
                else com.engine.core.R.drawable.ic_thumb_up_unlike
            )

            val (_, currentUserId) = tokenManager.getTokenAndUserId()
            val isOwner = currentUserId != null && currentUserId == comment.publisher?.userId

            binding.ivMenu.setOnClickListener { view ->
                val popup = PopupMenu(context, view)
                popup.menuInflater.inflate(R.menu.comment_options_menu, popup.menu)
                
                popup.menu.findItem(R.id.action_edit_comment)?.isVisible = isOwner
                popup.menu.findItem(R.id.action_delete_comment)?.isVisible = isOwner
                popup.menu.findItem(R.id.action_report_comment)?.isVisible = !isOwner

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit_comment -> {
                            onEditClick(comment)
                            true
                        }
                        R.id.action_delete_comment -> {
                            onDeleteClick(comment)
                            true
                        }
                        R.id.action_report_comment -> {
                            // Handle report
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }

            binding.ivLike.setOnClickListener { onLikeClick(comment) }
            binding.ivReply.setOnClickListener { onReplyClick(comment) }
            binding.tvReplyCount.setOnClickListener { onReplyClick(comment) }
            binding.ivAvatar.setOnClickListener { comment.publisher?.userId?.let { onUserClick(it) } }
            binding.tvUserName.setOnClickListener { comment.publisher?.userId?.let { onUserClick(it) } }
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<CommentData>() {
        override fun areItemsTheSame(oldItem: CommentData, newItem: CommentData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CommentData, newItem: CommentData): Boolean {
            return oldItem == newItem && oldItem.isCommentLiked == newItem.isCommentLiked && oldItem.commentLikes == newItem.commentLikes
        }
    }
}
