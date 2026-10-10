/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import androidx.appcompat.app.AppCompatDelegate
import com.engine.core.AppCore
import com.engine.core.manager.ThemePrefManager
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.SplashViewModel
import com.writer_hub.novelserise.ui.custom.AppSheet
import com.writer_hub.novelserise.ui.custom.OptionsDialog
import com.google.firebase.auth.FirebaseAuth
import com.writer_hub.novelserise.databinding.ActivitySettingsBinding
import com.writer_hub.novelserise.sheet.SwitchAccountBottomSheet
import com.writer_hub.novelserise.ui.subscription.SubscriptionPackActivity

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var themePrefManager: ThemePrefManager
    private lateinit var splashViewModel: SplashViewModel

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SettingsActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        themePrefManager = ThemePrefManager(this)
        
        splashViewModel = AppProvider.getViewModel(application, SplashViewModel::class.java)

        setupToolbar()
        loadPreferences() // Load preferences BEFORE setting listeners to avoid trigger loops
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupListeners() {
        // Policy Section
        binding.tvPolicyTerms.setOnClickListener { showPolicy("Terms and Conditions", getString(R.string.placeholder_terms_conditions), R.drawable.set_badge_terms) }
        binding.tvPolicyPrivacy.setOnClickListener { showPolicy("Privacy Policy", getString(R.string.placeholder_privacy_policy), R.drawable.set_badge_privacy) }
        binding.tvPolicyRefund.setOnClickListener { showPolicy("Refund Policy", getString(R.string.placeholder_refund_policy), R.drawable.set_badge_refund) }
        binding.tvPolicyCancellation.setOnClickListener { showPolicy("Cancellation Policy", getString(R.string.placeholder_cancellation_policy), R.drawable.set_badge_cancel) }
        binding.tvPolicyAboutUs.setOnClickListener { showPolicy("About Us", getString(R.string.placeholder_about_us), R.drawable.set_badge_about) }

        // Help Section
        binding.tvHelp.setOnClickListener {
            HelpSupportActivity.start(this)
        }

        // Logout
        binding.tvLogout.setOnClickListener { showLogoutConfirmation() }

        // Refer and Earn
        binding.tvReferEarn.setOnClickListener {
            ReferEarnActivity.start(this)
        }

        // Transaction History
        binding.tvTransactionHistory.setOnClickListener {
            TransactionHistoryActivity.start(this, "0", "user", "diamond")
        }

        // Writer Subscription
        binding.tvWriterSubscription.setOnClickListener {
            val intent = Intent(this, SubscriptionPackActivity::class.java)
            startActivity(intent)
        }

        // Switch Account
        binding.tvSwitchAccount.setOnClickListener {
            val switchAccountSheet = SwitchAccountBottomSheet()
            switchAccountSheet.show(supportFragmentManager, SwitchAccountBottomSheet.TAG)
        }

        // Theme Switch
        binding.switchTheme.setOnCheckedChangeListener { _, isChecked ->
            val themeMode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            
            if (themePrefManager.themeMode != themeMode) {
                themePrefManager.themeMode = themeMode
                // Post to Handler to give the system time to handle the click and avoid 
                // the relaunch transaction conflict on MIUI (BinderProxy ClassCastException).
                Handler(Looper.getMainLooper()).postDelayed({
                    AppCompatDelegate.setDefaultNightMode(themeMode)
                }, 100)
            }
        }

        // Language Switch
        binding.tvLanguage.setOnClickListener { showLanguageSelectionDialog() }

        binding.tvViewDeveloperProfile.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(getString(com.engine.core.R.string.developer_url))
            )
            startActivity(intent)
        }
    }

    private fun loadPreferences() {
        // 1. Handle Theme
        val isDarkMode = themePrefManager.themeMode == AppCompatDelegate.MODE_NIGHT_YES
        
        // Temporarily remove listener to prevent recreation during initial setup
        binding.switchTheme.setOnCheckedChangeListener(null)
        binding.switchTheme.isChecked = isDarkMode
        
        // 2. Handle Language Text
        val currentLang = splashViewModel.getContentLanguage()
        binding.tvLanguageSelected.text = when (currentLang) {
            "en" -> "English"
            "hi" -> "Hindi"
            "mr" -> "Marathi"
            else -> "English"
        }
    }

    private fun showPolicy(title: String, content: String, icon: Int) {
        AppSheet.info(this, title, content, icon = icon)
    }

    private fun showLogoutConfirmation() {
        AppSheet.confirm(
            context = this,
            title = "Log out?",
            message = "You'll need to sign in again to continue reading on this device.",
            confirmText = "Log out",
            icon = R.drawable.set_badge_logout,
            destructive = true
        ) {
            firebaseAuth.signOut()
            AppCore.tokenManager.clearTokenAndUserId()
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf("English", "Hindi", "Marathi")
        val languageCodes = arrayOf("en", "hi", "mr")
        var checkedItem = 0

        val currentLangCode = splashViewModel.getContentLanguage()

        languageCodes.forEachIndexed { index, code ->
            if (code == currentLangCode) {
                checkedItem = index
            }
        }

        val nativeNames = arrayOf("English", "हिन्दी", "मराठी")
        OptionsDialog.show(
            context = this,
            title = "Choose language",
            subtitle = "Stories and the app will use this language",
            options = languages.indices.map { i ->
                OptionsDialog.Option(nativeNames[i], if (nativeNames[i] == languages[i]) null else languages[i], R.drawable.ic_set_globe)
            },
            selectedIndex = checkedItem
        ) { which ->
            setLocale(languageCodes[which])
            binding.tvLanguageSelected.text = languages[which]
        }
    }

    private fun setLocale(languageCode: String) {
        val sharedPrefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        
        val currentLang = splashViewModel.getContentLanguage()
        if (currentLang == languageCode) return

        with(sharedPrefs.edit()) {
            putString("app_language", languageCode)
            apply()
        }
        
        splashViewModel.saveSelectedLanguage(languageCode)

        AppProvider.clearAllViewModels(application)
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
