/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemHomeBannerBinding
import com.engine.core.models.HomepageNovelLite
import com.engine.core.helper.ImageHelper

class HomeBannerAdapter(private val novels: List<HomepageNovelLite>) :
    RecyclerView.Adapter<HomeBannerAdapter.BannerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val binding = ItemHomeBannerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BannerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        holder.bind(novels[position])
    }

    override fun getItemCount(): Int = novels.size

    class BannerViewHolder(private val binding: ItemHomeBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(novel: HomepageNovelLite) {
            val bannerUrl = novel.bannerImage?.takeIf { it.isNotBlank() }
                ?: novel.middleBannerImage?.takeIf { it.isNotBlank() }
                ?: novel.imageLinkHome?.takeIf { it.isNotBlank() }
                ?: novel.imageLinkCover?.takeIf { it.isNotBlank() }
                ?: novel.imageLink

            // Hardware bitmap decoded at the banner's on-screen size (full width, 16:9): the wide
            // banner is drawn from GPU memory on every carousel frame.
            val width = binding.root.resources.displayMetrics.widthPixels
            ImageHelper.loadImage(
                binding.root.context, bannerUrl, binding.ivBanner, R.drawable.shimmer_placeholder, allowHardware = true,
                targetWidthPx = width, targetHeightPx = (width * 0.5625f).toInt()
            )

            binding.root.setOnClickListener {
                novel.id?.let { id ->
                    NovelDetailActivity.start(it.context, id)
                }
            }
        }
    }
}
