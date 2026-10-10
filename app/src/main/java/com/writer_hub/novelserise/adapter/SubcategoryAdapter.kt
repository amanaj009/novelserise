/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import com.engine.core.models.WoSubcategory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.databinding.ItemCategorySelectableBinding

class SubcategoryAdapter(
    private var subcategories: List<WoSubcategory>,
    private val onSubcategorySelected: (WoSubcategory) -> Unit
) : RecyclerView.Adapter<SubcategoryAdapter.ViewHolder>() {

    private var selectedSubcategoryId: String? = null

    class ViewHolder(val binding: ItemCategorySelectableBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategorySelectableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val subcategory = subcategories[position]
        holder.binding.tvCategoryName.text = subcategory.name

        val isSelected = subcategory.id == selectedSubcategoryId
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
            selectSubcategory(subcategory.id)
            onSubcategorySelected(subcategory)
        }
    }

    override fun getItemCount(): Int = subcategories.size

    fun selectSubcategory(id: String?) {
        selectedSubcategoryId = id
        notifyDataSetChanged()
    }

    fun updateData(newSubcategories: List<WoSubcategory>) {
        subcategories = newSubcategories
        notifyDataSetChanged()
    }
}
