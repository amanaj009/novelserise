/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.adapter.TransactionAdapter
import com.writer_hub.novelserise.databinding.ActivityPointTransactionHistoryBinding
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel

class TransactionHistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityPointTransactionHistoryBinding
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var adapter: TransactionAdapter

    // Pagination state
    private var isLoading = false
    private var isLastPage = false
    private var currentPage = 0
    private val pageSize = 20

    companion object {
        fun start(context: Context, novelId: String, type: String, currency: String) {
            val intent = Intent(context, TransactionHistoryActivity::class.java).apply {
                putExtra("novel_id", novelId)
                putExtra("type", type)
                putExtra("currency", currency)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPointTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        fetchTransactionHistory(isInitialLoad = true)
    }

    private fun setupToolbar() {
        binding.toolbar.tvTitle.text = "Diamond History"
        binding.toolbar.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter()
        binding.recyclerTransactions.layoutManager = LinearLayoutManager(this)
        binding.recyclerTransactions.adapter = adapter

        binding.recyclerTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && !isLastPage) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                    ) {
                        currentPage++
                        fetchTransactionHistory(isInitialLoad = false)
                    }
                }
            }
        })
    }

    private fun fetchTransactionHistory(isInitialLoad: Boolean) {
        val novelId = intent.getStringExtra("novel_id") ?: "0"
        val type = intent.getStringExtra("type") ?: "writer"
        val currency = intent.getStringExtra("currency") ?: "diamond"

        if (isInitialLoad) {
            currentPage = 0
            isLastPage = false
            binding.shimmerViewContainer.visibility = View.VISIBLE
            binding.shimmerViewContainer.startShimmer()
            binding.layoutNoItem.visibility = View.GONE
            binding.recyclerTransactions.visibility = View.GONE
            binding.progressBarLoadMore.visibility = View.GONE
        } else {
            binding.progressBarLoadMore.visibility = View.VISIBLE
        }

        isLoading = true

        novelViewModel.fetchTransactions(
            novelId = novelId,
            type = type,
            currency = currency,
            limit = pageSize,
            offset = currentPage * pageSize
        ) { transactions ->
            runOnUiThread {
                isLoading = false
                if (isInitialLoad) {
                    binding.shimmerViewContainer.stopShimmer()
                    binding.shimmerViewContainer.visibility = View.GONE
                } else {
                    binding.progressBarLoadMore.visibility = View.GONE
                }

                if (transactions != null) {
                    if (transactions.isNotEmpty()) {
                        binding.recyclerTransactions.visibility = View.VISIBLE
                        binding.layoutNoItem.visibility = View.GONE
                        adapter.addTransactions(transactions, clear = isInitialLoad)
                        
                        if (transactions.size < pageSize) {
                            isLastPage = true
                        }
                    } else {
                        if (isInitialLoad) {
                            showNoItems()
                        }
                        isLastPage = true
                    }
                } else {
                    if (isInitialLoad) {
                        Toast.makeText(this, "Failed to load transactions.", Toast.LENGTH_SHORT).show()
                        showNoItems()
                    }
                    isLastPage = true
                }
            }
        }
    }

    private fun showNoItems() {
        binding.recyclerTransactions.visibility = View.GONE
        binding.layoutNoItem.visibility = View.VISIBLE
    }
}
