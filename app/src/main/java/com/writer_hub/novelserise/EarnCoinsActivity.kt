/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.manager.GlobalAppData
import com.engine.core.viewmodel.AdsViewModel
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.UserViewModel
import com.writer_hub.novelserise.databinding.ActivityEarnCoinsBinding
import kotlinx.coroutines.launch

/**
 * Activity for users to watch ads and earn diamonds.
 */
class EarnCoinsActivity : BaseActivity() {

    private lateinit var binding: ActivityEarnCoinsBinding
    private lateinit var userViewModel: UserViewModel
    private lateinit var adsViewModel: AdsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEarnCoinsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)
        adsViewModel = AppProvider.getViewModel(application, AdsViewModel::class.java)

        setupToolbar()
        setupListeners()
        observeViewModel()

    }

    private fun setupToolbar() {
        binding.toolbar.tvTitle.text = "Earn Diamonds"
        binding.toolbar.btnBack.setOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.btnWatchAd.setOnClickListener {
            showRewardAd()
        }
    }

    private fun observeViewModel() {
        // Observe user details for diamond count
        GlobalAppData.userDetailsLiveData.observe(this) { userDetails ->
            userDetails?.userData?.let {
                binding.tvPointCount.text = it.diamond ?: "0"
            }
        }

        // Observe ad reward status
        adsViewModel.isRewarded.observe(this) { isRewarded ->
            AppLoggerHelper.d("EarnCoinsActivity", "isRewarded observer triggered: $isRewarded")
            if (isRewarded) {
                AppLoggerHelper.d("EarnCoinsActivity", "Ad reward confirmed, adding diamonds...")
                addDiamondsForAd()
                adsViewModel.resetValues() // Reset state after handling reward
            }
        }

        // Observe ad load failure
        adsViewModel.adLoadFailed.observe(this) { failed ->
            if (failed) {
                binding.progressBar.visibility = View.GONE
                binding.btnWatchAd.isEnabled = true
                Toast.makeText(
                    this,
                    "Failed to load ad. Please try again later.",
                    Toast.LENGTH_SHORT
                ).show()
                adsViewModel.resetAdLoadFailed()
            }
        }
    }

    private fun showRewardAd() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnWatchAd.isEnabled = false

        // Ensure state is clean before starting
        adsViewModel.resetValues()

        // Show rewarded ad - fixed AdsViewModel to prioritize "rewarded" type
        adsViewModel.showAppAd(this, "rewarded")

        // We'll hide the progress bar only if ad fails or shows. 
        // Using a slightly longer delay just for UI safety if nothing happens.
        binding.root.postDelayed({
            if (binding.progressBar.visibility == View.VISIBLE) {
                binding.progressBar.visibility = View.GONE
                binding.btnWatchAd.isEnabled = true
            }
        }, 5000)
    }

    private fun addDiamondsForAd() {
        runOnUiThread {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnWatchAd.isEnabled = false
        }

        userViewModel.addAdEarning { success ->
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                binding.btnWatchAd.isEnabled = true
                if (success) {
                    lifecycleScope.launch {
                        // The reward endpoint is authoritative. Fetch its new wallet value
                        // immediately instead of leaving the previous in-memory balance visible.
                        userViewModel.getUserDataDirect(true)
                        Toast.makeText(
                            this@EarnCoinsActivity,
                            "Success! Diamonds added to your account.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Reward processed, but balance update failed. Please refresh.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure UI is interactive when coming back from an ad
        binding.progressBar.visibility = View.GONE
        binding.btnWatchAd.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        adsViewModel.resetValues()
    }
}
