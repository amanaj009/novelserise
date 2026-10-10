/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.NovelBody
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.Novel
import com.engine.core.models.WoSubcategory
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.CreateNovelViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.writer_hub.novelserise.adapter.CategoryAdapter
import com.writer_hub.novelserise.adapter.SubcategoryAdapter
import com.writer_hub.novelserise.databinding.ActivityCreateNovelBinding
import com.writer_hub.novelserise.helper.NovelImageEditorHelper
import com.writer_hub.novelserise.helper.NovelImagePicker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreateNovelActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateNovelBinding
    private lateinit var viewModel: CreateNovelViewModel
    
    private var fullCoverUri: Uri? = null
    private var smallCoverUri: Uri? = null
    
    private var selectedCategory: String? = null
    private var selectedSubcategory: String? = null
    
    private var isEditMode: Boolean = false
    private var existingNovel: Novel? = null
    
    private var debounceJob: Job? = null
    private var isMatchingTitle: Boolean = false

    private val novelImagePicker = NovelImagePicker(this) { uri, id ->
        if (id == R.id.imagecoverView) {
            fullCoverUri = uri
            loadImage(uri, binding.imagecoverView)
        } else if (id == R.id.smallNovelCover) {
            smallCoverUri = uri
            loadImage(uri, binding.smallNovelCover)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateNovelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = AppProvider.getViewModel(application, CreateNovelViewModel::class.java)
        
        // Only use GlobalAppData
        existingNovel = GlobalAppData.selectedNovel.value
        isEditMode = existingNovel != null
        
        setupUI()
        setupStatusSelector()
        setupListeners()
        fetchCategories()
        
        if (isEditMode) {
            populateData(existingNovel!!)
        }
    }

    private fun setupUI() {
        binding.headerView.tvTitle.text = if (isEditMode) "Edit Novel" else "Create Novel"
        binding.headerView.btnBack.setOnClickListener { finish() }
        binding.saveButton.text = if (isEditMode) "Update Story" else "Create Story"
        
        // Hide policies in Edit Mode
        binding.layoutPolicies.visibility = if (isEditMode) View.GONE else View.VISIBLE

        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        val color = typedValue.data
        val tint = ColorStateList.valueOf(color)
        
        if (!isEditMode) {
            binding.imagecoverView.imageTintList = tint
            binding.smallNovelCover.imageTintList = tint
        }
    }

    private fun setupListeners() {
        binding.imagecoverView.setOnClickListener {
            novelImagePicker.start(R.id.imagecoverView) { uri, context ->
                NovelImageEditorHelper.getWideBannerIntent(context, uri)
            }
        }

        binding.smallNovelCover.setOnClickListener {
            novelImagePicker.start(R.id.smallNovelCover) { uri, context ->
                NovelImageEditorHelper.getNovelCoverIntent(context, uri)
            }
        }

        binding.saveButton.setOnClickListener {
            handleSave()
        }

        listenToTitleInput()
        setupDescriptionTextWatcher()
    }

    private fun setupStatusSelector() {
        binding.btnStatusRunning.setOnClickListener {
            setCompletedStatus(isCompleted = false)
        }
        binding.btnStatusCompleted.setOnClickListener {
            setCompletedStatus(isCompleted = true)
        }
        setCompletedStatus(isCompleted = false)
    }

    private fun setCompletedStatus(isCompleted: Boolean) {
        binding.btnStatusRunning.isChecked = !isCompleted
        binding.btnStatusCompleted.isChecked = isCompleted
    }

    private fun loadImage(source: Any?, imageView: ImageView) {
        if (source == null || (source is String && source.isEmpty())) {
            Log.e("CreateNovelActivity", "Image source is null or empty for ImageView: ${imageView.id}")
            return
        }
        imageView.imageTintList = null
        ImageHelper.loadImage(this, source, imageView, R.drawable.shimmer_placeholder)
    }

    private fun fetchCategories() {
        viewModel.fetchCategories()
        viewModel.categories.observe(this) { categories ->
            if (categories.isNullOrEmpty()) return@observe

            val categoryToSelect = if (isEditMode && existingNovel?.category != null) {
                categories.find { it.id == existingNovel?.category } ?: categories.first()
            } else {
                categories.first()
            }

            selectedCategory = categoryToSelect.id

            val categoryAdapter = CategoryAdapter(categories) { category ->
                selectedCategory = category.id
                setupSubCategoryAdapter(category.subcategories, null)
            }

            categoryAdapter.selectCategory(categoryToSelect.id)
            binding.recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.recyclerView.adapter = categoryAdapter
            
            val subCatToSelectId = if (isEditMode) existingNovel?.subCategory else categoryToSelect.subcategories.firstOrNull()?.id
            setupSubCategoryAdapter(categoryToSelect.subcategories, subCatToSelectId)
        }
    }

    private fun setupSubCategoryAdapter(subcategories: List<WoSubcategory>, preSelectedId: String?) {
        selectedSubcategory = preSelectedId ?: subcategories.firstOrNull()?.id

        val adapter = SubcategoryAdapter(subcategories) { subcategory ->
            selectedSubcategory = subcategory.id
        }

        if (preSelectedId != null) {
            adapter.selectSubcategory(preSelectedId)
        }

        binding.subcategoryRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.subcategoryRecyclerView.adapter = adapter
        binding.subcategoryRecyclerView.visibility = if (subcategories.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun populateData(novel: Novel) {
        binding.titleEditText.setText(novel.title)
        binding.ideaEditText.setText(novel.description)
        binding.publishSwitch.isChecked = novel.isPublic == "1"
        setCompletedStatus(isCompleted = novel.isCompleted == "1")
        
        loadImage(novel.imageLink, binding.smallNovelCover)
        loadImage(novel.imageLinkCover, binding.imagecoverView)
    }

    private fun listenToTitleInput() {
        binding.titleEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable?) {
                val title = editable.toString().trim()
                if (title.isNotEmpty()) {
                    debounceJob?.cancel()
                    debounceJob = lifecycleScope.launch {
                        delay(500)
                        val exists = viewModel.chknovelname(title, existingNovel?.id)
                        isMatchingTitle = exists
                        binding.warningTextTop.visibility = if (exists) View.VISIBLE else View.GONE
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupDescriptionTextWatcher() {
        binding.ideaEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val wordCount = countWords(s.toString())
                if (wordCount < 10 && wordCount > 0) {
                    binding.warningTextIdea.text = "Description is too short (min 10 words)"
                    binding.warningTextIdea.visibility = View.VISIBLE
                } else if (wordCount > 500) {
                    binding.warningTextIdea.text = "Max 500 words limit!"
                    binding.warningTextIdea.visibility = View.VISIBLE
                } else {
                    binding.warningTextIdea.visibility = View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun countWords(text: String) = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size

    private fun handleSave() {
        if (isMatchingTitle) {
            Toast.makeText(this, "Please use a unique novel name.", Toast.LENGTH_SHORT).show()
            return
        }
        
        val title = binding.titleEditText.text.toString().trim()
        val idea = binding.ideaEditText.text.toString().trim()
        
        if (title.isEmpty() || idea.isEmpty() || selectedCategory == null) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isEditMode && smallCoverUri == null) {
            Toast.makeText(this, "Please select a cover image", Toast.LENGTH_SHORT).show()
            return
        }

        // Policy Checkpoints validation - ONLY for New Novels
        if (!isEditMode) {
            if (!binding.ownershipCheckbox.isChecked || !binding.contentPolicyCheckbox.isChecked || !binding.termsCheckbox.isChecked) {
                Toast.makeText(this, "Please agree to all policy checkpoints before proceeding.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val novelBody = NovelBody(
            id = existingNovel?.id,
            title = title,
            description = idea,
            category = selectedCategory,
            subCategory = selectedSubcategory,
            isPublic = if (binding.publishSwitch.isChecked) "1" else "0",
            isCompleted = if (binding.btnStatusCompleted.isChecked) "1" else "0",
            fullCoverUri = fullCoverUri?.toString() ?: existingNovel?.imageLinkCover,
            smallCoverUri = smallCoverUri?.toString() ?: existingNovel?.imageLink,
            smallCoverUrl = existingNovel?.imageLink,
            fullCoverUrl = existingNovel?.imageLinkCover
        )

        binding.progressBar.visibility = View.VISIBLE
        binding.saveButton.isEnabled = false
        GlobalAppData.lastErrorMessage = null

        lifecycleScope.launch {
            val result = viewModel.createNewNovel(novelBody)
            binding.progressBar.visibility = View.GONE
            binding.saveButton.isEnabled = true

            if (result != null) {
                val msg = if (isEditMode) "Novel updated successfully!" else "Novel created successfully!"
                Toast.makeText(this@CreateNovelActivity, msg, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                val errorText = GlobalAppData.lastErrorMessage ?: "Something went wrong"
                MaterialAlertDialogBuilder(this@CreateNovelActivity)
                    .setTitle(if (isEditMode) "Update Failed" else "Creation Failed")
                    .setMessage(errorText)
                    .setPositiveButton("OK", null)
                    .show()
                Toast.makeText(this@CreateNovelActivity, "Operation failed", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        debounceJob?.cancel()
    }
}
