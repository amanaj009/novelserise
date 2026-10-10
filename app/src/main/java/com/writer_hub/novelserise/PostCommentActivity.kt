/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.adapter.PostCommentAdapter
import com.writer_hub.novelserise.databinding.ActivityPostCommentBinding
import com.engine.core.models.CommentData
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.PostViewModel
import kotlinx.coroutines.launch
import kotlin.math.max

class PostCommentActivity : BaseActivity() {

    private lateinit var binding: ActivityPostCommentBinding
    private lateinit var postViewModel: PostViewModel
    private lateinit var adapter: PostCommentAdapter
    private lateinit var postId: String
    
    private var offset = 0
    private var isLoadingMore = false
    private var canLoadMore = true

    companion object {
        private const val EXTRA_POST_ID = "POST_ID"
        private const val EXTRA_COMMENT_COUNT = "COMMENT_COUNT"

        fun start(context: Context, postId: String, commentCount: String = "0") {
            val intent = Intent(context, PostCommentActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, postId)
                putExtra(EXTRA_COMMENT_COUNT, commentCount)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostCommentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root)

        postId = intent.getStringExtra(EXTRA_POST_ID) ?: ""
        if (postId.isEmpty()) {
            finish()
            return
        }

        postViewModel = AppProvider.getViewModel(application, PostViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        fetchComments(isInitial = true)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = PostCommentAdapter(
            onLikeClick = { handleLikeClick(it) },
            onReplyClick = { handleReplyClick(it) },
            onEditClick = { handleEditClick(it) },
            onDeleteClick = { handleDeleteClick(it) },
            onUserClick = { UserDetailsActivity.start(this, it) }
        )
        binding.recyclerViewComments.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewComments.adapter = adapter

        binding.recyclerViewComments.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1) && !isLoadingMore && canLoadMore) {
                    fetchComments(isInitial = false)
                }
            }
        })
    }

    private fun handleLikeClick(comment: CommentData) {
        postViewModel.performCommentAction(
            type = "comment_like",
            commentID = comment.id,
            postID = postId,
            reactionId = "2"
        )
        val wasLiked = comment.isCommentLiked == true
        comment.isCommentLiked = !wasLiked
        val currentLikes = comment.commentLikes?.toIntOrNull() ?: 0
        comment.commentLikes = (if (comment.isCommentLiked == true) currentLikes + 1 else max(0, currentLikes - 1)).toString()
        
        val position = adapter.currentList.indexOf(comment)
        if (position != -1) adapter.notifyItemChanged(position)
    }

    private fun handleReplyClick(comment: CommentData) {
        binding.etComment.requestFocus()
        binding.etComment.hint = "Reply to ${comment.publisher?.name}..."
        binding.etComment.setTag(R.id.etComment, comment.id)
        showKeyboard(binding.etComment)
    }

    private fun handleEditClick(comment: CommentData) {
        binding.etComment.requestFocus()
        binding.etComment.setText(comment.text)
        binding.etComment.setSelection(binding.etComment.text?.length ?: 0)
        binding.etComment.setTag(R.id.etComment, comment.id)
        binding.etComment.setTag(R.id.btnSend, "edit")
        showKeyboard(binding.etComment)
    }

    private fun handleDeleteClick(comment: CommentData) {
        lifecycleScope.launch {
            val success = comment.id?.let { postViewModel.deleteComment(it, postId) }
            if (success == true) {
                Toast.makeText(this@PostCommentActivity, "Comment deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            val text = binding.etComment.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val tagId = binding.etComment.getTag(R.id.etComment) as? String
            val mode = binding.etComment.getTag(R.id.btnSend) as? String

            when {
                mode == "edit" && tagId != null -> performEdit(tagId, text)
                tagId != null -> performReply(tagId, text)
                else -> performPostComment(text)
            }
        }
    }

    private fun performEdit(tagId: String, text: String) {
        lifecycleScope.launch {
            if (postViewModel.editComment(tagId, text)) {
                resetInput()
            }
        }
    }

    private fun performReply(tagId: String, text: String) {
        postViewModel.performCommentAction(commentText = text, type = "create_reply", commentID = tagId, postID = postId)
        resetInput()
    }

    private fun performPostComment(text: String) {
        postViewModel.performCommentAction(commentText = text, type = "create", postID = postId)
        resetInput()
    }

    private fun resetInput() {
        binding.etComment.text?.clear()
        binding.etComment.hint = "Add a comment..."
        binding.etComment.setTag(R.id.etComment, null)
        binding.etComment.setTag(R.id.btnSend, null)
        hideKeyboard(binding.etComment)
    }

    private fun observeViewModel() {
        postViewModel.loading.observe(this) { isLoading ->
            if (offset == 0) binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            else isLoadingMore = isLoading
        }

        postViewModel.comments.observe(this) { comments ->
            handleCommentsResponse(comments)
        }

        postViewModel.error.observe(this) { error ->
            if (!error.isNullOrEmpty()) Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCommentsResponse(comments: List<CommentData>?) {
        if (comments.isNullOrEmpty()) {
            if (offset == 0) {
                binding.tvNoComments.visibility = View.VISIBLE
                adapter.submitList(emptyList())
            }
            canLoadMore = false
            return
        }

        binding.tvNoComments.visibility = View.GONE
        adapter.submitList(comments.toList())
        
        if (offset == 0) binding.recyclerViewComments.scrollToPosition(comments.size - 1)
        offset = comments.size
    }

    private fun fetchComments(isInitial: Boolean) {
        if (isInitial) {
            offset = 0
            canLoadMore = true
        }
        postViewModel.fetchAllComments(this, postId, "fetch_comments", offset.toString())
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
