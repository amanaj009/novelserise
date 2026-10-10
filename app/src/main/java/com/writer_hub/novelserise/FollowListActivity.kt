package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.writer_hub.novelserise.adapter.FollowUserAdapter
import com.writer_hub.novelserise.databinding.ActivityFollowListBinding
import com.engine.core.models.UserData
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.UserViewModel
import kotlinx.coroutines.launch

class FollowListActivity : BaseActivity() {

    private lateinit var binding: ActivityFollowListBinding
    private lateinit var userViewModel: UserViewModel
    private lateinit var adapter: FollowUserAdapter
    private var userId: String = ""
    private var listType: String = "" // "followers" or "following"
    
    private var isLoading = false
    private var hasMore = true
    private var afterUserId: String? = null
    private val userList = mutableListOf<UserData>() // Initialize list

    companion object {
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_LIST_TYPE = "list_type"
        private const val LIMIT = 20
        
        fun start(context: Context, userId: String, type: String) {
            val intent = Intent(context, FollowListActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_LIST_TYPE, type)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFollowListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        listType = intent.getStringExtra(EXTRA_LIST_TYPE) ?: ""

        if (userId.isEmpty()) {
            finish()
            return
        }

        userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)

        setupToolbar()
        setupRecyclerView()
        loadData(isInitial = true)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (listType == "following") "Following" else "Followers"
    }

    private fun setupRecyclerView() {
        adapter = FollowUserAdapter(userList) { user ->
            UserDetailsActivity.start(this, user.userId ?: "")
        }
        val layoutManager = LinearLayoutManager(this)
        binding.rvUsers.layoutManager = layoutManager
        binding.rvUsers.adapter = adapter
        
        binding.rvUsers.addOnScrollListener(object : RecyclerView.OnScrollListener() {
             override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasMore) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                    ) {
                        loadData(isInitial = false)
                    }
                }
            }
        })
    }

    private fun loadData(isInitial: Boolean) {
        if (isLoading) return
        isLoading = true

        lifecycleScope.launch {
            if (isInitial) {
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.shimmerViewContainer.startShimmer()
                binding.rvUsers.visibility = View.GONE
                binding.tvEmpty.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.VISIBLE
            }

            try {
                // Fetch data
                // Assuming LIMIT is 20 for logic, passing afterUserId for pagination.
                val response = userViewModel.getUserFollowers(userId, listType, afterUserId)
                
                val newUsers = if (listType == "following") {
                    response?.following ?: emptyList()
                } else {
                    response?.followers ?: emptyList()
                }

                if (isInitial) {
                    binding.shimmerViewContainer.stopShimmer()
                    binding.shimmerViewContainer.visibility = View.GONE
                    binding.rvUsers.visibility = View.VISIBLE
                } else {
                    binding.progressBar.visibility = View.GONE
                }

                if (newUsers.isNotEmpty()) {
                    if (isInitial) {
                        userList.clear()
                    }
                    userList.addAll(newUsers)
                    adapter.notifyDataSetChanged()
                    
                    // Update cursor for next page
                    afterUserId = newUsers.lastOrNull()?.userId
                    
                    // Logic to check if more pages available
                    hasMore = newUsers.size >= LIMIT 
                } else {
                    if (isInitial) {
                        binding.tvEmpty.visibility = View.VISIBLE
                    } else {
                        hasMore = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                 if (isInitial) {
                    binding.shimmerViewContainer.stopShimmer()
                    binding.shimmerViewContainer.visibility = View.GONE
                    binding.tvEmpty.text = "Error loading users"
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            } finally {
                isLoading = false
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
