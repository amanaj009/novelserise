/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.models.WriterSubscriptionPack
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemWriterPackBinding
import com.writer_hub.novelserise.util.DisplayText
import kotlin.math.roundToInt

class WriterPackAdapter(
    private val onBuyClick: (WriterSubscriptionPack) -> Unit,
    private val onCancelClick: (WriterSubscriptionPack) -> Unit
) : RecyclerView.Adapter<WriterPackAdapter.PackViewHolder>() {

    private val items = mutableListOf<WriterSubscriptionPack>()

    /** Pack highlighted with the gold "BEST VALUE" look: the server's featured pack, else the longest one. */
    private var bestValueId: Int? = null

    fun submitList(newItems: List<WriterSubscriptionPack>) {
        items.clear()
        items.addAll(newItems)
        bestValueId = when {
            items.size < 2 -> null
            else -> (items.firstOrNull { it.isFeaturedInt == 1 }
                ?: items.maxByOrNull { if (it.validityInt == 0) Int.MAX_VALUE else it.validityInt })?.id
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
        val binding = ItemWriterPackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PackViewHolder(private val binding: ItemWriterPackBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.packOriginalPrice.paintFlags = binding.packOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        fun bind(item: WriterSubscriptionPack) {
            binding.packTitle.text = item.name

            val currentPrice = item.price
            val discountAmount = item.discountDouble

            binding.packPrice.text = rupees(currentPrice)

            if (discountAmount > 0) {
                binding.packOriginalPrice.text = rupees(currentPrice + discountAmount)
                binding.packOriginalPrice.visibility = View.VISIBLE
            } else {
                binding.packOriginalPrice.visibility = View.GONE
            }

            if (discountAmount > 0 && !item.discountText.isNullOrEmpty()) {
                binding.discountTag.text = item.discountText
                binding.discountTag.visibility = View.VISIBLE
            } else {
                binding.discountTag.visibility = View.GONE
            }

            val validity = item.validityInt
            binding.packDuration.text = if (validity == 0) "/ lifetime" else "/ $validity days"

            // Long plans read better as a monthly price.
            if (validity >= 60 && currentPrice > 0) {
                binding.packPerMonth.text = "Just ${rupees((currentPrice * 30 / validity).roundToInt().toDouble())} / month"
                binding.packPerMonth.visibility = View.VISIBLE
            } else {
                binding.packPerMonth.visibility = View.GONE
            }

            val description = item.description?.let { DisplayText.of(it).toString() }
            binding.packDescription.text = description
            binding.packDescription.visibility = if (description.isNullOrBlank()) View.GONE else View.VISIBLE

            val isBestValue = item.id == bestValueId
            binding.cardRoot.setBackgroundResource(if (isBestValue) R.drawable.bg_shop_card_featured else R.drawable.bg_shop_card)
            binding.tvBestValue.visibility = if (isBestValue) View.VISIBLE else View.GONE

            bindBenefits(item)

            val isSubscribedToThisPack = GlobalVariableManager.isSpecificPackActive(item.id)

            if (isSubscribedToThisPack) {
                binding.btnBuyNow.text = "✓  Active plan"
                binding.btnBuyNow.isEnabled = false
                binding.btnBuyNow.setBackgroundResource(R.drawable.bg_shop_button)
                binding.btnBuyNow.setTextColor(Color.parseColor("#10B981"))
                binding.btnBuyNow.setOnClickListener(null)
                binding.cardRoot.setOnClickListener(null)
                binding.cardRoot.isClickable = false

                binding.subscribedInfoLayout.visibility = View.VISIBLE
                val billingDate = GlobalVariableManager.getActiveSubscriptionByPackId(item.id)?.endDate
                binding.nextBillingDateText.text = billingDate?.let { "Valid until $it" }.orEmpty()
                binding.btnCancel.setOnClickListener { onCancelClick(item) }
            } else {
                binding.btnBuyNow.text = if (isBestValue) "Get Best Value" else "Subscribe Now"
                binding.btnBuyNow.isEnabled = true
                binding.btnBuyNow.setBackgroundResource(if (isBestValue) R.drawable.bg_shop_button_gold else R.drawable.bg_shop_button)
                binding.btnBuyNow.setTextColor(if (isBestValue) Color.parseColor("#3A2500") else Color.WHITE)

                // Click on card or button triggers purchase
                val buyClickListener = View.OnClickListener { onBuyClick(item) }
                binding.btnBuyNow.setOnClickListener(buyClickListener)
                binding.cardRoot.setOnClickListener(buyClickListener)

                binding.subscribedInfoLayout.visibility = View.GONE
            }
        }

        private fun bindBenefits(item: WriterSubscriptionPack) {
            val container = binding.benefitsContainer
            container.removeAllViews()
            val benefits = item.benefit
                ?.map { (key, benefit) ->
                    benefit.title?.takeIf { it.isNotBlank() }
                        ?: benefit.description?.takeIf { it.isNotBlank() }
                        ?: formatKey(key)
                }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    "All premium content unlocked",
                    "Unlimited reading and listening",
                    "Ad-free experience"
                )
            container.visibility = if (benefits.isEmpty()) View.GONE else View.VISIBLE
            if (benefits.isEmpty()) return

            val context = container.context
            val density = context.resources.displayMetrics.density
            val onSurface = binding.packTitle.currentTextColor
            benefits.forEach { text ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (3 * density).toInt(), 0, (3 * density).toInt())
                }
                row.addView(FrameLayout(context).apply {
                    setBackgroundResource(R.drawable.bg_shop_check)
                    addView(ImageView(context).apply {
                        setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_shop_check))
                    }, FrameLayout.LayoutParams((12 * density).toInt(), (12 * density).toInt(), Gravity.CENTER))
                }, LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt()))
                row.addView(TextView(context).apply {
                    this.text = text
                    textSize = 14f
                    setTextColor(onSurface)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = (10 * density).toInt() })
                container.addView(row)
            }
        }

        private fun rupees(value: Double): String =
            if (value % 1.0 == 0.0) "₹${value.roundToInt()}" else "₹$value"

        private fun formatKey(key: String): String {
            return key.replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercaseChar() } }
        }
    }
}
