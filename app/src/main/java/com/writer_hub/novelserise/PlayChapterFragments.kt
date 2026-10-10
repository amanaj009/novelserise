/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.ChapterComment
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.ReportViewModel
import com.engine.core.viewmodel.WoChpterViewModel
import com.writer_hub.novelserise.adapter.ChapterAdapter
import com.writer_hub.novelserise.adapter.ChapterCommentAdapter
import com.writer_hub.novelserise.databinding.FragmentPlayChapterChaptersBinding
import com.writer_hub.novelserise.databinding.FragmentPlayChapterCommentsBinding
import com.writer_hub.novelserise.databinding.FragmentPlayChapterDetailsBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DetailsFragment : Fragment() {
    private var _binding: FragmentPlayChapterDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayChapterDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        GlobalAppData.selectedNovel.observe(viewLifecycleOwner) { novel ->
            _binding?.let { binding ->
                binding.tvNovelDescription.text = decodeHtmlToTextV2(novel?.description ?: "")
                binding.tvAuthorName.text =
                    "Author: ${decodeHtmlToTextV2(novel?.writerData?.name ?: "Unknown")}"
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ChaptersFragment : Fragment() {
    private var _binding: FragmentPlayChapterChaptersBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: WoChpterViewModel
    private lateinit var adapter: ChapterAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayChapterChaptersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val novelId = arguments?.getString("novelId") ?: return
        viewModel = AppProvider.getUniqueViewModel(requireActivity().application, WoChpterViewModel::class.java, novelId)

        adapter = ChapterAdapter(
            chapters = emptyList(),
            onChapterClick = { chapter ->
                (requireActivity() as? PlayChapterActivity)?.initializeWithChapter(chapter)
            },
            onLoadMoreClick = { loadNextPage() }
        )
        val layoutManager = LinearLayoutManager(context)
        binding.rvChapters.layoutManager = layoutManager
        binding.rvChapters.adapter = adapter
        // Next page loads as the list nears its end, no "Load More" tap needed.
        binding.rvChapters.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 4) loadNextPage()
            }
        })

        setupObservers()
    }

    /** The cache is shared with the novel page, which may have jumped ahead; fill pages in order. */
    private fun loadNextPage() {
        if (viewModel.isLoading.value == true) return
        val page = viewModel.nextPageToLoad() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fetchChapters(
                order = viewModel.cachedOrder,
                offset = page,
                status = "published",
                pagination = page,
                scrolling = true,
                extra = true,
                currentReading = "0",
                fromPage = "player_chapters"
            )
        }
    }

    private fun setupObservers() {
        viewModel._chapterModelsList.observe(viewLifecycleOwner) { pageList ->
            if (!pageList.isNullOrEmpty()) {
                adapter.updateChapters(viewModel.chaptersFromFirstPage(pageList))
                showLoadingFooter(viewModel.isLoading.value == true)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading -> showLoadingFooter(isLoading) }

        viewModel.chapterIsBuyedStatus.observe(viewLifecycleOwner) { statusMap ->
            val allChapters = adapter.getChapters()
            var changed = false
            allChapters.forEach { chapter ->
                val id = chapter.id ?: return@forEach
                statusMap[id]?.let { 
                    if (chapter.isBuyed != it) {
                        chapter.isBuyed = it
                        changed = true
                    }
                }
            }
            if (changed) adapter.updateChapters(allChapters)
        }

        viewModel.chapterTemporaryUnlockStatus.observe(viewLifecycleOwner) { statusMap ->
            val allChapters = adapter.getChapters()
            var changed = false
            allChapters.forEach { chapter ->
                val id = chapter.id ?: return@forEach
                statusMap[id]?.let { 
                    val isUnlocked = it == "true"
                    if (chapter.temporaryUnlock != isUnlocked) {
                        chapter.temporaryUnlock = isUnlocked
                        changed = true
                    }
                }
            }
            if (changed) adapter.updateChapters(allChapters)
        }

        GlobalAppData.currentlyPlayingChapter.observe(viewLifecycleOwner) {
            adapter.notifyDataSetChanged()
        }
    }

    /** Footer spinner only while a further page is loading (pages load on scroll, so no button). */
    private fun showLoadingFooter(isLoading: Boolean) {
        adapter.setLoadMore(show = isLoading && adapter.getChapters().isNotEmpty(), loading = isLoading)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class CommentsFragment : Fragment() {
    private var _binding: FragmentPlayChapterCommentsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: WoChpterViewModel
    private lateinit var reportViewModel: ReportViewModel
    private lateinit var adapter: ChapterCommentAdapter
    private var novelId: String = ""
    private var chapterId: String = ""
    private var hasMyComment: Boolean = false
    
    // For debouncing like requests
    private val likeJobs = mutableMapOf<String, Job>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayChapterCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        novelId = arguments?.getString("novelId") ?: ""
        
        viewModel = AppProvider.getUniqueViewModel(requireActivity().application, WoChpterViewModel::class.java, novelId)
        reportViewModel = AppProvider.getViewModel(requireActivity().application, ReportViewModel::class.java)

        // Hide input by default until we know if user has commented
        binding.commentInputLayout.visibility = View.GONE

        setupRecyclerView()
        
        GlobalAppData.currentlyPlayingChapter.observe(viewLifecycleOwner) { chapter ->
            if (chapter != null) {
                if (chapter.id != chapterId) {
                    chapterId = chapter.id ?: ""
                    loadComments()
                }
                
                // Use global data to hide input if user already commented
                hasMyComment = chapter.isCommented == "1"
                if (hasMyComment) {
                    binding.commentInputLayout.visibility = View.GONE
                } else {
                    // If no comment arrived yet from API, but global says not commented, 
                    // we show it (or wait for loadComments to be sure)
                    binding.commentInputLayout.visibility = View.VISIBLE
                }
            }
        }

        binding.btnSend.setOnClickListener { postComment() }
        
        reportViewModel.reportStatus.observe(viewLifecycleOwner) { (success, message) ->
            Toast.makeText(context ?: return@observe, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChapterCommentAdapter(
            onReplyClick = { comment ->
                _binding?.let { binding ->
                    // Ensure layout is visible for reply even if user has a main comment
                    binding.commentInputLayout.visibility = View.VISIBLE
                    binding.etComment.requestFocus()
                    binding.etComment.hint = "Replying to ${comment.user?.userName}..."
                    binding.etComment.setTag(R.id.etComment, comment.id)
                    adapter.setReplyingTo(comment.id)
                }
            },
            onLikeClick = { handleLike(it) },
            onViewRepliesClick = { comment ->
                 ChapterCommentsActivity.startForReplies(requireContext(), novelId, chapterId, comment.id, "Replies")
            },
            onEditClick = { showEditDialog(it) },
            onReportClick = { showReportDialog(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
    }

    private fun loadComments() {
        if (chapterId.isEmpty()) return

        _binding?.apply {
            shimmerView.visibility = View.VISIBLE
            shimmerView.startShimmer()
            recyclerView.visibility = View.GONE
            tvNoComments.visibility = View.GONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val results = viewModel.getComments(novelId, chapterId, 20, 0)
            val binding = _binding ?: return@launch
            
            binding.shimmerView.stopShimmer()
            binding.shimmerView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            
            if (results.isEmpty()) {
                binding.tvNoComments.visibility = View.VISIBLE
                adapter.setComments(emptyList())
                // Only show if global state didn't already say we commented
                if (!hasMyComment) {
                    binding.commentInputLayout.visibility = View.VISIBLE
                }
            } else {
                val sortedResults = results.toMutableList()
                val myCommentIndex = sortedResults.indexOfFirst { it.isMy == "1" }
                if (myCommentIndex != -1) {
                    val myComment = sortedResults.removeAt(myCommentIndex)
                    sortedResults.add(0, myComment)
                    binding.commentInputLayout.visibility = View.GONE
                    hasMyComment = true
                    // Sync global state if needed
                    GlobalAppData.currentlyPlayingChapter.value?.isCommented = "1"
                } else {
                    // Check global state as fallback
                    if (!hasMyComment) {
                        binding.commentInputLayout.visibility = View.VISIBLE
                    }
                }
                
                binding.tvNoComments.visibility = View.GONE
                adapter.setComments(sortedResults)
            }
        }
    }

    private fun postComment() {
        val text = binding.etComment.text.toString().trim()
        if (text.isEmpty() || chapterId.isEmpty()) return

        val replyToId = binding.etComment.getTag(R.id.etComment) as? String

        if (replyToId == null && !hasMyComment) {
            showRatingDialog(text)
        } else {
            performPostComment(text, replyToId)
        }
    }

    private fun showRatingDialog(commentText: String) {
        val context = context ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rate_comment, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val etCommentInDialog = dialogView.findViewById<EditText>(R.id.etComment)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val btnSubmit = dialogView.findViewById<View>(R.id.btnSubmit)
        
        tvTitle.text = "Rate this Chapter"
        etCommentInDialog.setText(commentText)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        btnSubmit.setOnClickListener {
            val rating = ratingBar.rating.toInt()
            if (rating == 0) {
                Toast.makeText(context, "Please select a rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val finalCommentText = etCommentInDialog.text.toString().trim()
            if (finalCommentText.isEmpty()) {
                Toast.makeText(context, "Please write a comment", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            performPostComment(finalCommentText, null, rating.toString())
        }
        
        dialog.show()
    }

    private fun performPostComment(text: String, replyToId: String?, rating: String? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            val initialBinding = _binding ?: return@launch
            initialBinding.btnSend.isEnabled = false
            
            // If rating is provided, we use both functions "separately"
            if (rating != null) {
                viewModel.updateChapterDetails(null, chapterId, "rating", rating, null, null).await()
                GlobalAppData.currentlyPlayingChapter.value?.isRated = "1"
            }

            val newComment = if (replyToId != null) {
                viewModel.addNewChapterCommentReplySuspended(novelId, replyToId, chapterId, "comment_reply", text)
            } else {
                viewModel.addnewChapterCommentSuspend(chapterId, "comment", text, null, null, rating)
            }

            val binding = _binding ?: return@launch
            binding.btnSend.isEnabled = true

            if (newComment != null) {
                binding.etComment.text.clear()
                binding.etComment.hint = "Add a comment..."
                binding.etComment.setTag(R.id.etComment, null)
                adapter.setReplyingTo(null)

                if (replyToId != null) {
                    adapter.incrementReplyCount(replyToId)
                    Toast.makeText(context, "Reply sent", Toast.LENGTH_SHORT).show()
                    // If user already has a main comment, hide the input box again after reply
                    if (hasMyComment) {
                        binding.commentInputLayout.visibility = View.GONE
                    }
                } else {
                    hasMyComment = true
                    GlobalAppData.currentlyPlayingChapter.value?.isCommented = "1"
                    adapter.addCommentAtTop(newComment)
                    binding.recyclerView.scrollToPosition(0)
                    binding.tvNoComments.visibility = View.GONE
                    binding.commentInputLayout.visibility = View.GONE
                }
            } else {
                Toast.makeText(context, "Failed to post comment", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showEditDialog(comment: ChapterComment) {
        val context = context ?: return
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Edit Comment")
        val input = EditText(context)
        input.setText(comment.comment)
        builder.setView(input)
        
        builder.setPositiveButton("Save") { _, _ ->
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val updated = viewModel.addnewChapterCommentSuspend(chapterId, "comment", text, null, comment.id)
                    if (updated != null) {
                        adapter.updateComment(updated)
                        Toast.makeText(getContext(), "Comment updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    private fun showReportDialog(comment: ChapterComment) {
        val context = context ?: return
        val reasons = arrayOf("Spam", "Inappropriate", "Harassment", "Other")
        AlertDialog.Builder(context)
            .setTitle("Report Comment")
            .setItems(reasons) { _, which ->
                reportViewModel.reportComment(comment.id, reasons[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleLike(comment: ChapterComment) {
        val targetId = comment.id
        val isReply = comment.commentId.isNotEmpty() && comment.commentId != "0"

        // Store original state for potential rollback or sync
        val originalIsLiked = comment.isCommentLiked
        val originalLikesCount = comment.commentLikes
        
        // Optimistic UI Update (Immediate)
        comment.isCommentLiked = !originalIsLiked
        val currentLikes = originalLikesCount.toIntOrNull() ?: 0
        comment.commentLikes = if (comment.isCommentLiked) (currentLikes + 1).toString() else (currentLikes - 1).coerceAtLeast(0).toString()
        adapter.updateComment(comment)

        // Cancel any pending like request for this specific comment
        likeJobs[targetId]?.cancel()

        // Debounce the network call by 500ms
        likeJobs[targetId] = viewLifecycleOwner.lifecycleScope.launch {
            delay(500)
            
            val result = viewModel.likeOrUnlikeComment(targetId, isReply, chapterId)
            
            if (result == null) {
                // Rollback on complete failure
                comment.isCommentLiked = originalIsLiked
                comment.commentLikes = originalLikesCount
                adapter.updateComment(comment)
                Toast.makeText(context, "Unable to update like", Toast.LENGTH_SHORT).show()
            } else if (result != comment.isCommentLiked) {
                // Sync with server if it disagrees with our final local state
                comment.isCommentLiked = result
                // We trust the server result for the new state, but count sync might be complex.
                // For simplicity, we just trigger an update with the server's state.
                val base = originalLikesCount.toIntOrNull() ?: 0
                comment.commentLikes = if (result) (base + 1).toString() else (base - 1).coerceAtLeast(0).toString()
                adapter.updateComment(comment)
            }
            likeJobs.remove(targetId)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
