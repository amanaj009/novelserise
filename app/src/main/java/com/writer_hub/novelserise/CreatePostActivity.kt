/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.writer_hub.novelserise.databinding.ActivityCreatePostBinding
import com.engine.core.AppCore.tokenManager
import com.engine.core.helper.PostBody
import com.engine.core.helper.SelectedImageBody
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.PostViewModel
import com.engine.core.helper.ImageHelper
import kotlinx.coroutines.launch

class CreatePostActivity : BaseActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private lateinit var postViewModel: PostViewModel
    private var selectedImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            binding.cardImagePreview.visibility = View.VISIBLE
            // Use ImageHelper (Coil) to load post image
            ImageHelper.loadImage(this, it.toString(), binding.ivPostImage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        postViewModel = AppProvider.getViewModel(application, PostViewModel::class.java)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.toolbar.tvTitle.text = "Create Post"
        binding.toolbar.btnBack.setOnClickListener { finish() }

        val (_, userId) = tokenManager.getTokenAndUserId()
        if (userId != null) {
            val user = tokenManager.getUser(userId)
            binding.tvUserName.text = user?.name ?: "User"
            // Use ImageHelper (Coil) to load user avatar
            ImageHelper.loadCircleImage(
                this,
                user?.avatar,
                binding.ivUserAvatar,
                com.engine.core.R.drawable.ic_profile
            )
        }
    }

    private fun setupListeners() {
        binding.btnAddImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            binding.cardImagePreview.visibility = View.GONE
        }

        binding.btnPost.setOnClickListener {
            submitPost()
        }
    }

    private fun submitPost() {
        val content = binding.etPostContent.text.toString().trim()
        if (content.isEmpty() && selectedImageUri == null) {
            Toast.makeText(this, "Please enter some text or select an image", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnPost.isEnabled = false

        val images = selectedImageUri?.let { listOf(SelectedImageBody(it.toString())) }

        val postBody = PostBody(
            content = content,
            postType = if (images != null) "image" else "text",
            selectedImages = images
        )

        lifecycleScope.launch {
            val result = postViewModel.submitPost(postBody)
            binding.progressBar.visibility = View.GONE
            binding.btnPost.isEnabled = true

            if (result.success) {
                Toast.makeText(this@CreatePostActivity, result.message, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@CreatePostActivity, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
