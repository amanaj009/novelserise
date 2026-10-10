/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImageView
import com.writer_hub.novelserise.databinding.ActivityNovelImageEditorBinding
import com.writer_hub.novelserise.util.NovelImageUtils

class NovelImageEditorActivity : BaseActivity() {

    private lateinit var binding: ActivityNovelImageEditorBinding
    private var sourceUri: Uri? = null

    companion object {
        const val KEY_SOURCE_URI = "input_uri"
        const val KEY_SCREEN_TITLE = "editor_title"
        const val KEY_RATIO_X = "aspect_x"
        const val KEY_RATIO_Y = "aspect_y"
        const val KEY_IS_FIXED = "fixed_ratio"
        const val KEY_OUTPUT_URI = "processed_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovelImageEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourceUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(KEY_SOURCE_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(KEY_SOURCE_URI)
        }

        if (sourceUri == null) {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeEditor()
        setupListeners()
    }

    private fun initializeEditor() {
        val title = intent.getStringExtra(KEY_SCREEN_TITLE) ?: "Edit"
        val rx = intent.getIntExtra(KEY_RATIO_X, 1)
        val ry = intent.getIntExtra(KEY_RATIO_Y, 1)
        val fixed = intent.getBooleanExtra(KEY_IS_FIXED, false)

        binding.editorTitle.text = title

        binding.novelCropView.apply {
            guidelines = CropImageView.Guidelines.ON
            setImageUriAsync(sourceUri)
            
            if (fixed) {
                setAspectRatio(rx, ry)
                setFixedAspectRatio(true)
            }
        }
    }

    private fun setupListeners() {
        binding.backBtn.setOnClickListener { finish() }

        binding.rotateTool.setOnClickListener {
            binding.novelCropView.rotateImage(90)
        }

        binding.mirrorTool.setOnClickListener {
            binding.novelCropView.flipImageHorizontally()
        }

        binding.applyBtn.setOnClickListener {
            binding.novelCropView.croppedImageAsync()
        }

        binding.novelCropView.setOnCropImageCompleteListener { _, result ->
            if (result.isSuccessful) {
                result.bitmap?.let { bitmap ->
                    val uri = NovelImageUtils.cacheImageToDisk(this, bitmap)
                    if (uri != null) {
                        val data = Intent().apply {
                            putExtra(KEY_OUTPUT_URI, uri)
                        }
                        setResult(Activity.RESULT_OK, data)
                        finish()
                    } else {
                        Toast.makeText(this, "Save error", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.e("NovelEditor", "Error: ${result.error?.message}")
                Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
