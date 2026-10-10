/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.fragment

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.helper.AlertUtils
import com.engine.core.helper.BillingHelper
import com.engine.core.manager.GlobalAppData
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.models.DiamondPack
import com.engine.core.models.WriterSubscriptionPack
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.UserViewModel
import com.writer_hub.novelserise.EarnCoinsActivity
import com.writer_hub.novelserise.PaymentOptionsActivity
import com.writer_hub.novelserise.adapter.DiamondPackAdapter
import com.writer_hub.novelserise.adapter.WriterPackAdapter
import com.writer_hub.novelserise.databinding.DialogPaymentSuccessBinding
import com.writer_hub.novelserise.databinding.FragmentShopBinding
import com.writer_hub.novelserise.sheet.PaymentBottomSheet
import com.writer_hub.novelserise.ui.writerSubscriptionPack.WriterSubscriptionPackActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ShopFragment : Fragment() {

    private var _binding: FragmentShopBinding? = null
    private val binding get() = _binding!!

    private lateinit var userViewModel: UserViewModel
    private lateinit var diamondAdapter: DiamondPackAdapter
    private lateinit var subscriptionAdapter: WriterPackAdapter
    private var shouldRetryPendingPurchases = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userViewModel = AppProvider.getViewModel(requireActivity().application, UserViewModel::class.java)

        setupUI()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data whenever the fragment becomes visible (e.g., returning from purchase activity)
        refreshData()

        // A Play purchase can complete while server verification temporarily fails. The billing
        // callback is not replayed until the app process restarts, so retry owned/unconsumed items
        // whenever Shop opens and after returning from the Play purchase sheet.
        if (shouldRetryPendingPurchases) {
            shouldRetryPendingPurchases = false
            _binding?.root?.postDelayed({
                if (isAdded) {
                    BillingHelper.init(requireActivity().application)
                    BillingHelper.handlePendingPurchases()
                }
            }, 2_500L)
        }
    }

    private fun setupUI() {
        _binding?.let { binding ->
            // Setup Diamond Packs
            binding.rvDiamondPacks.layoutManager = GridLayoutManager(context ?: return@let, 3)
            binding.rvDiamondPacks.itemAnimator = null
            val diamondPacks = GlobalAppData.siteSettings?.config?.diamondPacks ?: emptyList()
            diamondAdapter = DiamondPackAdapter(diamondPacks) { pack ->
                showPaymentOptions(pack)
            }
            binding.rvDiamondPacks.adapter = diamondAdapter

            // Setup Subscription Packs
            binding.rvSubscriptions.layoutManager = LinearLayoutManager(context ?: return@let)
            subscriptionAdapter = WriterPackAdapter(
                onBuyClick = { pack ->
                    handleSubscriptionClick(pack)
                },
                onCancelClick = { pack ->
                    handleSubscriptionCancellation(pack)
                }
            )
            binding.rvSubscriptions.itemAnimator = null
            binding.rvSubscriptions.adapter = subscriptionAdapter

            // Wallet "Top up" jumps to the diamond packs.
            binding.btnTopUp.setOnClickListener {
                val scroll = binding.rvDiamondPacks.parent.parent as? androidx.core.widget.NestedScrollView
                scroll?.smoothScrollTo(0, binding.rvDiamondPacks.top - resources.displayMetrics.density.times(64).toInt())
            }

            // Setup Earn Coins/Diamonds
            binding.cardEarnCoins.setOnClickListener {
                startActivity(
                    Intent(
                        context ?: return@setOnClickListener,
                        EarnCoinsActivity::class.java
                    )
                )
            }

            binding.swipeRefreshLayout.setOnRefreshListener {
                refreshData()
            }
        }
    }

    private fun setupObservers() {
        GlobalAppData.userDetailsLiveData.observe(viewLifecycleOwner) { userDetails ->
            _binding?.let { binding ->
                binding.tvDiamondBalance.text = userDetails?.userData?.diamond ?: "0"
                binding.tvPointsBalance.text = "${userDetails?.userData?.points ?: "0"} Points"
                // Re-load data to filter packs if user has active subscription
                loadData()
            }
        }

        GlobalAppData.siteSettingsLiveData.observe(viewLifecycleOwner) {
            loadData()
        }

        // Observe purchase success events for Play Store
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.purchaseSuccessEvent.collectLatest { event ->
                    if (event.type == "diamond") {
                        // The billing screen resumes before server-side verification can finish.
                        // Refresh again only after verification succeeds so the credited balance
                        // replaces the stale value loaded by onResume().
                        userViewModel.getUserDataDirect(true)
                        val pack =
                            GlobalAppData.siteSettings?.config?.diamondPacks?.find { it.sku == event.productId }
                        showSuccessDialog(pack)
                    } else if (event.type == "subscription") {
                        userViewModel.getUserDataDirect(true)
                        val pack =
                            GlobalAppData.siteSettings?.config?.subscriptionPacks?.find { it.playstorePlanId == event.productId }
                        showSubscriptionSuccessDialog(pack)
                    }
                }
            }
        }
    }

    private fun refreshData() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Force refresh user data to get the latest subscription status
            userViewModel.getUserDataDirect(true)

            val binding = _binding ?: return@launch
            // Also fetch subscription packs directly to ensure we have the latest server flags
            loadData()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun loadData() {
        val siteConfig = GlobalAppData.siteSettings
        val subscriptionPacks = siteConfig?.config?.subscriptionPacks ?: emptyList()
        
        // Find if there is any active subscription
        val activePack = subscriptionPacks.find { pack ->
            GlobalVariableManager.isSpecificPackActive(pack.id)
        }
        
        // If user has an active pack, only show that one. Otherwise show all.
        val displayPacks = if (activePack != null) {
            listOf(activePack)
        } else {
            subscriptionPacks
        }

        if (::subscriptionAdapter.isInitialized) {
            subscriptionAdapter.submitList(displayPacks)
        }
        _binding?.let {
            val visibility = if (displayPacks.isEmpty()) View.GONE else View.VISIBLE
            it.subscriptionsHeader.visibility = visibility
            it.rvSubscriptions.visibility = visibility
        }
    }

    private fun showPaymentOptions(pack: DiamondPack) {
        val isRazorpayEnabled = PaymentBottomSheet.isRazorpayEnabled()

        if (isRazorpayEnabled) {
            val bottomSheet = PaymentBottomSheet { option ->
                when (option) {
                    PaymentBottomSheet.PaymentOption.PLAY_STORE -> {
                        shouldRetryPendingPurchases = true
                        activity?.let { BillingHelper.startPurchaseFlow(it, pack.sku) }
                    }

                    PaymentBottomSheet.PaymentOption.OTHER_PAYMENT -> {
                        context?.let { PaymentOptionsActivity.start(it, pack) }
                    }
                }
            }
            bottomSheet.show(childFragmentManager, "PaymentBottomSheet")
        } else {
            // Directly start Play Store if Razorpay is disabled
            shouldRetryPendingPurchases = true
            activity?.let { BillingHelper.startPurchaseFlow(it, pack.sku) }
        }
    }

    private fun handleSubscriptionClick(pack: WriterSubscriptionPack) {
        val isRazorpayEnabled = PaymentBottomSheet.isRazorpayEnabled()

        if (isRazorpayEnabled) {
            val bottomSheet = PaymentBottomSheet { option ->
                when (option) {
                    PaymentBottomSheet.PaymentOption.PLAY_STORE -> {
                        val playstoreId = pack.playstorePlanId
                        if (!playstoreId.isNullOrEmpty()) {
                            shouldRetryPendingPurchases = true
                            activity?.let { BillingHelper.startPurchaseFlow(it, playstoreId, true) }
                        } else {
                            Toast.makeText(
                                context,
                                "Play Store plan not configured",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    PaymentBottomSheet.PaymentOption.OTHER_PAYMENT -> {
                        openSubscriptionPack(pack)
                    }
                }
            }
            bottomSheet.show(childFragmentManager, "SubscriptionPaymentBottomSheet")
        } else {
            // Directly start Play Store if Razorpay is disabled
            val playstoreId = pack.playstorePlanId
            if (!playstoreId.isNullOrEmpty()) {
                shouldRetryPendingPurchases = true
                activity?.let { BillingHelper.startPurchaseFlow(it, playstoreId, true) }
            } else {
                Toast.makeText(
                    context,
                    "Play Store plan not configured",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openSubscriptionPack(pack: WriterSubscriptionPack) {
        val intent = Intent(context ?: return, WriterSubscriptionPackActivity::class.java).apply {
            putExtra("EXTRA_PACK_ID", pack.id)
        }
        startActivity(intent)
    }

    private fun handleSubscriptionCancellation(pack: WriterSubscriptionPack) {
        val currentContext = context ?: return
        val currentActivity = activity ?: return

        androidx.appcompat.app.AlertDialog.Builder(currentContext)
            .setTitle("Cancel Subscription?")
            .setMessage("Are you sure you want to cancel '${pack.name}'?")
            .setPositiveButton("Yes") { _, _ ->
                AlertUtils.showLoadingDialog(currentActivity, "Cancelling...")
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = userViewModel.cancelSubscription(pack.id, "Cancelled by user")
                    AlertUtils.dismissLoadingDialog()
                    if (success) {
                        Toast.makeText(context, "Cancelled successfully", Toast.LENGTH_SHORT).show()
                        userViewModel.getUserDataDirect(true)
                        // loadData() will be triggered by userDetailsLiveData observer
                    } else {
                        Toast.makeText(context, "Failed to cancel", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showSuccessDialog(pack: DiamondPack?) {
        val currentActivity = activity ?: return
        val dialog = Dialog(currentActivity)
        val dialogBinding = DialogPaymentSuccessBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val diamondCount = pack?.diamonds ?: "Diamonds"
        dialogBinding.tvSuccessMessage.text = "$diamondCount have been added to your account."

        dialogBinding.btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSubscriptionSuccessDialog(pack: WriterSubscriptionPack?) {
        val currentActivity = activity ?: return
        val dialog = Dialog(currentActivity)
        val dialogBinding = DialogPaymentSuccessBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val packName = pack?.name ?: "Subscription"
        dialogBinding.tvSuccessMessage.text = "You have successfully subscribed to $packName."

        dialogBinding.btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
