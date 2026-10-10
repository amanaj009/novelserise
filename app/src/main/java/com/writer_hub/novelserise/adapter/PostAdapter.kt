/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.PostCommentActivity
import com.engine.core.utill.SocialConfig
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemPostBinding
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.helper.getTimeAgo
import com.engine.core.models.PostData
import com.engine.core.utill.ThemeUtils
import com.engine.core.viewmodel.PostViewModel
import com.engine.core.helper.ImageHelper
import kotlin.math.max

class PostAdapter(
    private val postViewModel: PostViewModel,
    private val posts: MutableList<PostData> = mutableListOf()
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    fun addPosts(newPosts: List<PostData>, clear: Boolean = false) {
        if (clear) {
            posts.clear()
        }
        val start = posts.size
        posts.addAll(newPosts)
        if (clear) {
            notifyDataSetChanged()
        } else {
            notifyItemRangeInserted(start, newPosts.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount(): Int = posts.size

    inner class PostViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: PostData) {
            val context = binding.root.context
            binding.tvPostUserName.text = decodeHtmlToTextV2(post.userData?.name ?: "Unknown")
            binding.tvPostTime.text = getTimeAgo(post.time)
            binding.tvPostText.text = decodeHtmlToTextV2(post.postText ?: "")
            
            // Use ImageHelper (Coil) to load user avatar
            ImageHelper.loadCircleImage(
                context,
                post.userData?.avatar,
                binding.ivPostUserAvatar,
                com.engine.core.R.drawable.ic_profile
            )

            if (!post.postFile.isNullOrEmpty()) {
                binding.ivPostImage.visibility = View.VISIBLE
                // Use ImageHelper (Coil) to load post image
                ImageHelper.loadImage(context, post.postFile, binding.ivPostImage)
            } else {
                binding.ivPostImage.visibility = View.GONE
            }

            binding.tvLikeCount.text = post.likeCount?.toString() ?: "0"
            binding.tvCommentCount.text = post.commentCount?.toString() ?: "0"

            // Update Like UI using icons from storymania library and theme colors
            val isLiked = post.isLiked == true

            val likeIcon = if (isLiked) com.engine.core.R.drawable.ic_thumb_up_liked
            else com.engine.core.R.drawable.ic_thumb_up_unlike
            binding.ivLike.setImageResource(likeIcon)
            
            val likeColorAttr = if (isLiked) androidx.appcompat.R.attr.colorPrimary 
                               else com.google.android.material.R.attr.colorOnSurfaceVariant
            val likeColor = ThemeUtils.getThemeColor(context, likeColorAttr)
            
            binding.ivLike.imageTintList = ColorStateList.valueOf(likeColor)
            binding.tvLikeCount.setTextColor(likeColor)

            binding.btnLike.setOnClickListener {
                post.id?.let { id ->
                    postViewModel.performPostAction(id, "like", 0)
                    
                    // Toggle locally for immediate feedback
                    val wasLiked = post.isLiked == true
                    post.isLiked = !wasLiked
                    val currentLikes = post.likeCount ?: 0
                    post.likeCount = if (post.isLiked == true) currentLikes + 1 else max(0, currentLikes - 1)
                    
                    notifyItemChanged(adapterPosition)
                }
            }

            binding.btnComment.setOnClickListener {
                post.id?.let { id ->
                    PostCommentActivity.start(context, id, post.commentCount?.toString() ?: "0")
                }
            }

            binding.btnShare.setOnClickListener {
                val baseUrl = SocialConfig.shareFaceLink.trimEnd('/')
                val backendUrl = post.postShare?.trim().orEmpty()
                val shareUrl = when {
                    backendUrl.startsWith("https://") || backendUrl.startsWith("http://") -> backendUrl
                    backendUrl.isNotEmpty() -> "$baseUrl/${backendUrl.trimStart('/')}"
                    !post.id.isNullOrBlank() -> "$baseUrl/post/${post.id}"
                    else -> ""
                }
                if (shareUrl.isNotEmpty()) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Check out this post: $shareUrl")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Post"))
                }
            }
        }
    }
}
