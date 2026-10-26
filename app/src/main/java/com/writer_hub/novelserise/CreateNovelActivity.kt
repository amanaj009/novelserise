/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.activity.OnBackPressedCallback
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
    private var selectedCategoryName: String? = null
    private var selectedSubcategoryName: String? = null

    private var currentStep = 1
    
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
        showStep(1)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        
        if (isEditMode) {
            populateData(existingNovel!!)
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { goBack() }
        
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
            if (currentStep < TOTAL_STEPS) goNext() else handleSave()
        }

        listenToTitleInput()
        setupDescriptionTextWatcher()
    }

    private fun goBack() {
        if (currentStep > 1) showStep(currentStep - 1) else finish()
    }

    private fun goNext() {
        if (!validateStep(currentStep)) return
        showStep(currentStep + 1)
    }

    private fun validateStep(step: Int): Boolean {
        when (step) {
            1 -> {
                val title = binding.titleEditText.text.toString().trim()
                val idea = binding.ideaEditText.text.toString().trim()
                if (isMatchingTitle) {
                    Toast.makeText(this, "Please use a unique novel name.", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (title.isEmpty() || idea.isEmpty()) {
                    Toast.makeText(this, "Please add a title and a story idea", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            2 -> if (selectedCategory == null) {
                Toast.makeText(this, "Please pick a genre", Toast.LENGTH_SHORT).show()
                return false
            }
            3 -> if (!isEditMode && smallCoverUri == null) {
                Toast.makeText(this, "Please select a cover image", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun showStep(step: Int) {
        currentStep = step
        binding.step1.visibility = if (step == 1) View.VISIBLE else View.GONE
        binding.step2.visibility = if (step == 2) View.VISIBLE else View.GONE
        binding.step3.visibility = if (step == 3) View.VISIBLE else View.GONE
        binding.step4.visibility = if (step == 4) View.VISIBLE else View.GONE

        listOf(binding.seg1, binding.seg2, binding.seg3, binding.seg4).forEachIndexed { index, seg ->
            seg.setBackgroundResource(if (index < step) R.drawable.v2_seg_on else R.drawable.v2_seg_off)
        }
        binding.tvStepCount.text = "$step / $TOTAL_STEPS"

        binding.saveButton.text = when (step) {
            1 -> "Next · Genre"
            2 -> "Next · Covers"
            3 -> "Next · Review"
            else -> if (isEditMode) "Update story" else "Create story"
        }

        if (step == TOTAL_STEPS) bindReview()
        currentFocus?.clearFocus()
    }

    private fun bindReview() {
        binding.tvReviewTitle.text = binding.titleEditText.text.toString().trim()
        binding.tvReviewMeta.text = listOfNotNull(selectedCategoryName, selectedSubcategoryName).joinToString(" · ")
        val cover: Any? = smallCoverUri ?: existingNovel?.imageLink
        if (cover != null) {
            ImageHelper.loadImage(this, cover, binding.ivReviewCover, R.drawable.shimmer_placeholder)
        }
        refreshReviewStatus()
    }

    private fun refreshReviewStatus() {
        binding.tvReviewStatus.text = if (binding.btnStatusCompleted.isChecked) "Completed" else "Running"
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
        refreshReviewStatus()
    }

    private fun loadImage(source: Any?, imageView: ImageView) {
        if (source == null || (source is String && source.isEmpty())) {
            Log.e("CreateNovelActivity", "Image source is null or empty for ImageView: ${imageView.id}")
            return
        }
        imageView.imageTintList = null
        imageView.setPadding(0, 0, 0, 0)
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
            selectedCategoryName = categoryToSelect.name

            val categoryAdapter = CategoryAdapter(categories) { category ->
                selectedCategory = category.id
                selectedCategoryName = category.name
                setupSubCategoryAdapter(category.subcategories, null)
            }

            categoryAdapter.selectCategory(categoryToSelect.id)
            binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
            binding.recyclerView.adapter = categoryAdapter
            
            val subCatToSelectId = if (isEditMode) existingNovel?.subCategory else categoryToSelect.subcategories.firstOrNull()?.id
            setupSubCategoryAdapter(categoryToSelect.subcategories, subCatToSelectId)
        }
    }

    private fun setupSubCategoryAdapter(subcategories: List<WoSubcategory>, preSelectedId: String?) {
        selectedSubcategory = preSelectedId ?: subcategories.firstOrNull()?.id
        selectedSubcategoryName = subcategories.find { it.id == selectedSubcategory }?.name

        val adapter = SubcategoryAdapter(subcategories) { subcategory ->
            selectedSubcategory = subcategory.id
            selectedSubcategoryName = subcategory.name
        }
        if (preSelectedId == null) {
            selectedSubcategory?.let { adapter.selectSubcategory(it) }
        }

        if (preSelectedId != null) {
            adapter.selectSubcategory(preSelectedId)
        }

        binding.subcategoryRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.subcategoryRecyclerView.adapter = adapter
        binding.layoutSubcategory.visibility = if (subcategories.isNotEmpty()) View.VISIBLE else View.GONE
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
                        binding.tvTitleAvailable.visibility = if (exists) View.GONE else View.VISIBLE
                    }
                } else {
                    debounceJob?.cancel()
                    isMatchingTitle = false
                    binding.warningTextTop.visibility = View.GONE
                    binding.tvTitleAvailable.visibility = View.GONE
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
                binding.tvIdeaCount.text = "$wordCount / 500 words"
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

    private companion object {
        const val TOTAL_STEPS = 4
    }

    override fun onDestroy() {
        super.onDestroy()
        debounceJob?.cancel()
    }
}
