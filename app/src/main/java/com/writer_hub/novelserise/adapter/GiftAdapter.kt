/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.databinding.ItemGiftBinding
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.models.Gift
import com.engine.core.helper.ImageHelper

class GiftAdapter(
    private var gifts: List<Gift>,
    private val mode: GiftMode,
    private val onActionClick: (Gift) -> Unit
) : RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    enum class GiftMode {
        FREE, STORE, OWNED
    }

    class GiftViewHolder(val binding: ItemGiftBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val binding = ItemGiftBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        
        // Set fixed width for horizontal scrolling items (FREE mode)
        if (mode == GiftMode.FREE) {
            val density = parent.context.resources.displayMetrics.density
            val widthInPx = (120 * density).toInt() // fixed width in the horizontal row
            val marginInPx = (5 * density).toInt()
            
            val layoutParams = RecyclerView.LayoutParams(widthInPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(marginInPx, marginInPx, marginInPx, marginInPx)
            }
            binding.root.layoutParams = layoutParams
        }

        return GiftViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        val gift = gifts[position]
        holder.binding.apply {
            tvGiftName.text = decodeHtmlToTextV2(gift.name)
            
            when (mode) {
                GiftMode.FREE -> {
                    tvPrice.text = "FREE"
                    tvPrice.setTextColor(FREE_COLOR)
                    btnGrabOrBuy.visibility = View.VISIBLE
                    val isGrabbed = gift.isGrabbed == 1
                    btnGrabOrBuy.text = if (isGrabbed) "✓ Grabbed" else "Grab"
                    btnGrabOrBuy.isEnabled = !isGrabbed
                    btnGrabOrBuy.alpha = if (isGrabbed) 0.7f else 1f
                    
                    // Use theme-compliant colors implicitly via enabled state or explicitly if needed
                    // MaterialButton handles this well when using style and ?attr/colorPrimary
                    
                    btnGrabOrBuy.setOnClickListener { onActionClick(gift) }
                    root.setOnClickListener(null)
                    root.isClickable = false
                }
                GiftMode.STORE -> {
                    tvPrice.text = "💎 ${gift.buyingPrice ?: 0}"
                    tvPrice.setTextColor(tvGiftName.currentTextColor)
                    btnGrabOrBuy.visibility = View.VISIBLE
                    btnGrabOrBuy.text = "Buy"
                    btnGrabOrBuy.isEnabled = true
                    btnGrabOrBuy.alpha = 1f
                    btnGrabOrBuy.setOnClickListener { onActionClick(gift) }
                    root.setOnClickListener(null)
                    root.isClickable = false
                }
                GiftMode.OWNED -> {
                    tvPrice.text = "Owned × ${gift.counts}"
                    tvPrice.setTextColor(FREE_COLOR)
                    btnGrabOrBuy.visibility = View.GONE
                    btnGrabOrBuy.setOnClickListener(null)
                    root.setOnClickListener { onActionClick(gift) }
                    root.isClickable = true
                }
            }

            // Use ImageHelper (Coil) to load gift image
            ImageHelper.loadImage(ivGift.context, gift.mediaFile, ivGift)
        }
    }

    override fun getItemCount(): Int = gifts.size

    private companion object {
        val FREE_COLOR = Color.parseColor("#10B981")
    }

    fun updateData(newGifts: List<Gift>) {
        gifts = newGifts
        notifyDataSetChanged()
    }
}
