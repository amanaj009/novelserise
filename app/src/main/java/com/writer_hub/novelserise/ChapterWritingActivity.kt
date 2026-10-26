/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.Novel
import com.engine.core.models.WoChapter
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.WriterChapterViewModel
import com.writer_hub.novelserise.databinding.ActivityChapterWritingBinding
import com.writer_hub.novelserise.worker.PublishChapterWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChapterWritingActivity : BaseActivity() {

    private lateinit var binding: ActivityChapterWritingBinding
    private lateinit var chapterViewModel: WriterChapterViewModel
    private var chapter: WoChapter? = null
    private var novel: Novel? = null
    private var isModified: Boolean = false

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private var isUndoing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChapterWritingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Only get from GlobalAppData
        chapter = GlobalAppData.selectedChapter.value
        novel = GlobalAppData.selectedNovel.value

        if (chapter == null) {
            finish()
            return
        }

        chapterViewModel = AppProvider.getViewModel(application, WriterChapterViewModel::class.java)

        setupUI()
        setupListeners()
        
        // Fetch full chapter data if content is missing
        if (chapter?.content.isNullOrBlank() || chapter?.content == "hello") {
            fetchFullChapterData()
        } else {
            loadChapterData()
        }
        
        observeViewModel()
    }

    private fun fetchFullChapterData() {
        val chapterId = chapter?.id?.toIntOrNull() ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val fullChapter = chapterViewModel.getChapterById(chapterId)
                if (fullChapter != null) {
                    chapter = fullChapter
                    GlobalAppData.selectedChapter.postValue(chapter)
                    loadChapterData()
                } else {
                    Toast.makeText(this@ChapterWritingActivity, "Failed to load chapter content", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChapterWritingActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupUI() {
        val published = chapter?.status == "published"
        if (published) {
            binding.btnPublish.text = "Update"
        }

        val number = chapter?.sqc?.takeIf { it.isNotBlank() }
        binding.tvBarTitle.text = if (number != null) "Chapter $number" else "New chapter"
        val novelTitle = novel?.title?.takeIf { it.isNotBlank() }
        val state = if (published) "Published" else "Draft"
        binding.tvBarSubtitle.text = if (novelTitle != null) "$novelTitle · $state" else state

        updateScheduleUI()
        updateUndoRedoButtons()
    }

    private fun updateScheduleUI() {
        if (chapter?.isSchedule == "1") {
            binding.btnSchedule.alpha = 1.0f
        } else {
            binding.btnSchedule.alpha = 0.5f
        }
    }

    private fun loadChapterData() {
        isUndoing = true
        binding.etChapterTitle.setText(decodeHtmlToTextV2(chapter?.chapterName ?: ""))
        binding.etContent.setText(decodeHtmlToTextV2(chapter?.content ?: ""))
        updateWordCount(binding.etContent.text.toString())
        
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoButtons()
        
        isModified = false
        isUndoing = false
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.etChapterTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { isModified = true }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etContent.addTextChangedListener(object : TextWatcher {
            private var lastText: String = ""
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isUndoing) {
                    lastText = s.toString()
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isModified = true
                updateWordCount(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {
                if (!isUndoing) {
                    val currentText = s.toString()
                    if (currentText != lastText) {
                        if (undoStack.size >= 50) undoStack.removeAt(0)
                        undoStack.add(lastText)
                        redoStack.clear()
                        updateUndoRedoButtons()
                    }
                }
            }
        })

        binding.btnSave.setOnClickListener {
            saveChapterLocally()
        }

        binding.btnPublish.setOnClickListener {
            startPublishingJob()
        }

        binding.btnSchedule.setOnClickListener {
            if (validateChapter()) {
                showDateTimePicker()
            }
        }

        binding.ivUndo.setOnClickListener {
            if (undoStack.isNotEmpty()) {
                performUndo()
            }
        }

        binding.ivRedo.setOnClickListener {
            if (redoStack.isNotEmpty()) {
                performRedo()
            }
        }
    }

    private fun performUndo() {
        isUndoing = true
        val currentText = binding.etContent.text.toString()
        redoStack.add(currentText)
        val previousText = undoStack.removeAt(undoStack.size - 1)
        binding.etContent.setText(previousText)
        binding.etContent.setSelection(previousText.length)
        isUndoing = false
        updateUndoRedoButtons()
    }

    private fun performRedo() {
        isUndoing = true
        val currentText = binding.etContent.text.toString()
        undoStack.add(currentText)
        val nextText = redoStack.removeAt(redoStack.size - 1)
        binding.etContent.setText(nextText)
        binding.etContent.setSelection(nextText.length)
        isUndoing = false
        updateUndoRedoButtons()
    }

    private fun updateUndoRedoButtons() {
        val undoAvailable = undoStack.isNotEmpty()
        val redoAvailable = redoStack.isNotEmpty()
        
        binding.ivUndo.isEnabled = undoAvailable
        binding.ivRedo.isEnabled = redoAvailable
        
        binding.ivUndo.alpha = if (undoAvailable) 1.0f else 0.3f
        binding.ivRedo.alpha = if (redoAvailable) 1.0f else 0.3f
    }

    private fun observeViewModel() {
        chapterViewModel.scheduleStatus.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Chapter scheduled successfully", Toast.LENGTH_SHORT).show()
                chapter = chapter?.copy(isSchedule = "1") 
                GlobalAppData.selectedChapter.postValue(chapter)
                updateScheduleUI()
            } else {
                Toast.makeText(this, "Failed to schedule chapter", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateWordCount(text: String) {
        val words = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size
        binding.tvWordCount.text = "Words: $words"
        chapter = chapter?.copy(wordsCount = words.toString())
        GlobalAppData.selectedChapter.postValue(chapter)
    }

    private fun validateChapter(): Boolean {
        val wordLimit = GlobalAppData.siteSettings?.config?.wordLimit?.toIntOrNull() ?: 0
        val currentWordCount = chapter?.wordsCount?.toIntOrNull() ?: 0
        
        if (currentWordCount < wordLimit) {
            Toast.makeText(this, "Minimum $wordLimit words required", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (GlobalAppData.appType == GlobalAppData.APP_TYPE_AUDIO) {
            if (chapter?.audioFilePath.isNullOrBlank()) {
                Toast.makeText(this, "Audio file is required for audio novels", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        
        return true
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val timePickerDialog = TimePickerDialog(this, { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val formattedDate = sdf.format(calendar.time)
                
                chapter = chapter?.copy(scheduleAt = formattedDate)
                GlobalAppData.selectedChapter.postValue(chapter)

                scheduleChapter(formattedDate)

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
            timePickerDialog.show()

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun scheduleChapter(scheduledDate: String) {
        val data = arrayOf(
            novel?.id,
            chapter?.id,
            scheduledDate
        )
        chapterViewModel.scheduleChapter(data)
    }

    private fun saveChapterLocally() {
        val title = binding.etChapterTitle.text.toString()
        val content = binding.etContent.text.toString()
        val chapterId = chapter?.id ?: return

        if (title.isBlank()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val wordCount = content.trim().split("\\s+".toRegex()).size.toString()
                val status = if (chapter?.status == "published") "published" else "unpublished"

                val resultChapter = chapterViewModel.saveOrUpdateChapter(
                    chapterId = chapterId,
                    chapterName = title,
                    content = content,
                    wordCount = wordCount,
                    status = status
                )

                if (resultChapter != null) {
                    chapter = resultChapter
                    GlobalAppData.selectedChapter.postValue(chapter)
                    Toast.makeText(this@ChapterWritingActivity, "Saved successfully", Toast.LENGTH_SHORT).show()
                    isModified = false
                } else {
                    Toast.makeText(this@ChapterWritingActivity, "Failed to save chapter", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChapterWritingActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun startPublishingJob() {
        val title = binding.etChapterTitle.text.toString()
        val content = binding.etContent.text.toString()
        val chapterId = chapter?.id ?: return
        val novelId = novel?.id ?: return

        if (title.isBlank()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateChapter()) {
            return
        }

        val wordCount = content.trim().split("\\s+".toRegex()).size.toString()

        // Save content to a temporary file
        val tempFileName = "publish_content_${chapterId}_${System.currentTimeMillis()}.txt"
        AppProvider.getFileManager().saveToFile(tempFileName, content)

        val workRequest = OneTimeWorkRequestBuilder<PublishChapterWorker>()
            .setInputData(workDataOf(
                "novel_id" to novelId,
                "chapter_id" to chapterId,
                "chapter_name" to title,
                "content_file" to tempFileName,
                "word_count" to wordCount,
                "status" to "published"
            ))
            .build()

        val workManager = WorkManager.getInstance(this)
        val publishWork = workManager.getWorkInfoByIdLiveData(workRequest.id)
        workManager.enqueue(workRequest)

        setLoading(true)
        binding.btnPublish.text = "Publishing..."
        binding.tvAutoSaveStatus.text = "Publishing chapter..."
        Toast.makeText(this, "Publishing chapter...", Toast.LENGTH_SHORT).show()

        // Keep the editor open while the background upload runs. Closing it here
        // made a successful publish look like the app had navigated away on error.
        publishWork.observe(this) { workInfo ->
            when (workInfo?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    setLoading(false)
                    chapter = chapter?.copy(
                        chapterName = title,
                        content = content,
                        wordsCount = wordCount,
                        status = "published"
                    )
                    GlobalAppData.selectedChapter.postValue(chapter)
                    binding.btnPublish.text = "Update"
                    binding.tvAutoSaveStatus.text = "Published"
                    isModified = false
                    Toast.makeText(this, "Chapter published successfully", Toast.LENGTH_SHORT).show()
                    publishWork.removeObservers(this)
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    setLoading(false)
                    binding.btnPublish.text = if (chapter?.status == "published") "Update" else "Publish"
                    binding.tvAutoSaveStatus.text = "Publishing failed"
                    Toast.makeText(this, "Chapter publish failed. Please try again.", Toast.LENGTH_LONG).show()
                    publishWork.removeObservers(this)
                }
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                    setLoading(true)
                    binding.btnPublish.text = "Publishing..."
                }
                else -> Unit
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !isLoading
        binding.btnPublish.isEnabled = !isLoading
        binding.btnSchedule.isEnabled = !isLoading
        binding.ivUndo.isEnabled = !isLoading && undoStack.isNotEmpty()
        binding.ivRedo.isEnabled = !isLoading && redoStack.isNotEmpty()
    }
}
