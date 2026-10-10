/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import com.writer_hub.novelserise.databinding.ActivityReferEarnBinding
import com.engine.core.manager.GlobalVariableManager

class ReferEarnActivity : BaseActivity() {

    private lateinit var binding: ActivityReferEarnBinding

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ReferEarnActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReferEarnBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        displayReferralCode()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Refer & Earn"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun displayReferralCode() {
        val referralCode = GlobalVariableManager.getUserReferrerCodeForInvite()
        if (referralCode.isNotEmpty()) {
            binding.tvReferralCode.text = referralCode
        } else {
            binding.tvReferralCode.text = "NOT AVAILABLE"
        }
    }

    private fun setupListeners() {
        binding.ivCopyCode.setOnClickListener {
            val code = binding.tvReferralCode.text.toString()
            if (code.isNotEmpty() && code != "LOADING..." && code != "NOT AVAILABLE") {
                copyToClipboard(code)
            }
        }

        binding.btnShare.setOnClickListener {
            val referralCode = GlobalVariableManager.getUserReferrerCode()
            if (referralCode.isNotEmpty()) {
                val referralLink = GlobalVariableManager.getReferralLink("", "") // Passing empty IDs for general app referral
                shareReferralLink(referralLink ?: "Check out this app! Use my referral code: $referralCode")
            } else {
                Toast.makeText(this, "Referral code not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Referral Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun shareReferralLink(link: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, link)
        startActivity(Intent.createChooser(intent, "Share via"))
    }
}