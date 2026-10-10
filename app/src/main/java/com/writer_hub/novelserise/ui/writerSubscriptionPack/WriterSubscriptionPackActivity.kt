/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.writerSubscriptionPack

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.AlertUtils
import com.engine.core.helper.AppEventLogger
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.WriterSubscriptionPack
import com.engine.core.utill.EdgeToEdgeHelper
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.UserViewModel
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import com.writer_hub.novelserise.BaseActivity
import com.writer_hub.novelserise.adapter.WriterPackAdapter
import com.writer_hub.novelserise.databinding.ActivityWriterSubscriptionPackBinding
import kotlinx.coroutines.launch
import org.json.JSONObject

class WriterSubscriptionPackActivity : BaseActivity(), PaymentResultListener {

    private lateinit var binding: ActivityWriterSubscriptionPackBinding
    private lateinit var userViewModel: UserViewModel
    private lateinit var packAdapter: WriterPackAdapter
    private var lastSelectedPack: WriterSubscriptionPack? = null
    private var autoBuyPackId: Int = -1
    private var isProcessCancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriterSubscriptionPackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EdgeToEdgeHelper.applyEdgeToEdge(this, binding.root)

        userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)

        autoBuyPackId = intent.getIntExtra("EXTRA_PACK_ID", -1)

        setupToolbar()
        setupRecyclerView()
        loadWriterSubscriptionPacks()
    }

    private fun setupToolbar() {
        binding.toolbarLayout.tvTitle.text = "Writer Subscription Packs"
        binding.toolbarLayout.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        setupCenterSnapCarousel(binding.recyclerViewWriterPacks, peekWidthDp = 32)
        
        packAdapter = WriterPackAdapter(
            onBuyClick = { selectedPack ->
                initiateSubscription(selectedPack)
            },
            onCancelClick = { selectedPack ->
                handleSubscriptionCancellation(selectedPack)
            }
        )
        binding.recyclerViewWriterPacks.adapter = packAdapter
    }

    private fun setupCenterSnapCarousel(recyclerView: RecyclerView, peekWidthDp: Int) {
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val padding = (peekWidthDp * resources.displayMetrics.density).toInt()
        recyclerView.setPadding(padding, 0, padding, 0)
        recyclerView.clipToPadding = false
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)
    }

    private fun initiateSubscription(selectedPack: WriterSubscriptionPack) {
        lastSelectedPack = selectedPack
        isProcessCancelled = false
        AlertUtils.showLoadingDialog(this, "Creating payment... please wait", true) {
            isProcessCancelled = true
        }

        userViewModel.subscribeToWriterPack(selectedPack.id) { response ->
            if (isProcessCancelled) return@subscribeToWriterPack
            AlertUtils.dismissLoadingDialog()

            if (response != null && response.status == "success") {
                openRazorpayCheckout(selectedPack, response.subscriptionId, response.razorpayKeyId)
            } else {
                Toast.makeText(this, "Subscription failed to initialize.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadWriterSubscriptionPacks() {
        val siteConfig = GlobalAppData.siteSettings
        val packs = siteConfig?.config?.subscriptionPacks ?: emptyList()
        val sortedPacks = packs.sortedWith(
            compareByDescending<WriterSubscriptionPack> { it.isFeaturedInt }
                .thenByDescending { it.priority?.toIntOrNull() ?: 0 }
        )
        packAdapter.submitList(sortedPacks)
        
        if (autoBuyPackId != -1) {
            val packToAutoBuy = sortedPacks.find { it.id == autoBuyPackId }
            if (packToAutoBuy != null) {
                binding.recyclerViewWriterPacks.postDelayed({
                    initiateSubscription(packToAutoBuy)
                }, 500)
            }
            autoBuyPackId = -1
        }
    }

    private fun handleSubscriptionCancellation(packToCancel: WriterSubscriptionPack) {
        val packId = packToCancel.id
        // Using standard MaterialAlertDialogBuilder since CustomInfoDialog might not be in novelserise
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cancel Subscription?")
            .setMessage("Are you sure you want to cancel your '${packToCancel.name}' subscription? This action cannot be undone.")
            .setPositiveButton("Yes") { _, _ ->
                AlertUtils.showLoadingDialog(this, "Cancelling subscription...")
                lifecycleScope.launch {
                    val success = userViewModel.cancelSubscription(packId, "Cancelled by user")
                    AlertUtils.dismissLoadingDialog()

                    if (success) {
                        Toast.makeText(this@WriterSubscriptionPackActivity, "Subscription cancelled.", Toast.LENGTH_LONG).show()
                        userViewModel.getUserDataDirect(true)
                        loadWriterSubscriptionPacks()
                    } else {
                        Toast.makeText(this@WriterSubscriptionPackActivity, "Could not cancel. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun openRazorpayCheckout(
        pack: WriterSubscriptionPack,
        razorpayId: String,
        razorpayKey: String
    ) {
        val checkout = Checkout()
        checkout.setKeyID(razorpayKey)

        try {
            val options = JSONObject().apply {
                put("name", "NovelSeries")
                put("currency", "INR")
                put("description", pack.description ?: pack.name ?: "NovelSeries Pack")

                if (pack.isOneTimeInt == 1) {
                    put("order_id", razorpayId)
                } else {
                    put("subscription_id", razorpayId)
                }

                put("method", "upi")
                put("upi", JSONObject().apply {
                    put("flow", "intent")
                })

                val user = GlobalAppData.userDetails?.userData
                put("prefill", JSONObject().apply {
                    put("email", user?.email ?: "")
                    put("contact", user?.phnNumber ?: "")
                    put("name", user?.name ?: "")
                })
            }

            checkout.open(this, options)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error starting payment: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String) {
        AlertUtils.showLoadingDialog(this, "Verifying your subscription...")

        userViewModel.verifyPlatformWriterSubscriptionPack(razorpayPaymentId) { verified, message ->
            AlertUtils.dismissLoadingDialog()

            if (verified) {
                lastSelectedPack?.let { pack ->
                    AppEventLogger.logPurchase(
                        amount = pack.price,
                        currency = "INR",
                        productId = pack.id.toString(),
                        type = "membership"
                    )
                }

                Toast.makeText(this, "Subscription Verified Successfully!", Toast.LENGTH_SHORT).show()

                lifecycleScope.launch {
                    userViewModel.getUserDataDirect(true)
                    Toast.makeText(this@WriterSubscriptionPackActivity, "Your subscription has been activated!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        val errorMessage = "Payment failed. Code: $code, Response: $response"
        Toast.makeText(this, "Payment failed", Toast.LENGTH_LONG).show()
        AppLoggerHelper.e("WriterSubPackActivity", errorMessage)
    }
}
