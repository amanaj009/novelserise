/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.adapter.BankTransactionAdapter
import com.writer_hub.novelserise.databinding.ActivityBankTransactionBinding
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.BankTransactionViewModel

class BankTransactionActivity : BaseActivity() {

    private lateinit var binding: ActivityBankTransactionBinding
    private lateinit var viewModel: BankTransactionViewModel
    private lateinit var adapter: BankTransactionAdapter

    companion object {
        /**
         * Starts the BankTransactionActivity.
         * @param context The context to start the activity from.
         */
        fun start(context: Context) {
            val intent = Intent(context, BankTransactionActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBankTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root)

        // Using AppProvider for consistent ViewModel management
        viewModel = AppProvider.getViewModel(application, BankTransactionViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupListeners()

        viewModel.fetchTransactions(isRefresh = true)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = BankTransactionAdapter()
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter

        binding.rvTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                val loading = viewModel.isLoading.value ?: false
                val hasMore = viewModel.hasMore.value ?: false

                if (!loading && hasMore) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                    ) {
                        viewModel.fetchTransactions()
                    }
                }
            }
        })
    }

    private fun setupObservers() {
        viewModel.transactions.observe(this) { transactions ->
            adapter.updateData(transactions ?: emptyList())
            binding.tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (!binding.swipeRefresh.isRefreshing) {
                binding.progressBar.visibility = if (isLoading && adapter.itemCount == 0) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.fetchTransactions(isRefresh = true)
        }
    }
}
