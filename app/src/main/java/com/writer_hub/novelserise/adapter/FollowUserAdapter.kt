/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemFollowUserBinding
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.models.UserData
import com.engine.core.helper.ImageHelper

class FollowUserAdapter(
    private var users: List<UserData>,
    private val onUserClick: (UserData) -> Unit
) : RecyclerView.Adapter<FollowUserAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFollowUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFollowUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        val context = holder.itemView.context

        holder.binding.tvName.text = decodeHtmlToTextV2(user.name)
        holder.binding.tvFollowersCount.text = "${user.details?.followerCount ?: "0"} Followers"

        ImageHelper.loadImage(
            context,
            user.avatar,
            holder.binding.ivAvatar,
            com.engine.core.R.drawable.ic_profile
        )

        holder.itemView.setOnClickListener {
            onUserClick(user)
        }
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<UserData>) {
        users = newUsers
        notifyDataSetChanged()
    }
}
