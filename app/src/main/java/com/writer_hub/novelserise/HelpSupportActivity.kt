/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import com.engine.core.manager.GlobalAppData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.writer_hub.novelserise.databinding.ActivityHelpSupportBinding

class HelpSupportActivity : BaseActivity() {

    private lateinit var binding: ActivityHelpSupportBinding

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, HelpSupportActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadContactInfo()
        setupListeners()
        
        // Final fallback to ensure something is always shown
        if (binding.tvEmail.text.isNullOrEmpty()) {
            binding.tvEmail.text = "support@novelseries.com"
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.help)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadContactInfo() {
        // Observe LiveData in case settings are updated asynchronously
        GlobalAppData.siteSettingsLiveData.observe(this) { settings ->
            settings?.config?.let {
                Log.d("HelpSupport", "Settings updated: ${it.siteEmail}")
                if (!it.siteEmail.isNullOrBlank()) {
                    binding.tvEmail.text = it.siteEmail
                }
            }
        }

        // Also check immediate value
        val config = GlobalAppData.siteSettings?.config
        config?.let {
            Log.d("HelpSupport", "Immediate config found: ${it.siteEmail}")
            if (!it.siteEmail.isNullOrBlank()) {
                binding.tvEmail.text = it.siteEmail
            }
        }
    }

    private fun setupListeners() {
        val config = GlobalAppData.siteSettings?.config

        binding.cardEmail.setOnClickListener {
            val email = if (config?.siteEmail.isNullOrBlank()) "support@novelseries.com" else config!!.siteEmail
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, "Support Request: ${getString(R.string.app_name)}")
            }
            try {
                startActivity(Intent.createChooser(intent, "Send Email"))
            } catch (e: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardWhatsApp.setOnClickListener {
            // Priority: WhatsApp from config (if available), then social link, then default
            val whatsappUrl = "https://wa.me/919999999999" // TODO: Add to config if needed
            openUrl(whatsappUrl)
        }

        binding.ivFacebook.setOnClickListener { config?.facebookLink?.let { openUrl(it) } }
        binding.ivInstagram.setOnClickListener { config?.instagramLink?.let { openUrl(it) } }
        binding.ivTelegram.setOnClickListener { config?.telegramLink?.let { openUrl(it) } }
        binding.ivYouTube.setOnClickListener { config?.youtubeLink?.let { openUrl(it) } }
    }

    private fun openUrl(url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Link not available", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }
}