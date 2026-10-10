/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.models.BankTransaction
import com.engine.core.utill.ThemeUtils
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemBankTransactionBinding

class BankTransactionAdapter(
    private val transactions: MutableList<BankTransaction> = mutableListOf()
) : RecyclerView.Adapter<BankTransactionAdapter.ViewHolder>() {

    fun updateData(newTransactions: List<BankTransaction>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        // The backing list is replaced, so an inserted-range notification would leave RecyclerView
        // holding stale rows and can crash after a paid transaction changes the response shape.
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBankTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    inner class ViewHolder(private val binding: ItemBankTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: BankTransaction) {
            binding.tvBankName.text = transaction.bankName ?: "BANK TRANSFER"
            binding.tvAccountInfo.text = "Account: ${transaction.accountNumber}"
            
            // Relative Time Formatting
            binding.tvDate.text = formatRelativeTime(transaction.createdAt)
            
            binding.tvAmount.text = "₹ ${transaction.amount ?: 0.0}"

            val status = (transaction.transactionStatus ?: "pending").lowercase()
            binding.tvStatus.text = status.uppercase()

            val context = binding.root.context
            
            // Resolve colors via theme
            val statusColor = when (status) {
                "success", "verified", "completed" -> {
                    ThemeUtils.getThemeColor(context, com.engine.core.R.attr.colorSuccess)
                }
                "pending", "processing" -> {
                    ThemeUtils.getThemeColor(context, com.engine.core.R.attr.colorDarkGold)
                }
                else -> {
                    ThemeUtils.getThemeColor(context, androidx.appcompat.R.attr.colorError)
                }
            }

            // Apply status colors
            binding.statusTag.apply {
                setStrokeColor(ColorStateList.valueOf(statusColor))
                setCardBackgroundColor(ColorStateList.valueOf(statusColor).withAlpha(20))
            }
            binding.tvStatus.setTextColor(statusColor)
            binding.tvAmount.setTextColor(ThemeUtils.getThemeColor(context, com.engine.core.R.attr.colorPrimary))
        }

        private fun formatRelativeTime(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return "N/A"
            return try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val date = sdf.parse(dateString)
                if (date != null) {
                    android.text.format.DateUtils.getRelativeTimeSpanString(
                        date.time,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                } else {
                    dateString
                }
            } catch (e: Exception) {
                dateString
            }
        }
    }
}
