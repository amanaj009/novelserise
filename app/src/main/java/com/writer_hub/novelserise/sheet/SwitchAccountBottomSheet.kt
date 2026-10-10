/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.sheet

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.engine.core.AppCore
import com.engine.core.models.MiniUser
import com.engine.core.viewmodel.AppProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.writer_hub.novelserise.AuthActivity
import com.writer_hub.novelserise.SplashActivity
import com.writer_hub.novelserise.adapter.MiniUserAdapter
import com.writer_hub.novelserise.databinding.LayoutSwitchAccountBinding

class SwitchAccountBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutSwitchAccountBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutSwitchAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tokenManager = AppCore.tokenManager
        val users = tokenManager.getAllUsers()
        val currentUserId = tokenManager.getCurrentUserId()

        val adapter = MiniUserAdapter(users, currentUserId) { selectedUser ->
            switchAccount(selectedUser)
        }
        binding.rvUsers.adapter = adapter

        binding.llAddAccount.setOnClickListener {
            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.putExtra("is_switching_account", true)
            startActivity(intent)
            dismiss()
        }
    }

    private fun switchAccount(user: MiniUser) {
        val tokenManager = AppCore.tokenManager
        tokenManager.saveTokenAndUserId(user.accessToken, user.userId)
        tokenManager.setCurrentUserId(user.userId)

        // Clear all ViewModels before restarting the app to ensure fresh state
        AppProvider.clearAllViewModels(requireActivity().application)

        // Restart app or navigate to MainActivity to refresh data
        val intent = Intent(requireContext(), SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SwitchAccountBottomSheet"
    }
}