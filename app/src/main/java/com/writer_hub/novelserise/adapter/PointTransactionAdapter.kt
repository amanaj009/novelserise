/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.models.PointTransactionItem
import com.google.android.material.color.MaterialColors
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemPointTransactionBinding

class PointTransactionAdapter(
    private var transactions: List<PointTransactionItem>
) : RecyclerView.Adapter<PointTransactionAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemPointTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPointTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = transactions[position]
        holder.binding.apply {
            tvTitle.text = item.description.ifEmpty { item.source.replace("_", " ").capitalize() }
            tvDate.text = item.createdAt
            
            if (item.isPositive) {
                tvPoints.text = "+${item.points}"
                // Using theme colors instead of direct XML colors
                val greenColor = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary)
                tvPoints.setTextColor(greenColor)
                ivTypeIcon.setImageResource(com.engine.core.R.drawable.ic_gift)
            } else {
                tvPoints.text = "-${item.points}"
                val redColor = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorError)
                tvPoints.setTextColor(redColor)
                ivTypeIcon.setImageResource(com.writer_hub.novelserise.R.drawable.ic_shop)
            }
        }
    }

    override fun getItemCount(): Int = transactions.size

    fun updateData(newTransactions: List<PointTransactionItem>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
}
