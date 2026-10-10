/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import com.engine.core.models.WoCategory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.databinding.ItemCategorySelectableBinding

class CategoryAdapter(
    private var categories: List<WoCategory>,
    private val onCategorySelected: (WoCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private var selectedCategoryId: String? = null

    class ViewHolder(val binding: ItemCategorySelectableBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategorySelectableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.binding.tvCategoryName.text = category.name

        val isSelected = category.id == selectedCategoryId
        val context = holder.itemView.context
        val accentColor = com.engine.core.manager.ThemeManager.getThemeColor(context, "colorAccent")
        val outlineColor =
            com.engine.core.manager.ThemeManager.getThemeColor(context, "colorOutline")
        val onSurfaceColor =
            com.engine.core.manager.ThemeManager.getThemeColor(context, "colorOnSurface")

        holder.binding.cardCategory.strokeColor = if (isSelected) accentColor else outlineColor
        holder.binding.cardCategory.setCardBackgroundColor(
            if (isSelected) com.engine.core.manager.ThemeManager.getThemeColor(
                context,
                "colorSecondaryPrimaryLite"
            ) else com.engine.core.manager.ThemeManager.getThemeColor(context, "colorSurface")
        )
        holder.binding.tvCategoryName.setTextColor(if (isSelected) accentColor else onSurfaceColor)

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

    fun updateData(newCategories: List<WoCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}
