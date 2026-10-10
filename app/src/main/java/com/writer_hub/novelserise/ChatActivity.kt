/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.AppCore.tokenManager
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.viewmodel.AdsViewModel
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.ChatViewModel
import com.writer_hub.novelserise.adapter.ChatAdapter
import com.writer_hub.novelserise.databinding.ActivityChatBinding
import kotlinx.coroutines.launch

class ChatActivity : BaseActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var adsViewModel: AdsViewModel
    private lateinit var chatAdapter: ChatAdapter
    private var userId: String = ""
    private var userName: String = ""
    private var userAvatar: String = ""
    private var currentUserId: String = ""

    companion object {
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_USER_NAME = "user_name"
        private const val EXTRA_USER_AVATAR = "user_avatar"

        fun start(context: Context, userId: String, name: String, avatar: String) {
            val intent = Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, name)
                putExtra(EXTRA_USER_AVATAR, avatar)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        userName = intent.getStringExtra(EXTRA_USER_NAME) ?: ""
        userAvatar = intent.getStringExtra(EXTRA_USER_AVATAR) ?: ""
        
        val (_, id) = tokenManager.getTokenAndUserId()
        currentUserId = id ?: ""

        if (userId.isEmpty() || currentUserId.isEmpty()) {
            finish()
            return
        }

        chatViewModel = AppProvider.getViewModel(application, ChatViewModel::class.java)
        adsViewModel = AppProvider.getViewModel(application, AdsViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        loadMessages()
        checkAndShowBannerAd()
    }

    private fun checkAndShowBannerAd() {
        if (GlobalVariableManager.shouldShowHomeBannerAd()) {
            binding.adContainer.visibility = View.VISIBLE
            adsViewModel.showBannerAd(this, binding.adContainer)
        } else {
            binding.adContainer.visibility = View.GONE
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = userName
        
        binding.toolbar.setOnClickListener {
            UserDetailsActivity.start(this, userId)
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(this, mutableListOf(), currentUserId) { user ->
            // Handle the user name click event
            UserDetailsActivity.start(this, user.userId ?: "")
        }
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                reverseLayout = true // Chat starts from bottom
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }
    }

    private fun observeViewModel() {
        chatViewModel.userMessages.observe(this) { messages ->
            if (!messages.isNullOrEmpty()) {
                chatAdapter.setMessages(messages)
                binding.rvMessages.scrollToPosition(0)
                stopShimmer()
            }
        }

        chatViewModel.isSendingMessage.observe(this) { isSending ->
            binding.btnSend.isEnabled = !isSending
        }

        chatViewModel.isFetchingMessages.observe(this) { isFetching ->
            if (isFetching) {
                startShimmer()
            } else {
                stopShimmer()
            }
        }
    }

    private fun startShimmer() {
        binding.shimmerViewContainer.startShimmer()
        binding.shimmerViewContainer.visibility = View.VISIBLE
        binding.rvMessages.visibility = View.GONE
    }

    private fun stopShimmer() {
        binding.shimmerViewContainer.stopShimmer()
        binding.shimmerViewContainer.visibility = View.GONE
        binding.rvMessages.visibility = View.VISIBLE
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            chatViewModel.fetchUserMessages(
                userId = userId,
                limit = 50, // Load more messages initially
                beforeMessageId = null,
                afterMessageId = null
            )
        }
    }

    private fun sendMessage(message: String) {
        lifecycleScope.launch {
            binding.etMessage.setText("")
            val localId = System.currentTimeMillis().toString() 
            
            chatViewModel.sendMessage(
                userId = userId,
                message = message,
                file = null,
                localId = localId,
                chatId = "0",
            )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        chatViewModel.currentlyOpenedChatBox(userId)
    }

    override fun onPause() {
        super.onPause()
        chatViewModel.currentlyOpenedChatBox(null)
    }
}
