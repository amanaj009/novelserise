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
import com.writer_hub.novelserise.databinding.ItemDiamondPackBinding
import com.engine.core.models.DiamondPack

class DiamondPackAdapter(
    private val packs: List<DiamondPack>,
    private val onBuyClick: (DiamondPack) -> Unit
) : RecyclerView.Adapter<DiamondPackAdapter.DiamondViewHolder>() {

    private fun DiamondPack.hasBonus() = bonus.isNotEmpty() && bonus != "0"

    /** Reserve the bonus line only when some pack has one, so tiles stay compact otherwise. */
    private val anyBonus = packs.any { it.hasBonus() }

    /** Pack with the most diamonds per rupee (factual "best rate"), used when no pack is flagged as an offer. */
    private val bestRateId: String? = if (packs.size < 3 || packs.any { it.isOffer }) null else
        packs.maxByOrNull { p ->
            val amount = p.amount.toDoubleOrNull() ?: 0.0
            if (amount > 0) (p.diamonds.toDoubleOrNull() ?: 0.0) / amount else 0.0
        }?.id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiamondViewHolder {
        val binding = ItemDiamondPackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DiamondViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DiamondViewHolder, position: Int) {
        holder.bind(packs[position])
    }

    override fun getItemCount(): Int = packs.size

    inner class DiamondViewHolder(private val binding: ItemDiamondPackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var pack: DiamondPack? = null

        init {
            // Tile or price pill both start the purchase.
            val buy = View.OnClickListener { pack?.let(onBuyClick) }
            binding.cardRoot.setOnClickListener(buy)
            binding.btnBuy.setOnClickListener(buy)
        }

        fun bind(pack: DiamondPack) {
            this.pack = pack
            binding.tvDiamondCount.text = pack.diamonds

            if (pack.hasBonus()) {
                binding.tvBonus.visibility = View.VISIBLE
                binding.tvBonus.text = "+${pack.bonus} bonus"
            } else {
                // INVISIBLE keeps tiles in a row the same height when other packs show a bonus.
                binding.tvBonus.visibility = if (anyBonus) View.INVISIBLE else View.GONE
            }

            binding.btnBuy.text = "₹${pack.amount.removeSuffix(".00")}"

            val highlight = pack.isOffer || pack.id == bestRateId
            binding.cardRoot.setBackgroundResource(if (highlight) R.drawable.bg_shop_pack_offer else R.drawable.bg_shop_pack)
            binding.tvHot.text = if (pack.isOffer) "HOT DEAL" else "BEST RATE"
            binding.tvHot.visibility = if (highlight) View.VISIBLE else View.GONE
        }
    }
}
