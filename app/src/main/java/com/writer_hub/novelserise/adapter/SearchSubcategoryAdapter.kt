/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import com.engine.core.models.WoSubcategory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.databinding.ItemSearchSubcategoryBinding

class SearchSubcategoryAdapter(
    private var subcategories: List<WoSubcategory>,
    private val onItemClick: (WoSubcategory) -> Unit
) : RecyclerView.Adapter<SearchSubcategoryAdapter.SubcategoryViewHolder>() {

    private var selectedSubcategoryId: String? = null

    fun updateData(newSubcategories: List<WoSubcategory>) {
        subcategories = newSubcategories
        notifyDataSetChanged()
    }

    fun selectSubcategory(subcategoryId: String?) {
        selectedSubcategoryId = subcategoryId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubcategoryViewHolder {
        val binding = ItemSearchSubcategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SubcategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubcategoryViewHolder, position: Int) {
        val subcategory = subcategories[position]
        holder.bind(subcategory)
        holder.itemView.setOnClickListener { onItemClick(subcategory) }
    }

    override fun getItemCount(): Int = subcategories.size

    inner class SubcategoryViewHolder(private val binding: ItemSearchSubcategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(subcategory: WoSubcategory) {
            binding.subcategoryName.text = subcategory.name
            // You can add logic for image loading here if subcategory.image is used
            // For now, let's just display the name.
            binding.subcategoryCard.isSelected = subcategory.id == selectedSubcategoryId
            if (subcategory.id == selectedSubcategoryId) {
                binding.subcategoryName.alpha = 1.0f
                binding.subcategoryCard.strokeWidth = 4
            } else {
                binding.subcategoryName.alpha = 0.6f
                binding.subcategoryCard.strokeWidth = 1
            }
        }
    }
}
