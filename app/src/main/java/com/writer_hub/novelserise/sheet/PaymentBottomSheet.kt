/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.sheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.writer_hub.novelserise.databinding.BottomSheetPaymentOptionsBinding
import com.engine.core.manager.GlobalAppData

class PaymentBottomSheet(
    private val onOptionSelected: (PaymentOption) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPaymentOptionsBinding? = null
    private val binding get() = _binding!!

    enum class PaymentOption {
        PLAY_STORE,
        OTHER_PAYMENT
    }

    companion object {
        /** Server sends razorpay_payment as "1" or "yes"; accept either so the sheet and its Razorpay row agree. */
        fun isRazorpayEnabled(): Boolean {
            val value = GlobalAppData.siteSettings?.config?.razorpayPayment?.trim() ?: return false
            return value == "1" || value.equals("yes", true) || value.equals("true", true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPaymentOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Conditionally show Razorpay option
        if (isRazorpayEnabled()) {
            binding.optionOtherPayment.visibility = View.VISIBLE
            binding.optionOtherPayment.setOnClickListener {
                onOptionSelected(PaymentOption.OTHER_PAYMENT)
                dismiss()
            }
        } else {
            binding.optionOtherPayment.visibility = View.GONE
        }

        binding.optionPlayStore.setOnClickListener {
            onOptionSelected(PaymentOption.PLAY_STORE)
            dismiss()
        }

        binding.optionCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
