/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.viewmodel.AdsViewModel
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.MyNotificationViewModel
import com.writer_hub.novelserise.adapter.NotificationAdapter
import com.writer_hub.novelserise.databinding.FragmentNotificationBinding
import kotlinx.coroutines.launch

class NotificationFragment : Fragment() {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MyNotificationViewModel
    private lateinit var adsViewModel: AdsViewModel
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModels using AppProvider
        viewModel = AppProvider.getViewModel(requireActivity().application, MyNotificationViewModel::class.java)
        adsViewModel = AppProvider.getViewModel(requireActivity().application, AdsViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        loadNotifications()
        checkAndShowBannerAd()
    }

    private fun checkAndShowBannerAd() {
        _binding?.let { binding ->
            if (GlobalVariableManager.shouldShowHomeBannerAd()) {
                binding.adContainer.visibility = View.VISIBLE
                adsViewModel.showBannerAd(requireActivity(), binding.adContainer)
            } else {
                binding.adContainer.visibility = View.GONE
            }
        }
    }

    private fun setupToolbar() {
        binding.ivMarkAllRead.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = viewModel.markAllAsRead()
                if (_binding == null) return@launch
                
                result.onSuccess { success ->
                    if (success) {
                        Toast.makeText(context, "All marked as read", Toast.LENGTH_SHORT).show()
                        // Refresh the list to update UI states
                        loadNotifications()
                    }
                }.onFailure {
                    Toast.makeText(context, "Failed to mark all as read", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(viewModel)
        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@NotificationFragment.adapter
            
            // Handle pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                    ) {
                        loadMoreNotifications()
                    }
                }
            })
        }
    }

    private fun loadNotifications() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.fetchNotifications(isRefresh = true)

            _binding?.let { binding ->
                binding.progressBar.visibility = View.GONE

                result.onSuccess { model ->
                    val notifications = model?.notifications
                    if (notifications.isNullOrEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvNotifications.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvNotifications.visibility = View.VISIBLE
                        adapter.submitList(notifications)
                    }
                }.onFailure {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "Error loading notifications"
                }
            }
        }
    }

    private fun loadMoreNotifications() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.fetchMoreNotifications()
            if (!isAdded) return@launch

            result.onSuccess { newNotifications ->
                if (newNotifications.isNotEmpty()) {
                    val currentList = adapter.currentList.toMutableList()
                    currentList.addAll(newNotifications)
                    adapter.submitList(currentList)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
