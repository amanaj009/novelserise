/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import com.engine.core.models.WoCategory
import android.view.View
import androidx.core.content.ContextCompat
import com.writer_hub.novelserise.R
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.databinding.ItemGenreTileBinding

class CategoryAdapter(
    private var categories: List<WoCategory>,
    private val onCategorySelected: (WoCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private var selectedCategoryId: String? = null

    class ViewHolder(val binding: ItemGenreTileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGenreTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.binding.tvCategoryName.text = category.name

        val isSelected = category.id == selectedCategoryId
        val context = holder.itemView.context

        holder.binding.cardCategory.strokeColor =
            ContextCompat.getColor(context, if (isSelected) R.color.v2_accent else R.color.v2_border)
        holder.binding.cardCategory.strokeWidth = if (isSelected) 2.dp(context) else 1.dp(context)
        holder.binding.cardCategory.setCardBackgroundColor(
            ContextCompat.getColor(context, if (isSelected) R.color.v2_accent_soft else R.color.v2_card)
        )
        holder.binding.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            selectCategory(category.id)
            onCategorySelected(category)
        }
    }

    override fun getItemCount(): Int = categories.size

    fun selectCategory(id: String?) {
        selectedCategoryId = id
        notifyDataSetChanged()
    }

    private fun Int.dp(context: android.content.Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    fun updateData(newCategories: List<WoCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}
