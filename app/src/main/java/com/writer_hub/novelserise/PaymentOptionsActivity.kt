/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import com.engine.core.manager.GlobalAppData
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import com.writer_hub.novelserise.databinding.ActivityPaymentOptionsBinding
import com.writer_hub.novelserise.databinding.DialogPaymentSuccessBinding
import com.engine.core.helper.AlertUtils
import com.engine.core.helper.AppEventLogger
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.models.DiamondOrderResponse
import com.engine.core.models.DiamondPack
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.UserViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject

class PaymentOptionsActivity : BaseActivity(), PaymentResultListener {

    private lateinit var binding: ActivityPaymentOptionsBinding
    private var diamondPack: DiamondPack? = null
    private var razorpayKeyId: String? = null
    private lateinit var userViewModel: UserViewModel
    private var isProcessCancelled = false

    companion object {
        private const val EXTRA_DIAMOND_PACK = "extra_diamond_pack"

        fun start(context: Context, diamondPack: DiamondPack) {
            val intent = Intent(context, PaymentOptionsActivity::class.java).apply {
                putExtra(EXTRA_DIAMOND_PACK, diamondPack)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)

        setupToolbar()
        validateConfig()
        setupUI()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun validateConfig() {
        razorpayKeyId = GlobalAppData.siteSettings?.config?.razorpayKeyId
        if (razorpayKeyId.isNullOrBlank()) {
            Toast.makeText(this, "Payment configuration missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        diamondPack = intent.getParcelableExtra(EXTRA_DIAMOND_PACK)
        if (diamondPack == null) {
            Toast.makeText(this, "Invalid diamond pack data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun setupUI() {
        binding.subTitle.text = "Buy ${diamondPack?.diamonds} Diamonds for ₹${diamondPack?.amount}"
    }

    private fun setupListeners() {
        binding.cardGooglePay.setOnClickListener { startRazorpayPayment("Google Pay") }
        binding.cardPhonepe.setOnClickListener { startRazorpayPayment("PhonePe") }
        binding.cardPaytm.setOnClickListener { startRazorpayPayment("Paytm") }
    }

    private fun startRazorpayPayment(selectedMethod: String) {
        val sku = diamondPack?.sku
        if (sku.isNullOrBlank()) {
            Toast.makeText(this, "Invalid diamond pack", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessCancelled = false
        AlertUtils.showLoadingDialog(this, "Creating payment... please wait", true) {
            isProcessCancelled = true
        }

        userViewModel.createDiamondOrder(sku) { response ->
            if (isProcessCancelled) return@createDiamondOrder
            AlertUtils.dismissLoadingDialog()

            if (response?.status == "success") {
                launchRazorpayCheckout(selectedMethod, response)
            } else {
                Toast.makeText(
                    this,
                    response?.message ?: "Failed to create order",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun launchRazorpayCheckout(selectedMethod: String, response: DiamondOrderResponse) {
        val keyId = response.razorpayKeyId ?: razorpayKeyId
        if (keyId.isNullOrBlank()) {
            Toast.makeText(this, "Payment config missing", Toast.LENGTH_SHORT).show()
            return
        }

        val checkout = Checkout()
        checkout.setKeyID(keyId)

        try {
            val options = JSONObject().apply {
                put("name", "NovelSeries")
                put("description", "${diamondPack?.diamonds} Diamonds Pack")
                put("currency", response.currency ?: "INR")
                put("amount", response.amount ?: 0)
                put("order_id", response.orderId ?: "")

                put("method", "upi")

                val upiOptions = JSONObject().apply {
                    put("flow", "intent")
                    val upiApp = when (selectedMethod) {
                        "Google Pay" -> "com.google.android.apps.nbu.paisa.user"
                        "PhonePe" -> "com.phonepe.app"
                        "Paytm" -> "net.one97.paytm"
                        else -> null
                    }
                    if (upiApp != null) {
                        put("app", upiApp)
                    }
                }
                put("upi", upiOptions)

                val user = GlobalAppData.userDetails?.userData
                val prefill = JSONObject().apply {
                    put("email", user?.email ?: "")
                    put("contact", user?.phnNumber ?: "")
                    put("name", user?.name ?: "")
                }
                put("prefill", prefill)
            }

            checkout.open(this, options)

        } catch (e: Exception) {
            AppLoggerHelper.e("PaymentOptionsActivity", "Error: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentID: String?) {
        if (razorpayPaymentID == null) return
        
        AlertUtils.showLoadingDialog(this, "Verifying payment...")

        userViewModel.verifyDiamondPurchase(razorpayPaymentID) { verified ->
            AlertUtils.dismissLoadingDialog()

            if (verified) {
                diamondPack?.let { pack ->
                    val amount = pack.amount.toDoubleOrNull() ?: 0.0
                    
                    // Track individual success event with SKU for parity with Play Store
                    AppEventLogger.logEvent("diamond_purchase_verified_${pack.sku}", mapOf(
                        "sku" to pack.sku,
                        "method" to "razorpay",
                        "payment_id" to razorpayPaymentID
                    ))

                    // Log standard unified purchase event
                    AppEventLogger.logPurchase(
                        amount = amount,
                        currency = "INR",
                        productId = pack.sku,
                        type = "diamond_razorpay"
                    )
                }

                lifecycleScope.launch {
                    userViewModel.getUserDataDirect(true)
                    showSuccessDialog()
                }
            } else {
                Toast.makeText(this, "Verification failed. Please contact support.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSuccessDialog() {
        val dialog = Dialog(this)
        val dialogBinding = DialogPaymentSuccessBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.tvSuccessMessage.text = "${diamondPack?.diamonds} Diamonds have been added to your account."
        
        dialogBinding.btnDone.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.show()
    }

    override fun onPaymentError(code: Int, response: String?) {
        AlertUtils.dismissLoadingDialog()
        if (code == Checkout.PAYMENT_CANCELED) {
            Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Payment failed: $response", Toast.LENGTH_SHORT).show()
        }
    }
}
