/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemMiniUserBinding
import com.engine.core.models.MiniUser
import com.engine.core.helper.ImageHelper

class MiniUserAdapter(
    private val users: List<MiniUser>,
    private val currentUserId: String?,
    private val onUserClick: (MiniUser) -> Unit
) : RecyclerView.Adapter<MiniUserAdapter.UserViewHolder>() {

    inner class UserViewHolder(val binding: ItemMiniUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemMiniUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.binding.apply {
            tvUserName.text = user.name ?: user.userName
            tvUserEmail.text = user.userName // Assuming userName is email or similar identifier

            // Use ImageHelper (Coil)
            ImageHelper.loadCircleImage(
                ivUserAvatar.context,
                user.avatar,
                ivUserAvatar,
                com.engine.core.R.drawable.ic_profile
            )

            ivCurrentAccount.visibility = if (user.userId == currentUserId) View.VISIBLE else View.GONE

            root.setOnClickListener {
                onUserClick(user)
            }
        }
    }

    override fun getItemCount(): Int = users.size
}
