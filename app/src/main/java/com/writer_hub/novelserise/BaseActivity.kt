/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.engine.core.AppCore
import com.engine.core.utill.EdgeToEdgeHelper
import java.util.Locale

/**
 * Base Activity to handle common functionality like Language changes.
 */
open class BaseActivity : AppCompatActivity() {

    private var lastAppliedStyle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply dynamic theme style based on user selection
        applyThemeStyle()
        super.onCreate(savedInstanceState)
    }

    /** Blocks screenshots/recording in release builds only, so debug builds stay capturable. */
    protected fun enableSecureScreen() {
        val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun applyThemeStyle() {
        val themePrefManager = com.engine.core.manager.ThemePrefManager.getInstance(this)
        val currentStyle = themePrefManager.themeStyle
        lastAppliedStyle = currentStyle
        val styleId = AppCore.getThemeStyle(currentStyle ?: "")

        if (styleId != 0) {
            setTheme(styleId)
        } else {
            // Default fallback if no custom style selected
            setTheme(R.style.Theme_NovelSerise)
        }
        // App-wide: no visible scrollbars on lists and scroll views.
        theme.applyStyle(R.style.ThemeOverlay_NovelSerise_NoScrollbars, true)
    }

    override fun onResume() {
        try {
            super.onResume()
        } catch (e: Exception) {
            // Guard against framework-level glitches during theme/locale changes.
            if (e is ClassCastException || e.message?.contains("ClientTransaction") == true) {
                // Ignore the glitch; the system will recover on the next frame.
            } else {
                throw e
            }
        }
        AppCore.setCurrentActivity(this)

        val themePrefManager = com.engine.core.manager.ThemePrefManager.getInstance(this)
        val currentStyle = themePrefManager.themeStyle
        if (lastAppliedStyle != null && lastAppliedStyle != currentStyle) {
            recreate()
        }
        lastAppliedStyle = currentStyle
    }

    override fun onPause() {
        super.onPause()
        if (AppCore.getCurrentActivity() == this) {
            AppCore.setCurrentActivity(null)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val sharedPrefs = newBase.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val languageCode = sharedPrefs.getString("app_language", Locale.getDefault().language) ?: "en"
        
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        
        // Create the configuration context for locale support
        val context = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(context)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        val root = findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0) 
            ?: findViewById(android.R.id.content)
        root?.let { applyEdgeToEdge(it) }
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        view?.let { applyEdgeToEdge(it) }
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        view?.let { applyEdgeToEdge(it) }
    }

    protected fun applyEdgeToEdge(rootView: View) {
        EdgeToEdgeHelper.applyEdgeToEdge(this, rootView)
    }
}
