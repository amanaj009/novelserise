/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import ToastUtil
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.ChapterComment
import com.engine.core.models.WoChapter
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.WoChpterViewModel
import com.writer_hub.novelserise.adapter.ChapterCommentAdapter
import com.writer_hub.novelserise.databinding.ActivityChapterCommentsBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChapterCommentsActivity : BaseActivity() {

    private lateinit var binding: ActivityChapterCommentsBinding
    private lateinit var viewModel: WoChpterViewModel
    private lateinit var adapter: ChapterCommentAdapter

    private var novelId: String = ""
    private var chapterId: String = ""
    private var parentCommentId: String? = null // If set, we are viewing replies

    private var currentChapter: WoChapter? = null

    private var offset = 0
    private val limit = 20
    private var isLoading = false
    private var canLoadMore = true

    // For debouncing like requests
    private val likeJobs = mutableMapOf<String, Job>()

    companion object {
        private const val EXTRA_NOVEL_ID = "extra_novel_id"
        private const val EXTRA_CHAPTER_ID = "extra_chapter_id"
        private const val EXTRA_PARENT_COMMENT_ID = "extra_parent_comment_id"
        private const val EXTRA_TITLE = "extra_title"

        fun start(context: Context, novelId: String, chapterId: String, title: String = "Comments") {
            val intent = Intent(context, ChapterCommentsActivity::class.java).apply {
                putExtra(EXTRA_NOVEL_ID, novelId)
                putExtra(EXTRA_CHAPTER_ID, chapterId)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }

        fun startForReplies(context: Context, novelId: String, chapterId: String, parentCommentId: String, title: String = "Replies") {
            val intent = Intent(context, ChapterCommentsActivity::class.java).apply {
                putExtra(EXTRA_NOVEL_ID, novelId)
                putExtra(EXTRA_CHAPTER_ID, chapterId)
                putExtra(EXTRA_PARENT_COMMENT_ID, parentCommentId)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChapterCommentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        novelId = intent.getStringExtra(EXTRA_NOVEL_ID) ?: ""
        chapterId = intent.getStringExtra(EXTRA_CHAPTER_ID) ?: ""
        parentCommentId = intent.getStringExtra(EXTRA_PARENT_COMMENT_ID)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Comments"

        viewModel = AppProvider.getViewModel(application, WoChpterViewModel::class.java)
        viewModel.setSelectedNovelId(novelId)

        setupToolbar(title)
        setupRecyclerView()
        
        initializeData()

        binding.btnSend.setOnClickListener { postComment() }
        binding.btnViewMyComment.setOnClickListener { showMyCommentDialog() }
    }

    private fun initializeData() {
        binding.shimmerView.visibility = View.VISIBLE
        binding.shimmerView.startShimmer()
        binding.recyclerView.visibility = View.GONE
        
        showCommentInputState()

        lifecycleScope.launch {
            val selected = GlobalAppData.selectedChapter.value
            if (selected != null && selected.id == chapterId) {
                currentChapter = selected
            } else {
                val idInt = chapterId.toIntOrNull()
                if (idInt != null) {
                    currentChapter = viewModel.getChapter(idInt)
                }
            }

            if (parentCommentId == null) {
                currentChapter?.totalComments?.let { count ->
                    supportActionBar?.title = "Comments ($count)"
                }
            }
            
            loadComments(true)
            updateUIState()
        }
    }

    private fun updateUIState() {
        currentChapter?.let { chapter ->
            if (parentCommentId == null && chapter.isCommented == "1" && chapter.myCommet != null) {
                showAlreadyCommentedState()
            } else {
                showCommentInputState()
            }
        }
    }

    private fun showAlreadyCommentedState() {
        binding.alreadyCommentedLayout.visibility = View.VISIBLE
        binding.commentInputLayout.visibility = View.GONE
    }

    private fun showCommentInputState() {
        binding.alreadyCommentedLayout.visibility = View.GONE
        binding.commentInputLayout.visibility = View.VISIBLE
    }

    private fun showMyCommentDialog() {
        val myComment = currentChapter?.myCommet ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_view_my_comment, null)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val tvComment = dialogView.findViewById<TextView>(R.id.tvComment)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val btnClose = dialogView.findViewById<View>(R.id.btnClose)
        val btnEdit = dialogView.findViewById<View>(R.id.btnEdit)

        tvComment.text = myComment.comment
        ratingBar.rating = myComment.user_rating?.toFloatOrNull() ?: 0f
        
        btnClose.setOnClickListener { dialog.dismiss() }
        btnEdit.setOnClickListener {
            dialog.dismiss()
            enableEditMode(myComment)
        }
        dialog.show()
    }

    private fun enableEditMode(comment: ChapterComment) {
        showCommentInputState()
        binding.etComment.setText(comment.comment)
        binding.etComment.setSelection(binding.etComment.text?.length ?: 0)
        binding.etComment.hint = "Edit your comment..."
        binding.etComment.requestFocus()
        showKeyboard(binding.etComment)
    }
    
    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupToolbar(title: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = ChapterCommentAdapter(
            onReplyClick = { comment ->
                showCommentInputState()
                binding.etComment.requestFocus()
                binding.etComment.hint = "Replying to ${comment.user?.userName}..."
                binding.etComment.setTag(R.id.etComment, comment.id)
                showKeyboard(binding.etComment)
                adapter.setReplyingTo(comment.id)
            },
            onLikeClick = { comment ->
                handleLike(comment)
            },
            onViewRepliesClick = { comment ->
                startForReplies(this, novelId, chapterId, comment.id, "Replies")
            },
            onEditClick = { comment ->
                enableEditMode(comment)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1) && !isLoading && canLoadMore) {
                    loadComments(false)
                }
            }
        })
    }

    private fun loadComments(isInitial: Boolean) {
        if (isInitial) {
            offset = 0
            canLoadMore = true
        }
        
        isLoading = true
        if (!isInitial) binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val results = if (parentCommentId != null) {
                viewModel.getCommentsReplySuspended(parentCommentId!!, chapterId, limit, offset)
            } else {
                viewModel.getComments(novelId, chapterId, limit, offset)
            }

            isLoading = false
            binding.progressBar.visibility = View.GONE
            
            if (isInitial) {
                binding.shimmerView.stopShimmer()
                binding.shimmerView.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
            }

            if (results.isEmpty()) {
                if (isInitial) {
                    binding.tvNoComments.visibility = View.VISIBLE
                    adapter.setComments(emptyList())
                }
                canLoadMore = false
            } else {
                binding.tvNoComments.visibility = View.GONE
                if (isInitial) {
                    adapter.setComments(results)
                } else {
                    adapter.addComments(results)
                }
                offset += results.size
                if (results.size < limit) canLoadMore = false
            }
        }
    }

    private fun postComment() {
        val text = binding.etComment.text.toString().trim()
        if (text.isEmpty()) return

        val replyToId = binding.etComment.getTag(R.id.etComment) as? String
        
        lifecycleScope.launch {
            binding.btnSend.visibility = View.GONE
            binding.sendProgressBar.visibility = View.VISIBLE
            
            val newComment = if (replyToId != null) {
                viewModel.addNewChapterCommentReplySuspended(novelId, replyToId, chapterId, "comment_reply", text)
            } else if (parentCommentId != null) {
                viewModel.addNewChapterCommentReplySuspended(novelId, parentCommentId!!, chapterId, "comment_reply", text)
            } else {
                viewModel.addnewChapterCommentSuspend(chapterId, "comment", text, null)
            }

            binding.btnSend.visibility = View.VISIBLE
            binding.sendProgressBar.visibility = View.GONE

            if (newComment != null) {
                binding.etComment.text?.clear()
                binding.etComment.hint = "Add a comment..."
                binding.etComment.setTag(R.id.etComment, null)
                adapter.setReplyingTo(null)
                
                val isMainCommentUpdate = replyToId == null && parentCommentId == null && currentChapter?.isCommented == "1"

                if (isMainCommentUpdate) {
                    adapter.updateComment(newComment)
                    ToastUtil.showToast(this@ChapterCommentsActivity, "Comment updated")
                } else {
                    if (parentCommentId != null) {
                        adapter.addCommentAtTop(newComment)
                        binding.recyclerView.scrollToPosition(0)
                        binding.tvNoComments.visibility = View.GONE
                    } else {
                        if (replyToId != null) {
                            adapter.incrementReplyCount(replyToId)
                            ToastUtil.showToast(this@ChapterCommentsActivity, "Reply sent")
                        } else {
                            adapter.addCommentAtTop(newComment)
                            binding.recyclerView.scrollToPosition(0)
                            binding.tvNoComments.visibility = View.GONE
                        }
                    }
                }
                
                if (replyToId == null && parentCommentId == null) {
                    currentChapter?.let {
                        it.isCommented = "1"
                        it.myCommet = newComment
                        GlobalAppData.selectedChapter.postValue(it)
                        val currentCount = it.totalComments?.toIntOrNull() ?: 0
                        supportActionBar?.title = "Comments (${currentCount + 1})"
                    }
                    updateUIState()
                }
                
            } else {
                ToastUtil.showToast(this@ChapterCommentsActivity, "Failed to post comment")
            }
        }
    }

    private fun handleLike(comment: ChapterComment) {
        val targetId = comment.id
        val isReply = comment.commentId.isNotEmpty() && comment.commentId != "0"
        
        // Store original state
        val originalIsLiked = comment.isCommentLiked
        val originalLikesCount = comment.commentLikes
        
        // Optimistic UI update (Immediate)
        comment.isCommentLiked = !originalIsLiked
        val currentLikesCount = originalLikesCount.toIntOrNull() ?: 0
        comment.commentLikes = if (comment.isCommentLiked) (currentLikesCount + 1).toString() else (currentLikesCount - 1).coerceAtLeast(0).toString()
        adapter.updateComment(comment)

        // Cancel pending job for this comment
        likeJobs[targetId]?.cancel()

        // Debounce the network call
        likeJobs[targetId] = lifecycleScope.launch {
            delay(500) // 500ms delay to wait for user to finish tapping
            
            val result = viewModel.likeOrUnlikeComment(targetId, isReply, chapterId)
            
            if (result == null) {
                // Rollback
                comment.isCommentLiked = originalIsLiked
                comment.commentLikes = originalLikesCount
                adapter.updateComment(comment)
                ToastUtil.showToast(this@ChapterCommentsActivity, "Unable to update like")
            } else if (result != comment.isCommentLiked) {
                // Sync
                comment.isCommentLiked = result
                val baseLikes = originalLikesCount.toIntOrNull() ?: 0
                comment.commentLikes = if (result) (baseLikes + 1).toString() else (baseLikes - 1).coerceAtLeast(0).toString()
                adapter.updateComment(comment)
            }
            likeJobs.remove(targetId)
        }
    }
}
