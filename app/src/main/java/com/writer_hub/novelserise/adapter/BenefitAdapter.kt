/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.R
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.models.BenefitItem

class BenefitAdapter(private val benefits: List<BenefitItem>) :
    RecyclerView.Adapter<BenefitAdapter.BenefitViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BenefitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_benefit, parent, false)
        return BenefitViewHolder(view)
    }

    override fun onBindViewHolder(holder: BenefitViewHolder, position: Int) {
        holder.bind(benefits[position])
    }

    override fun getItemCount(): Int = benefits.size

    class BenefitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.benefitIcon)
        private val title: TextView = view.findViewById(R.id.benefitTitle)

        fun bind(item: BenefitItem) {
            title.text = decodeHtmlToTextV2(item.title)
            item.iconResId?.let { icon.setImageResource(it) }
        }
    }
}