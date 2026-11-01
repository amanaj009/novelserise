/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import com.writer_hub.novelserise.databinding.ActivityHelpSupportBinding

class HelpSupportActivity : BaseActivity() {

    private lateinit var binding: ActivityHelpSupportBinding

    companion object {
        private const val SUPPORT_EMAIL = "novelseries9@gmail.com"
        private const val WHATSAPP_URL = "https://wa.me/message/RQGISU3I75V6P1"
        private const val TELEGRAM_URL = "https://t.me/The_Horror_Hunter"
        private const val INSTAGRAM_URL = "https://www.instagram.com/the_horror_hunter?igsh=ODJwbWRrNW9mcm9o"
        private const val YOUTUBE_URL = "https://www.youtube.com/channel/UCAz9S8jpCKAdEf1_YHKUiMg"
        private const val FACEBOOK_URL = "https://www.facebook.com/share/1M6mB6knGE/"

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
        binding.tvEmail.text = SUPPORT_EMAIL
    }

    private fun setupListeners() {
        binding.cardEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$SUPPORT_EMAIL")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, "Support Request: ${getString(R.string.app_name)}")
            }
            try {
                startActivity(Intent.createChooser(intent, "Send Email"))
            } catch (e: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardWhatsApp.setOnClickListener {
            openUrl(WHATSAPP_URL)
        }

        binding.ivFacebook.setOnClickListener { openUrl(FACEBOOK_URL) }
        binding.ivInstagram.setOnClickListener { openUrl(INSTAGRAM_URL) }
        binding.ivTelegram.setOnClickListener { openUrl(TELEGRAM_URL) }
        binding.ivYouTube.setOnClickListener { openUrl(YOUTUBE_URL) }
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
