package com.writer_hub.novelserise

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.writer_hub.novelserise.adapter.ConversationAdapter
import com.writer_hub.novelserise.databinding.ActivityMessageListBinding
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.ChatViewModel

class MessageActivity : BaseActivity() {

    private lateinit var binding: ActivityMessageListBinding
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var adapter: ConversationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatViewModel = AppProvider.getViewModel(application, ChatViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        
        loadChats()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(emptyList()) { chat ->
            ChatActivity.start(this, chat.userId ?: "", chat.name ?: "", chat.avatar ?: "")
        }
        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        binding.rvConversations.adapter = adapter
    }

    private fun observeViewModel() {
        chatViewModel.chats.observe(this) { chats ->
            stopShimmer()
            if (chats.isNullOrEmpty()) {
                binding.tvNoMessages.visibility = View.VISIBLE
                binding.rvConversations.visibility = View.GONE
            } else {
                binding.tvNoMessages.visibility = View.GONE
                binding.rvConversations.visibility = View.VISIBLE
                adapter.updateChats(chats)
            }
        }

        chatViewModel.isFetchingChats.observe(this) { isFetching ->
            if (isFetching) {
                startShimmer()
            } else {
                stopShimmer()
            }
        }
    }

    private fun loadChats() {
        chatViewModel.fetchChats()
    }
    
    private fun startShimmer() {
        binding.shimmerViewContainer.startShimmer()
        binding.shimmerViewContainer.visibility = View.VISIBLE
        binding.rvConversations.visibility = View.GONE
    }
    
    private fun stopShimmer() {
        binding.shimmerViewContainer.stopShimmer()
        binding.shimmerViewContainer.visibility = View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
