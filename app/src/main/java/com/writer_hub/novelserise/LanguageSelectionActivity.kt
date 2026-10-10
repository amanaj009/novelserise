/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.SplashViewModel
import com.writer_hub.novelserise.databinding.ActivityLanguageSelectionBinding

class LanguageSelectionActivity : BaseActivity() {

    private lateinit var binding: ActivityLanguageSelectionBinding
    private lateinit var splashViewModel: SplashViewModel
    private var selectedLangCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root)

        splashViewModel = AppProvider.getViewModel(application, SplashViewModel::class.java)

        setupListeners()
        
        // Default to current selection or "hi" (Hindi) as requested
        val currentLang = if (splashViewModel.isLanguageSelected()) {
            splashViewModel.getSelectedLanguage()
        } else {
            "hi"
        }
        selectLanguage(currentLang)
    }

    private fun setupListeners() {
        binding.cardEnglish.setOnClickListener { selectLanguage("en") }
        binding.cardHindi.setOnClickListener { selectLanguage("hi") }
        binding.cardMarathi.setOnClickListener { selectLanguage("mr") }

        binding.btnContinue.setOnClickListener {
            selectedLangCode?.let { code ->
                saveAndFinish(code)
            }
        }
    }

    private fun selectLanguage(code: String) {
        selectedLangCode = code
        
        // Reset all cards
        resetCard(binding.cardEnglish)
        resetCard(binding.cardHindi)
        resetCard(binding.cardMarathi)

        // Highlight selected
        val selectedCard = when (code) {
            "en" -> binding.cardEnglish
            "hi" -> binding.cardHindi
            "mr" -> binding.cardMarathi
            else -> binding.cardHindi // Default to Hindi if unknown
        }
        highlightCard(selectedCard)
    }

    private fun resetCard(card: com.google.android.material.card.MaterialCardView) {
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
        card.setCardBackgroundColor(typedValue.data)
        theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
        card.strokeColor = typedValue.data
        card.strokeWidth = 1 // Ensure it's visible but subtle
    }

    private fun highlightCard(card: com.google.android.material.card.MaterialCardView) {
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
        card.setCardBackgroundColor(typedValue.data)
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        card.strokeColor = typedValue.data
        card.strokeWidth = 4 // Make selection bold
    }

    private fun saveAndFinish(languageCode: String) {
        // 1. Save in shared preferences for language setting
        val sharedPrefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString("app_language", languageCode)
            apply()
        }

        // 2. Update SplashViewModel to mark language as selected
        splashViewModel.saveSelectedLanguage(languageCode)

        // 3. Restart activity to apply language changes and go to Dashboard
        val intent = Intent(this, DashboardActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
