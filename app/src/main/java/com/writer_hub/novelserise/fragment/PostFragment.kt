/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.AppCore.tokenManager
import com.engine.core.manager.GlobalAppData
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.WriterNovelViewModel
import com.writer_hub.novelserise.CreateNovelActivity
import com.writer_hub.novelserise.NovelStatsActivity
import com.writer_hub.novelserise.TransactionHistoryActivity
import com.writer_hub.novelserise.WriterChapterActivity
import com.writer_hub.novelserise.adapter.WriterNovelAdapter
import com.writer_hub.novelserise.databinding.FragmentPostBinding
import kotlinx.coroutines.launch
import java.util.Locale

class PostFragment : Fragment() {

    private companion object {
        const val HIDING = "hiding"
    }

    private var _binding: FragmentPostBinding? = null
    private val binding get() = _binding!!
    private lateinit var writerNovelViewModel: WriterNovelViewModel
    private lateinit var adapter: WriterNovelAdapter

    // Pagination state
    private var isLoading = false
    private var isLastPage = false
    private var currentPage = 0
    private val pageSize = 20

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        writerNovelViewModel = AppProvider.getViewModel(requireActivity().application, WriterNovelViewModel::class.java)

        setupRecyclerView()
        setupListeners()
        setupCreatorEarnings()
        
        fetchWriterNovels(isInitialLoad = true) // Fetch initial data
    }

    private fun setupCreatorEarnings() {
        // Observe user data for diamond income (actual writer earnings)
        GlobalAppData.userDetailsLiveData.observe(viewLifecycleOwner) { userDetails ->
            _binding?.let { binding ->
                val income = userDetails?.userData?.diamondIncome ?: 0.0
                binding.tvTotalEarnings.text =
                    String.format(Locale.getDefault(), "₹%.2f", income)
            }
        }
        
        binding.btnViewTransactions.setOnClickListener {
            // Using correct starting parameters for writer diamond income transactions
            TransactionHistoryActivity.start(
                context ?: return@setOnClickListener,
                "0",
                "writer",
                "diamond"
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = WriterNovelAdapter(
            novels = mutableListOf(),
            onEditClick = { novel ->
                GlobalAppData.selectedNovel.value = novel
                val intent =
                    Intent(context ?: return@WriterNovelAdapter, CreateNovelActivity::class.java)
                startActivity(intent)
            },
            onStatsClick = { novel ->
                val intent =
                    Intent(context ?: return@WriterNovelAdapter, NovelStatsActivity::class.java)
                intent.putExtra("NOVEL_ID", novel.id)
                startActivity(intent)
            },
            onNovelClick = { novel ->
                GlobalAppData.selectedNovel.value = novel
                val intent =
                    Intent(context ?: return@WriterNovelAdapter, WriterChapterActivity::class.java)
                startActivity(intent)
            }
        )
        binding.rvWriterNovels.layoutManager = LinearLayoutManager(context)
        binding.rvWriterNovels.itemAnimator = null
        binding.rvWriterNovels.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnCreateNovel.setOnClickListener {
            openCreateNovel()
        }

        binding.cardCreateNovel.setOnClickListener {
            openCreateNovel()
        }

        binding.layoutEmpty.btnCreateEmpty.setOnClickListener {
            openCreateNovel()
        }
        
        binding.nestedScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            updateCreateButton(scrollY)
            // Check if we are near the bottom of the scroll view
            val view = v.getChildAt(0)
            if (view != null) {
                val diff = view.bottom - (v.height + scrollY)
                if (diff <= 300) { // Trigger early for smoother experience
                    if (!isLoading && !isLastPage) {
                        currentPage++
                        fetchWriterNovels(isInitialLoad = false)
                    }
                }
            }
        })
    }

    /** Studio totals from the loaded novels; "+" while more pages are still unloaded. */
    private fun updateTotals() {
        val binding = _binding ?: return
        val novels = adapter.items
        val more = if (isLastPage) "" else "+"
        binding.tvTotalNovels.text = "${novels.size}$more"
        binding.tvTotalChapters.text = "${novels.sumOf { it.chapterCount ?: 0 }}$more"
        binding.tvTotalViews.text = WriterNovelAdapter.compactCount(novels.sumOf { it.view?.toLongOrNull() ?: 0L }) + more
        binding.tvNovelCount.text = "${novels.size}$more"
        binding.tvNovelCount.visibility = if (novels.isEmpty()) View.GONE else View.VISIBLE
        // The dashed "start a new novel" card duplicates the empty state's button.
        binding.cardCreateNovel.visibility = if (novels.isEmpty()) View.GONE else View.VISIBLE
        updateCreateButton()
    }

    /** The floating "New Novel" button appears once the "Start a new novel" card has scrolled away. */
    private fun updateCreateButton(scrollY: Int = binding.nestedScrollView.scrollY) {
        val binding = _binding ?: return
        val card = binding.cardCreateNovel
        val show = card.visibility == View.VISIBLE && scrollY > card.bottom
        val fab = binding.btnCreateNovel
        if (show == (fab.visibility == View.VISIBLE && fab.tag != HIDING)) return
        fab.animate().cancel()
        if (show) {
            fab.tag = null
            fab.visibility = View.VISIBLE
            fab.alpha = 0f
            fab.translationY = fab.height / 2f
            fab.animate().alpha(1f).translationY(0f).setDuration(180).start()
        } else {
            fab.tag = HIDING
            fab.animate().alpha(0f).translationY(fab.height / 2f).setDuration(150)
                .withEndAction { fab.visibility = View.GONE; fab.tag = null }.start()
        }
    }

    private fun openCreateNovel() {
        GlobalAppData.selectedNovel.value = null // Clear for new creation
        val intent = Intent(context ?: return, CreateNovelActivity::class.java)
        startActivity(intent)
    }

    private fun fetchWriterNovels(isInitialLoad: Boolean) {
        val (_, userId) = tokenManager.getTokenAndUserId()
        if (userId == null || isLoading) return

        isLoading = true
        if (isInitialLoad) {
            currentPage = 0
            isLastPage = false
            binding.shimmerViewContainer.visibility = View.VISIBLE
            binding.shimmerViewContainer.startShimmer()
            binding.rvWriterNovels.visibility = View.GONE
            binding.layoutEmpty.root.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val novels = writerNovelViewModel.getWriterNovels(
                writerId = userId,
                limit = pageSize,
                offset = currentPage * pageSize,
                reload = isInitialLoad
            )

            val binding = _binding ?: return@launch

            if (isInitialLoad) {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.GONE
            }

            if (novels.isNotEmpty()) {
                binding.layoutEmpty.root.visibility = View.GONE
                binding.rvWriterNovels.visibility = View.VISIBLE
                if (isInitialLoad) {
                    adapter.updateData(novels)
                } else {
                    adapter.addNovels(novels)
                }
                
                // If we got fewer items than requested, we reached the end
                if (novels.size < pageSize) {
                    isLastPage = true
                }
                updateTotals()
            } else {
                if (isInitialLoad) {
                    binding.layoutEmpty.root.visibility = View.VISIBLE
                    binding.rvWriterNovels.visibility = View.GONE
                    adapter.updateData(emptyList())
                }
                isLastPage = true
                updateTotals()
            }
            
            isLoading = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Only refresh if not already loading to avoid duplicate requests
        if (!isLoading) {
             fetchWriterNovels(isInitialLoad = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
