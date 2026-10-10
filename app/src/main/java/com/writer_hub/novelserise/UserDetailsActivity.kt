/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import android.util.Patterns
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.AppCore.tokenManager
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.UserDataBody
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.UserData
import com.engine.core.utill.SocialConfig
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel
import com.engine.core.viewmodel.PostViewModel
import com.engine.core.viewmodel.UserViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.writer_hub.novelserise.adapter.NovelHorizontalAdapter
import com.writer_hub.novelserise.adapter.PostAdapter
import com.writer_hub.novelserise.databinding.ActivityUserDetailsBinding
import com.writer_hub.novelserise.databinding.BottomSheetAddBankBinding
import kotlinx.coroutines.launch
import java.util.Locale

class UserDetailsActivity : BaseActivity() {

    private lateinit var binding: ActivityUserDetailsBinding
    private lateinit var userViewModel: UserViewModel
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var postViewModel: PostViewModel
    
    private lateinit var postAdapter: PostAdapter
    private var userId: String = ""
    private var isCurrentUser: Boolean = false
    private var userData: UserData? = null
    private var shouldRefreshCurrentUserOnResume: Boolean = false
    
    private var lastPostId: String? = null
    private var isLoadingPosts = false
    private var hasMorePosts = true

    companion object {
        private const val EXTRA_USER_ID = "user_id"

        fun start(context: Context, userId: String) {
            val intent = Intent(context, UserDetailsActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
            }
            context.startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isCurrentUser || !shouldRefreshCurrentUserOnResume) return

        shouldRefreshCurrentUserOnResume = false
        GlobalAppData.userDetails?.userData?.let { latestUser ->
            userData = latestUser
            bindUserData(latestUser)
        }
        lifecycleScope.launch {
            val latestDetails = userViewModel.getUserDataDirect(reload = true)
            latestDetails?.userData?.let { latestUser ->
                userData = latestUser
                bindUserData(latestUser)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
        if (userId.isEmpty()) {
            finish()
            return
        }

        val (_, currentId) = tokenManager.getTokenAndUserId()
        isCurrentUser = userId == currentId

        userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)
        novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)
        postViewModel = AppProvider.getViewModel(application, PostViewModel::class.java)

        setupToolbar()
        setupRecyclerViews()
        setupListeners()
        loadInitialData()

        if (isCurrentUser) {
            GlobalAppData.userDetailsLiveData.observe(this) { userDetails ->
                userDetails?.userData?.let {
                    userData = it
                    bindUserData(it)
                }
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_user_details, menu)
        // Share is always visible as requested
        menu?.findItem(R.id.action_share)?.isVisible = true
        // Show settings only if current user's profile
        menu?.findItem(R.id.action_settings)?.isVisible = isCurrentUser
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareProfile()
                true
            }
            R.id.action_settings -> {
                SettingsActivity.start(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerViews() {
        postAdapter = PostAdapter(postViewModel)
        binding.rvUserPosts.apply {
            layoutManager = LinearLayoutManager(this@UserDetailsActivity)
            adapter = postAdapter
            isNestedScrollingEnabled = false
        }
        
        binding.rvUserNovels.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvViewHistory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupListeners() {
        binding.btnFollow.setOnClickListener {
            if (isCurrentUser) {
                handleEditProfileClick()
            } else {
                handleFollowClick()
            }
        }

        binding.btnMessage.setOnClickListener {
            if (isCurrentUser) {
                shareProfile()
            } else {
                val user = userData
                if (user != null) {
                    ChatActivity.start(this, user.userId ?: "", user.name ?: "Unknown", user.avatar ?: "")
                } else {
                     Toast.makeText(this, "User data not loaded yet", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnViewAllNovels.setOnClickListener {
            ViewAllNovelsActivity.start(this, ViewAllNovelsActivity.TYPE_WRITER_NOVELS, userId, "Writer's Novels")
        }

        binding.btnViewAllHistory.setOnClickListener {
            ViewAllNovelsActivity.start(this, ViewAllNovelsActivity.TYPE_CONTINUE_READING, userId, "Continue Reading")
        }
        
        binding.btnCreatePost.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }

        binding.statFollowers.setOnClickListener {
            FollowListActivity.start(this, userId, "followers")
        }
        binding.statFollowing.setOnClickListener {
             FollowListActivity.start(this, userId, "following")
        }

        binding.nestedScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            // The name lives in the hero; the toolbar shows it once the hero has scrolled away.
            val showTitle = scrollY > binding.tvUserName.bottom
            val title = if (showTitle) binding.tvUserName.text else ""
            if (binding.toolbar.title?.toString().orEmpty() != title.toString()) binding.toolbar.title = title

            if (scrollY >= (v.getChildAt(0).measuredHeight - v.measuredHeight - 300)) {
                if (!isLoadingPosts && hasMorePosts) {
                    loadPosts(refresh = false)
                }
            }
        })

        binding.ivFacebook.setOnClickListener { 
            val id = userData?.facebook
            if (!id.isNullOrEmpty()) {
                val url = if (id.startsWith("http")) id else "https://www.facebook.com/$id"
                openLink(url)
            }
        }
        binding.ivInstagram.setOnClickListener { 
            val id = userData?.instagram
            if (!id.isNullOrEmpty()) {
                val url = if (id.startsWith("http")) id else "https://www.instagram.com/$id"
                openLink(url)
            }
        }
        binding.ivWhatsapp.setOnClickListener { 
            val link = userData?.whatsapp
            if (!link.isNullOrEmpty()) {
                openLink(link)
            }
        }

        binding.cardAddBank.setOnClickListener {
            showBankDetailsBottomSheet()
        }
        binding.cardTransactions.setOnClickListener {
            BankTransactionActivity.start(this)
        }
        binding.cardEarnings.setOnClickListener {
            TransactionHistoryActivity.start(this, "0", "writer", "diamond")
        }
    }

    private fun handleEditProfileClick() {
        val data = userData ?: return
        val userDataBody = UserDataBody(
            userId = data.userId,
            name = data.name,
            firstName = data.firstName,
            lastName = data.lastName,
            userName = data.userName,
            avatar = data.avatar,
            working = data.working,
            address = data.address,
            birthday = data.birthday,
            phnNumber = data.phnNumber,
            email = data.email,
            gender = data.gender,
            companyWebsite = data.working,
            facebook = data.facebook,
            google = data.google,
            youtube = data.youtube,
            instagram = data.instagram,
            whatsapp = data.whatsapp,
            about = decodeHtmlToTextV2(data.about),
            bankName = data.bankName,
            accountNumber = data.accountNumber,
            ifscCode = data.ifscCode,
            panCard = data.panCard,
            aadhaarCard = data.aadhaarCard,
            accountHolderName = data.accountHolderName
        )
        val intent = Intent(this, EditProfileActivity::class.java).apply {
            val bundle = Bundle().apply {
                putParcelable("USER_DATA", userDataBody)
            }
            putExtra("USER_DATA_BUNDLE", bundle)
        }
        shouldRefreshCurrentUserOnResume = true
        startActivity(intent)
    }

    private fun openLink(url: String?) {
        if (url.isNullOrEmpty()) return
        val fullUrl = if (!url.startsWith("http")) "https://$url" else url
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            binding.shimmerHeader.root.visibility = View.VISIBLE
            binding.shimmerHeader.shimmerViewContainer.startShimmer()
            binding.cardProfile.visibility = View.GONE

            val userDetails = if (isCurrentUser && GlobalAppData.userDetails != null) {
                GlobalAppData.userDetails
            } else {
                userViewModel.getUserData(userId)
            }
            
            userData = userDetails?.userData
            userData?.let { bindUserData(it) }
            
            binding.shimmerHeader.shimmerViewContainer.stopShimmer()
            binding.shimmerHeader.root.visibility = View.GONE
            binding.cardProfile.visibility = View.VISIBLE

            loadNovels()
            if (isCurrentUser) loadHistory()
            loadPosts(refresh = true)
        }
    }

    private fun loadNovels() {
        lifecycleScope.launch {
            binding.shimmerNovels.visibility = View.VISIBLE
            binding.shimmerNovels.startShimmer()
            binding.layoutNovels.visibility = View.GONE

            val novels = novelViewModel.fetchNovelsByUserId(userId, limit = 3, offset = 0, isPremium = "all")
            
            binding.shimmerNovels.stopShimmer()
            binding.shimmerNovels.visibility = View.GONE
            
            if (!novels.isNullOrEmpty()) {
                binding.rvUserNovels.adapter = NovelHorizontalAdapter(novels)
                binding.layoutNovels.visibility = View.VISIBLE
            } else {
                binding.layoutNovels.visibility = View.GONE
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            binding.shimmerHistory.visibility = View.VISIBLE
            binding.shimmerHistory.startShimmer()
            binding.layoutViewHistory.visibility = View.GONE

            val history = novelViewModel.fetchNovelsByCategoryAndSubcategory(
                "0", "0", "getcontinuereading", 0, 3, "0", "0", "0"
            )
            
            binding.shimmerHistory.stopShimmer()
            binding.shimmerHistory.visibility = View.GONE

            if (history.isNotEmpty()) {
                binding.rvViewHistory.adapter = NovelHorizontalAdapter(history)
                binding.layoutViewHistory.visibility = View.VISIBLE
            } else {
                binding.layoutViewHistory.visibility = View.GONE
            }
        }
    }

    private fun bindUserData(user: UserData) {
        val displayName = decodeHtmlToTextV2(user.name)
        binding.tvUserName.text = displayName
        binding.tvUserHandle.text = "@${user.userName}"
        val fallbackBios = listOf(
            "Stories are my escape, and imagination is my home. Creating worlds one chapter at a time.",
            "Writing emotions that stay with you. Romance, drama, and unforgettable journeys.",
            "A reader by heart and a storyteller by choice. Welcome to my world of stories.",
            "Turning late-night thoughts into meaningful stories. Every chapter holds a new feeling.",
            "I write characters who feel real and stories that connect. Stay for the journey ahead.",
            "Dreaming in plots and living between chapters. New stories are always on the way.",
            "Sharing imagination, emotions, and memorable moments. One story at a time.",
            "Words can create an entirely new world. Come explore mine with me."
        )
        val fallbackIndex = Math.floorMod((user.userId ?: user.userName ?: "profile").hashCode(), fallbackBios.size)
        binding.tvUserAbout.text = if (user.about.isNullOrBlank()) {
            fallbackBios[fallbackIndex]
        } else {
            decodeHtmlToTextV2(user.about)
        }
        
        binding.tvFollowerCount.text = user.details?.followerCount ?: "0"
        binding.tvFollowingCount.text = user.details?.followingCount ?: "0"
        binding.tvViewCount.text = user.totalNovelsViews ?: "0"

        // Use ImageHelper (Coil) to load user avatar
        ImageHelper.loadCircleImage(
            this,
            user.avatar,
            binding.ivUserAvatar,
            com.engine.core.R.drawable.ic_profile
        )

        var hasSocial = false
        if (!user.facebook.isNullOrEmpty()) {
            binding.ivFacebook.visibility = View.VISIBLE
            binding.ivFacebook.setImageResource(com.engine.core.R.drawable.ic_facebook)
            hasSocial = true
        } else {
            binding.ivFacebook.visibility = View.GONE
        }

        if (!user.instagram.isNullOrEmpty()) {
            binding.ivInstagram.visibility = View.VISIBLE
            binding.ivInstagram.setImageResource(com.engine.core.R.drawable.ic_instagram)
            hasSocial = true
        } else {
            binding.ivInstagram.visibility = View.GONE
        }

        if (!user.whatsapp.isNullOrEmpty()) {
            binding.ivWhatsapp.visibility = View.VISIBLE
            binding.ivWhatsapp.setImageResource(com.engine.core.R.drawable.ic_whatsapp)
            hasSocial = true
        } else {
            binding.ivWhatsapp.visibility = View.GONE
        }

        binding.layoutSocialLinks.visibility = if (hasSocial) View.VISIBLE else View.GONE

        if (isCurrentUser) {
            binding.btnFollow.text = "Edit"
            binding.btnFollow.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            binding.btnFollow.strokeColor = ColorStateList.valueOf(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            binding.btnFollow.strokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                1f,
                resources.displayMetrics
            ).toInt()
            binding.btnFollow.setTextColor(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            
            binding.btnMessage.text = "Share"
            binding.btnMessage.setIconResource(0)
            binding.btnMessage.backgroundTintList = ColorStateList.valueOf(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
            binding.btnMessage.strokeWidth = 0
            binding.btnMessage.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimary))
            
            binding.btnCreatePost.visibility = View.VISIBLE

            binding.layoutCreatorEarnings.visibility = View.VISIBLE
            binding.tvEarnings.text = String.format(Locale.getDefault(), "₹%.2f", user.diamondIncome ?: 0.0)
        } else {
            updateFollowButton(user.isFollowing == 1)
            binding.btnMessage.text = "Message"
            binding.btnMessage.setIconResource(0)
            
            binding.btnCreatePost.visibility = View.GONE
            binding.layoutCreatorEarnings.visibility = View.GONE
        }
    }

    private fun updateFollowButton(isFollowing: Boolean) {
        val colorAttr = if (isFollowing) {
            binding.btnFollow.text = "Unfollow"
            com.google.android.material.R.attr.colorOnSurfaceVariant
        } else {
            binding.btnFollow.text = "Follow"
            androidx.appcompat.R.attr.colorPrimary
        }
        binding.btnFollow.backgroundTintList = ColorStateList.valueOf(getThemeColor(colorAttr))
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun handleFollowClick() {
        val user = userData ?: return
        val currentlyFollowing = user.isFollowing == 1
        val targetFollowing = !currentlyFollowing

        user.isFollowing = if (targetFollowing) 1 else 0
        updateFollowButton(targetFollowing)
        
        val currentCountText = binding.tvFollowerCount.text.toString()
        val currentCount = currentCountText.toIntOrNull() ?: 0
        binding.tvFollowerCount.text = if (targetFollowing) (currentCount + 1).toString() else (currentCount - 1).toString()

        lifecycleScope.launch {
            val status = userViewModel.followOrUnfollowUser(user.userId ?: "", user)
            if (status == null) {
                user.isFollowing = if (currentlyFollowing) 1 else 0
                updateFollowButton(currentlyFollowing)
                binding.tvFollowerCount.text = currentCountText
                Toast.makeText(this@UserDetailsActivity, "Failed to update follow status", Toast.LENGTH_SHORT).show()
            } else {
                val isActuallyFollowing = status == "followed"
                if (isActuallyFollowing != targetFollowing) {
                    user.isFollowing = if (isActuallyFollowing) 1 else 0
                    updateFollowButton(isActuallyFollowing)
                }
            }
        }
    }

    private fun loadPosts(refresh: Boolean) {
        if (isLoadingPosts) return
        isLoadingPosts = true
        
        lifecycleScope.launch {
            if (refresh) {
                binding.shimmerPosts.visibility = View.VISIBLE
                binding.shimmerPosts.startShimmer()
                binding.layoutPosts.visibility = View.GONE
                lastPostId = null
            }
            
            val currentLimit = if (refresh) "3" else "10"
            
            val posts = postViewModel.fetchPostsDirectly(
                afterPostId = if (refresh) null else lastPostId,
                type = "get_user_posts",
                id = userId,
                reload = refresh,
                limit = currentLimit
            )
            
            isLoadingPosts = false
            if (refresh) {
                binding.shimmerPosts.stopShimmer()
                binding.shimmerPosts.visibility = View.GONE
                binding.layoutPosts.visibility = View.VISIBLE
            }
            binding.progressBarPosts.visibility = if (!refresh && !posts.isNullOrEmpty()) View.GONE else binding.progressBarPosts.visibility
            
            if (!posts.isNullOrEmpty()) {
                binding.emptyPosts.visibility = View.GONE
                postAdapter.addPosts(posts, refresh)
                lastPostId = posts.last().id
                hasMorePosts = posts.size >= currentLimit.toInt()
            } else {
                hasMorePosts = false
                if (refresh) {
                    binding.emptyPosts.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun shareProfile() {
        val baseUrl = SocialConfig.shareFaceLink.trimEnd('/')
        val userName = userData?.userName ?: userId
        val shareUrl = "$baseUrl/profile/$userName"
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Check out ${userData?.name}'s profile on NovelSeries: $shareUrl")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share profile via"))
    }

    private fun showBankDetailsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val bsBinding = BottomSheetAddBankBinding.inflate(layoutInflater)
        dialog.setContentView(bsBinding.root)

        val user = userData
        if (user != null) {
            bsBinding.etPhone.setText(user.phnNumber)
            bsBinding.etEmail.setText(user.email)
            bsBinding.etAccountHolder.setText(user.accountHolderName)
            bsBinding.etBankName.setText(user.bankName)
            bsBinding.etAccountNumber.setText(user.accountNumber)
            bsBinding.etIfsc.setText(user.ifscCode)
            bsBinding.etPan.setText(user.panCard)
            bsBinding.etAadhaar.setText(user.aadhaarCard)
        }

        bsBinding.btnSaveBank.setOnClickListener {
            val phone = bsBinding.etPhone.text.toString().trim()
            val email = bsBinding.etEmail.text.toString().trim()
            val holder = bsBinding.etAccountHolder.text.toString()
            val bank = bsBinding.etBankName.text.toString()
            val account = bsBinding.etAccountNumber.text.toString()
            val ifsc = bsBinding.etIfsc.text.toString()
            val pan = bsBinding.etPan.text.toString()
            val aadhaar = bsBinding.etAadhaar.text.toString()

            if (phone.isEmpty() || email.isEmpty() || holder.isEmpty() || bank.isEmpty() || account.isEmpty() || ifsc.isEmpty()) {
                Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                bsBinding.tilEmail.error = "Enter a valid email address"
                return@setOnClickListener
            }
            bsBinding.tilEmail.error = null

            val body = UserDataBody(
                phnNumber = phone,
                email = email,
                accountHolderName = holder,
                bankName = bank,
                accountNumber = account,
                ifscCode = ifsc,
                panCard = pan,
                aadhaarCard = aadhaar
            )

            lifecycleScope.launch {
                bsBinding.btnSaveBank.isEnabled = false
                bsBinding.btnSaveBank.text = "Saving..."
                val result = userViewModel.editUserData(body)
                if (result.isSuccessful) {
                    Toast.makeText(this@UserDetailsActivity, "Bank details updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this@UserDetailsActivity, result.message, Toast.LENGTH_SHORT).show()
                    bsBinding.btnSaveBank.isEnabled = true
                    bsBinding.btnSaveBank.text = "Save Bank Details"
                }
            }
        }

        dialog.show()
    }
}
