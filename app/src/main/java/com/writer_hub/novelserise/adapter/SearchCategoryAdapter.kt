/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import com.engine.core.models.WoCategory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.databinding.ItemSearchCategoryBinding

class SearchCategoryAdapter(
    private var categories: List<WoCategory>,
    private val onItemClick: (WoCategory) -> Unit
) : RecyclerView.Adapter<SearchCategoryAdapter.CategoryViewHolder>() {

    private var selectedCategoryId: String? = null

    fun updateData(newCategories: List<WoCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }

    fun selectCategory(categoryId: String?) {
        selectedCategoryId = categoryId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemSearchCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category)
        holder.itemView.setOnClickListener { onItemClick(category) }
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(private val binding: ItemSearchCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: WoCategory) {
            binding.categoryName.text = category.name
            // You can add logic for image loading here if category.image is used
            // For now, let's just display the name.
            binding.categoryCard.isSelected = category.id == selectedCategoryId
            if (category.id == selectedCategoryId) {
                binding.categoryName.alpha = 1.0f
                binding.categoryCard.strokeWidth = 2
            } else {
                binding.categoryName.alpha = 1.0f
                binding.categoryCard.strokeWidth = 1
            }
        }
    }
}
