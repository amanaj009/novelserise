/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.adapter.LibraryAdapter
import com.writer_hub.novelserise.databinding.FragmentLibraryBinding
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.LibraryUiState
import com.engine.core.viewmodel.LibraryViewModel

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var libraryAdapter: LibraryAdapter

    private var offset = 0
    private val limit = 10
    private var isLoading = false
    private var isLastPage = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        libraryViewModel = AppProvider.getViewModel(requireActivity().application, LibraryViewModel::class.java)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        // Initial fetch
        fetchLibrary()
    }

    private fun setupRecyclerView() {
        libraryAdapter = LibraryAdapter { novel ->
            novel.id?.let { libraryViewModel.removeNovelFromServerLibrary(it) }
        }
        val layoutManager = LinearLayoutManager(context)
        binding.rvLibrary.apply {
            this.layoutManager = layoutManager
            adapter = libraryAdapter
            
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && !isLastPage) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= limit
                        ) {
                            fetchLibrary()
                        }
                    }
                }
            })
        }
    }

    private fun setupObservers() {
        libraryViewModel.libraryUiState.observe(viewLifecycleOwner) { state ->
            _binding?.let { binding ->
                when (state) {
                    is LibraryUiState.Loading -> {
                        if (offset == 0) {
                            toggleLoading(true)
                        }
                    }

                    is LibraryUiState.Success -> {
                        toggleLoading(false)
                        isLoading = false
                        binding.layoutEmpty.root.visibility = View.GONE
                        binding.rvLibrary.visibility = View.VISIBLE

                        val newList = state.novels
                        libraryAdapter.submitList(newList)

                        // Update offset for next fetch
                        offset = newList.size
                    }

                    is LibraryUiState.Empty -> {
                        toggleLoading(false)
                        isLoading = false
                        isLastPage = true
                        if (offset == 0) {
                            binding.rvLibrary.visibility = View.GONE
                            binding.layoutEmpty.root.visibility = View.VISIBLE
                        }
                    }

                    is LibraryUiState.Error -> {
                        toggleLoading(false)
                        isLoading = false
                    }
                }
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            offset = 0
            isLastPage = false
            libraryViewModel.fetchLibrary(offset = 0, limit = limit, reload = true)
        }
    }

    private fun fetchLibrary() {
        isLoading = true
        libraryViewModel.fetchLibrary(offset = offset, limit = limit)
    }

    private fun toggleLoading(isLoading: Boolean) {
        _binding?.let { binding ->
            if (isLoading) {
                binding.layoutShimmer.root.visibility = View.VISIBLE
                binding.layoutShimmer.shimmerViewContainer.startShimmer()
                binding.rvLibrary.visibility = View.GONE
                binding.layoutEmpty.root.visibility = View.GONE
            } else {
                binding.layoutShimmer.shimmerViewContainer.stopShimmer()
                binding.layoutShimmer.root.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
