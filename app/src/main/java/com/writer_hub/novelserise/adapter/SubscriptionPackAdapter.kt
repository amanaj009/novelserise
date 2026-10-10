/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.content.res.ColorStateList
import android.graphics.Paint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemSubscriptionPackBinding
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.models.WriterSubscriptionPack
import kotlin.math.roundToInt

class SubscriptionPackAdapter(
    private val onBuyClick: (WriterSubscriptionPack) -> Unit,
    private val onCancelClick: (WriterSubscriptionPack) -> Unit
) : RecyclerView.Adapter<SubscriptionPackAdapter.PackViewHolder>() {

    private val items = mutableListOf<WriterSubscriptionPack>()

    fun submitList(newItems: List<WriterSubscriptionPack>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
        val binding = ItemSubscriptionPackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PackViewHolder(private val binding: ItemSubscriptionPackBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WriterSubscriptionPack) {
            binding.packTitle.text = item.name

            val currentPrice = item.price
            val discountAmount = item.discountDouble

            val priceText = if (currentPrice % 1.0 == 0.0) {
                "₹${currentPrice.roundToInt()}"
            } else {
                "₹$currentPrice"
            }
            binding.packPrice.text = priceText

            if (discountAmount > 0) {
                val originalPrice = currentPrice + discountAmount
                val originalPriceText = if (originalPrice % 1.0 == 0.0) {
                    "₹${originalPrice.roundToInt()}"
                } else {
                    "₹$originalPrice"
                }

                binding.packOriginalPrice.text = originalPriceText
                binding.packOriginalPrice.visibility = View.VISIBLE
                binding.packOriginalPrice.paintFlags = binding.packOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.packOriginalPrice.visibility = View.GONE
            }

            if (discountAmount > 0 && !item.discountText.isNullOrEmpty()) {
                binding.discountTag.text = item.discountText
                binding.discountTag.visibility = View.VISIBLE
            } else {
                binding.discountTag.visibility = View.GONE
            }

            if (item.validityInt == 0) {
                binding.packDuration.text = "/ Lifetime"
            } else {
                binding.packDuration.text = "/ ${item.validityInt} Days"
            }

            // Always apply Premium Look to all packs as requested
            binding.cardRoot.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, itemView.resources.displayMetrics).toInt()
            binding.cardRoot.strokeColor = resolveColorAttr(R.attr.novel_app_gold_accent)
            binding.cardRoot.setCardBackgroundColor(resolveColorAttr(com.google.android.material.R.attr.colorSurfaceContainerLow))

            val isSubscribedToThisPack = GlobalVariableManager.isSpecificPackActive(item.id)

            if (isSubscribedToThisPack) {
                binding.btnBuyNow.text = "Subscribed"
                binding.btnBuyNow.isEnabled = false
                binding.btnBuyNow.backgroundTintList = ColorStateList.valueOf(
                    resolveColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                binding.btnBuyNow.setOnClickListener(null)
                binding.cardRoot.setOnClickListener(null)

                binding.subscribedInfoLayout.visibility = View.VISIBLE

                val activeSubscription = GlobalVariableManager.getActiveSubscriptionByPackId(item.id)
                val billingDate = activeSubscription?.endDate

                if (billingDate != null) {
                    binding.nextBillingDateText.text = "Valid until: $billingDate"
                    binding.nextBillingDateText.visibility = View.VISIBLE
                } else {
                    binding.nextBillingDateText.visibility = View.GONE
                }

                binding.btnCancel.setOnClickListener {
                    onCancelClick(item)
                }
            } else {
                binding.btnBuyNow.text = "Subscribe Now"
                binding.btnBuyNow.isEnabled = true
                binding.btnBuyNow.backgroundTintList = ColorStateList.valueOf(
                    resolveColorAttr(androidx.appcompat.R.attr.colorPrimary)
                )

                // Single click solution: Click on card or button should trigger purchase
                val buyClickListener = View.OnClickListener {
                    onBuyClick(item)
                }
                binding.btnBuyNow.setOnClickListener(buyClickListener)
                binding.cardRoot.setOnClickListener(buyClickListener)

                binding.subscribedInfoLayout.visibility = View.GONE
            }

            val benefitItems = item.benefit?.map { (key, value) ->
                value.copy(
                    title = formatKey(key),
                    iconResId = getIconForBenefit(key)
                )
            } ?: emptyList()

            binding.recyclerViewBenefits.layoutManager = LinearLayoutManager(itemView.context)
            binding.recyclerViewBenefits.adapter = BenefitAdapter(benefitItems)
            // Prevent benefits list from intercepting card clicks
            binding.recyclerViewBenefits.suppressLayout(true)
            binding.recyclerViewBenefits.isClickable = false
            binding.recyclerViewBenefits.isFocusable = false
        }

        private fun formatKey(key: String): String {
            return key.replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercaseChar() } }
        }

        private fun getIconForBenefit(key: String): Int {
            return when (key) {
                "create_audio" -> com.engine.core.R.drawable.ic_audio
                "create_video" -> com.engine.core.R.drawable.ic_video
                "upload_video_to_youtube" -> com.engine.core.R.drawable.ic_youtube
                "ai_story_parts_limit" -> com.engine.core.R.drawable.ic_ai
                else -> com.engine.core.R.drawable.ic_check
            }
        }

        private fun resolveColorAttr(@AttrRes attrRes: Int): Int {
            val typedValue = TypedValue()
            itemView.context.theme.resolveAttribute(attrRes, typedValue, true)
            return typedValue.data
        }
    }
}
