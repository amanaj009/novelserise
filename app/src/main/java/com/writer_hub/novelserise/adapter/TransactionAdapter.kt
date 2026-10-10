/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.helper.getTimeAgo
import com.engine.core.models.Transaction
import com.engine.core.utill.ThemeUtils
import com.writer_hub.novelserise.databinding.ItemTransactionBinding

class TransactionAdapter(
    private val transactions: MutableList<Transaction> = mutableListOf()
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    fun addTransactions(newTransactions: List<Transaction>, clear: Boolean = false) {
        if (clear) {
            transactions.clear()
        }
        val start = transactions.size
        transactions.addAll(newTransactions)
        if (clear) {
            notifyDataSetChanged()
        } else {
            notifyItemRangeInserted(start, newTransactions.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) {
            binding.tvTransactionTitle.text = decodeHtmlToTextV2(transaction.text)
            binding.tvTransactionDate.text = getTimeAgo(transaction.createdAt)
            
            val amountStr = transaction.amount
            val isNegative = amountStr.startsWith("-")
            binding.tvAmount.text = amountStr
            
            // Use theme attributes for colors
            val context = binding.root.context
            val color = if (isNegative) {
                ThemeUtils.getThemeColor(context, androidx.appcompat.R.attr.colorError)
            } else {
                ThemeUtils.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)
            }
            binding.tvAmount.setTextColor(color)
            
            binding.tvCurrency.text = transaction.currency.replaceFirstChar { it.uppercase() }
        }
    }
}
