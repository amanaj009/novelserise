/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import com.engine.core.models.WoSubcategory
import androidx.core.content.ContextCompat
import com.writer_hub.novelserise.R
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

        holder.binding.cardCategory.strokeColor =
            ContextCompat.getColor(context, if (isSelected) R.color.v2_pill_dark else R.color.v2_border)
        holder.binding.cardCategory.setCardBackgroundColor(
            ContextCompat.getColor(context, if (isSelected) R.color.v2_pill_dark else R.color.v2_card)
        )
        holder.binding.tvCategoryName.setTextColor(
            ContextCompat.getColor(context, if (isSelected) R.color.v2_on_pill_dark else R.color.v2_text)
        )

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
