/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.getTimeAgo
import com.engine.core.models.ChapterComment
import com.engine.core.utill.ThemeUtils
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemChapterCommentBinding

/**
 * Adapter for displaying comments on a chapter.
 */
class ChapterCommentAdapter(
    private val onReplyClick: (ChapterComment) -> Unit,
    private val onLikeClick: (ChapterComment) -> Unit,
    private val onViewRepliesClick: (ChapterComment) -> Unit,
    private val onEditClick: ((ChapterComment) -> Unit)? = null,
    private val onReportClick: ((ChapterComment) -> Unit)? = null
) : RecyclerView.Adapter<ChapterCommentAdapter.CommentViewHolder>() {

    private val comments = mutableListOf<ChapterComment>()
    private var replyingToCommentId: String? = null

    fun setComments(newComments: List<ChapterComment>) {
        comments.clear()
        comments.addAll(newComments)
        notifyDataSetChanged()
    }
    
    fun getComments(): List<ChapterComment> = comments

    fun addComments(newComments: List<ChapterComment>) {
        val start = comments.size
        comments.addAll(newComments)
        notifyItemRangeInserted(start, newComments.size)
    }

    fun addCommentAtTop(comment: ChapterComment) {
        comments.add(0, comment)
        notifyItemInserted(0)
    }
    
    fun updateComment(comment: ChapterComment) {
        val index = comments.indexOfFirst { it.id == comment.id }
        if (index != -1) {
            comments[index] = comment
            notifyItemChanged(index)
        }
    }

    fun incrementReplyCount(commentId: String) {
        val index = comments.indexOfFirst { it.id == commentId }
        if (index != -1) {
            val comment = comments[index]
            val currentCount = comment.replyCount.toIntOrNull() ?: 0
            comment.replyCount = (currentCount + 1).toString()
            notifyItemChanged(index)
        }
    }

    fun setReplyingTo(commentId: String?) {
        val oldId = replyingToCommentId
        replyingToCommentId = commentId
        
        if (oldId != null) {
            val oldIndex = comments.indexOfFirst { it.id == oldId }
            if (oldIndex != -1) notifyItemChanged(oldIndex)
        }
        
        if (commentId != null) {
            val newIndex = comments.indexOfFirst { it.id == commentId }
            if (newIndex != -1) notifyItemChanged(newIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemChapterCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(private val binding: ItemChapterCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: ChapterComment) {
            val context = binding.root.context
            val isMyComment = comment.isMy == "1"
            
            binding.tvUsername.text = if (isMyComment) "You" else (comment.user?.userName ?: "Unknown User")
            binding.tvComment.text = if (!comment.reply.isNullOrEmpty()) comment.reply else comment.comment
            
            binding.tvTime.text = getTimeAgo(comment.createdAt)
            binding.tvLikeCount.text = comment.commentLikes
            binding.tvReplyCount.text = comment.replyCount

            val rating = comment.user_rating?.toFloatOrNull() ?: 0f
            if (rating > 0) {
                binding.ratingLayout.visibility = View.VISIBLE
                binding.ratingBar.rating = rating
            } else {
                binding.ratingLayout.visibility = View.GONE
            }

            // Highlight logic: Only change background color, removed strokes
            if (comment.id == replyingToCommentId) {
                binding.root.setBackgroundColor(ThemeUtils.getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant))
            } else {
                if (isMyComment) {
                    binding.root.setBackgroundColor(ThemeUtils.getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant))
                    binding.root.setOnClickListener { onEditClick?.invoke(comment) }
                    binding.tvComment.setOnClickListener { onEditClick?.invoke(comment) }
                } else {
                    binding.root.setBackgroundResource(android.R.color.transparent)
                    binding.root.setOnClickListener(null)
                    binding.tvComment.setOnClickListener(null)
                    
                    binding.root.setOnLongClickListener {
                         onReportClick?.invoke(comment)
                         true
                    }
                }
            }

            val avatar = comment.user?.avatar
            ImageHelper.loadCircleImage(binding.ivAvatar.context, avatar, binding.ivAvatar, R.drawable.default_avatar)

            val likeIcon = if (comment.isCommentLiked) {
                com.engine.core.R.drawable.ic_thumb_up_liked
            } else {
                com.engine.core.R.drawable.ic_thumb_up_unlike
            }
            binding.ivLike.setImageResource(likeIcon)

            if (comment.isCommentLiked) {
                 ImageViewCompat.setImageTintList(binding.ivLike, null)
            } else {
                 val color = resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
                 ImageViewCompat.setImageTintList(binding.ivLike, ColorStateList.valueOf(color))
            }

            binding.ivLike.setOnClickListener { onLikeClick(comment) }
            binding.ivReply.setOnClickListener { onReplyClick(comment) }
            
            val replyCount = comment.replyCount.toIntOrNull() ?: 0
            if (replyCount > 0) {
                binding.btnViewReplies.visibility = View.VISIBLE
                binding.btnViewReplies.text = "View all $replyCount replies"
                binding.btnViewReplies.setOnClickListener { onViewRepliesClick(comment) }
            } else {
                binding.btnViewReplies.visibility = View.GONE
            }
        }
        
        private fun resolveColor(context: android.content.Context, @AttrRes attrRes: Int): Int {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(attrRes, typedValue, true)
            return if (typedValue.resourceId != 0) {
                androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }
    }
}