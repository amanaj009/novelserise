/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import com.engine.core.models.WoSubcategory
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.databinding.ItemSearchSubcategoryBinding

class CategorySubcategoryAdapter(
    private var subcategories: List<WoSubcategory?>,
    private val onItemClick: (WoSubcategory?) -> Unit
) : RecyclerView.Adapter<CategorySubcategoryAdapter.SubcategoryViewHolder>() {

    private var selectedSubcategoryId: String? = "all"

    fun updateData(newSubcategories: List<WoSubcategory?>) {
        subcategories = newSubcategories
        notifyDataSetChanged()
    }

    fun selectSubcategory(subcategoryId: String?) {
        selectedSubcategoryId = subcategoryId ?: "all"
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubcategoryViewHolder {
        val binding = ItemSearchSubcategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SubcategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubcategoryViewHolder, position: Int) {
        val subcategory = subcategories[position]
        holder.bind(subcategory)
        holder.itemView.setOnClickListener { 
            onItemClick(subcategory)
            selectSubcategory(subcategory?.id)
        }
    }

    override fun getItemCount(): Int = subcategories.size

    inner class SubcategoryViewHolder(private val binding: ItemSearchSubcategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(subcategory: WoSubcategory?) {
            binding.subcategoryName.text = subcategory?.name ?: "All"
            val currentId = subcategory?.id ?: "all"
            val isSelected = currentId == selectedSubcategoryId
            
            val context = binding.root.context
            val typedValue = TypedValue()
            val theme = context.theme
            
            theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
            val primaryColor = typedValue.data
            
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            val surfaceColor = typedValue.data

            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            val onSurfaceColor = typedValue.data

            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            val onPrimaryColor = typedValue.data

            if (isSelected) {
                binding.subcategoryCard.setCardBackgroundColor(ColorStateList.valueOf(primaryColor))
                binding.subcategoryName.setTextColor(onPrimaryColor)
                binding.subcategoryCard.strokeWidth = 0
            } else {
                binding.subcategoryCard.setCardBackgroundColor(ColorStateList.valueOf(surfaceColor))
                binding.subcategoryName.setTextColor(onSurfaceColor)
                binding.subcategoryCard.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
            }
        }
    }
}
