/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.subscription

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.writer_hub.novelserise.databinding.ActivitySubscriptionPackBinding
import kotlinx.coroutines.launch
import org.json.JSONObject

class SubscriptionPackActivity : BaseActivity(), PaymentResultListener {

    private lateinit var binding: ActivitySubscriptionPackBinding
    private lateinit var userViewModel: UserViewModel
    private lateinit var packAdapter: WriterPackAdapter
    private var lastSelectedPack: WriterSubscriptionPack? = null
    private var autoBuyPackId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionPackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EdgeToEdgeHelper.applyEdgeToEdge(this, binding.root)

        userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)

        autoBuyPackId = intent.getIntExtra("EXTRA_PACK_ID", -1)

        setupToolbar()
        setupRecyclerView()
        loadSubscriptionPacks()
    }

    private fun setupToolbar() {
        binding.toolbarLayout.tvTitle.text = "Subscription Packs"
        binding.toolbarLayout.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerViewPacks.layoutManager = LinearLayoutManager(this)
        
        packAdapter = WriterPackAdapter(
            onBuyClick = { selectedPack ->
                initiateSubscription(selectedPack)
            },
            onCancelClick = { selectedPack ->
                handleSubscriptionCancellation(selectedPack)
            }
        )
        binding.recyclerViewPacks.adapter = packAdapter
    }

    private fun initiateSubscription(selectedPack: WriterSubscriptionPack) {
        lastSelectedPack = selectedPack
        AlertUtils.showLoadingDialog(this, "Creating payment... please wait")

        userViewModel.subscribeToWriterPack(selectedPack.id) { response ->
            AlertUtils.dismissLoadingDialog()

            if (response != null && response.status == "success") {
                openRazorpayCheckout(selectedPack, response.subscriptionId, response.razorpayKeyId)
            } else {
                Toast.makeText(this, "Subscription failed to initialize.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSubscriptionPacks() {
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
                binding.recyclerViewPacks.postDelayed({
                    initiateSubscription(packToAutoBuy)
                }, 500)
            }
            autoBuyPackId = -1
        }
    }

    private fun handleSubscriptionCancellation(packToCancel: WriterSubscriptionPack) {
        val packId = packToCancel.id
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cancel Subscription?")
            .setMessage("Are you sure you want to cancel your '${packToCancel.name}' subscription? This action cannot be undone.")
            .setPositiveButton("Yes") { _, _ ->
                AlertUtils.showLoadingDialog(this, "Cancelling subscription...")
                lifecycleScope.launch {
                    val success = userViewModel.cancelSubscription(packId, "Cancelled by user")
                    AlertUtils.dismissLoadingDialog()

                    if (success) {
                        Toast.makeText(this@SubscriptionPackActivity, "Subscription cancelled.", Toast.LENGTH_LONG).show()
                        userViewModel.getUserDataDirect(true)
                        loadSubscriptionPacks()
                    } else {
                        Toast.makeText(this@SubscriptionPackActivity, "Could not cancel. Please try again.", Toast.LENGTH_SHORT).show()
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
                put("description", pack.description ?: pack.name ?: "Subscription Pack")

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
                    Toast.makeText(this@SubscriptionPackActivity, "Your subscription has been activated!", Toast.LENGTH_SHORT).show()
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
        AppLoggerHelper.e("SubscriptionPackActivity", errorMessage)
    }
}
