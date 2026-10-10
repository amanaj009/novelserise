/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.SearchViewModel
import com.writer_hub.novelserise.adapter.SearchAdapter
import com.writer_hub.novelserise.adapter.SearchCategoryAdapter
import com.writer_hub.novelserise.databinding.ActivitySearchBinding

class SearchActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var searchViewModel: SearchViewModel
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var categoryAdapter: SearchCategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        searchViewModel = AppProvider.getViewModel(application, SearchViewModel::class.java)

        setupRecyclerView()
        setupCategoryList()
        setupListeners()
        observeViewModel()
        searchViewModel.fetchCategories()
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(emptyList()) { suggestion ->
            when (suggestion.tag) {
                "novel" -> {
                    NovelDetailActivity.start(this, suggestion.id.toString())
                }
                "user" -> {
                    UserDetailsActivity.start(this, suggestion.id.toString())
                }
            }
        }
        binding.rvSearch.layoutManager = LinearLayoutManager(this)
        binding.rvSearch.adapter = searchAdapter
    }

    private fun setupCategoryList() {
        categoryAdapter = SearchCategoryAdapter(emptyList()) { category ->
            CategoryNovelActivity.start(this, category.id)
        }
        binding.rvCategories.layoutManager = GridLayoutManager(this, 2)
        binding.rvCategories.adapter = categoryAdapter
        binding.layoutCategories.visibility = View.VISIBLE
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text.clear()
            searchViewModel.clearAllValues()
            binding.rvSearch.visibility = View.GONE
            binding.layoutEmpty.visibility = View.GONE
            binding.shimmerViewContainer.visibility = View.GONE
            binding.shimmerViewContainer.stopShimmer()
            binding.layoutCategories.visibility = View.VISIBLE
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE

                if (query.isEmpty()) {
                    binding.rvSearch.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.GONE
                    binding.shimmerViewContainer.visibility = View.GONE
                    binding.shimmerViewContainer.stopShimmer()
                    binding.layoutCategories.visibility = View.VISIBLE
                    searchViewModel.clearAllValues()
                } else {
                    binding.layoutCategories.visibility = View.GONE
                    if (query.length >= 3) {
                        searchViewModel.searchSuggestion(query, 20, 0, {}, {})
                    } else {
                        searchAdapter.updateData(emptyList())
                        binding.rvSearch.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.GONE
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString().trim()
                if (query.length >= 2) {
                    searchViewModel.searchSuggestion(query, 20, 0, {}, {})
                }
                true
            } else {
                false
            }
        }
    }

    private fun observeViewModel() {
        searchViewModel.categories.observe(this) { categories ->
            val popularCategories = categories
                .sortedByDescending { it.novel_count ?: 0 }
                .take(8)
            categoryAdapter.updateData(popularCategories)
            if (binding.etSearch.text.isNullOrBlank()) {
                binding.layoutCategories.visibility = View.VISIBLE
                binding.rvCategories.visibility = if (popularCategories.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        searchViewModel.isLoadingCategories.observe(this) { isLoading ->
            if (binding.etSearch.text.isNullOrBlank()) {
                binding.shimmerCategories.visibility = if (isLoading) View.VISIBLE else View.GONE
                if (isLoading) binding.shimmerCategories.startShimmer()
                else binding.shimmerCategories.stopShimmer()
                binding.rvCategories.visibility = if (isLoading) View.GONE else View.VISIBLE
            }
        }

        searchViewModel.suggestions.observe(this) { suggestions ->
            searchAdapter.updateData(suggestions)
            if (searchViewModel.isLoading.value == false && !binding.etSearch.text.isNullOrEmpty()) {
                val query = binding.etSearch.text.toString().trim()
                if (query.length >= 3) {
                    binding.layoutEmpty.visibility =
                        if (suggestions.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvSearch.visibility = if (suggestions.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        searchViewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.shimmerViewContainer.startShimmer()
                binding.rvSearch.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
            } else {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                val suggestions = searchViewModel.suggestions.value ?: emptyList()
                val query = binding.etSearch.text.toString().trim()
                if (query.length >= 3) {
                    binding.rvSearch.visibility =
                        if (suggestions.isEmpty()) View.GONE else View.VISIBLE
                    binding.layoutEmpty.visibility =
                        if (suggestions.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
