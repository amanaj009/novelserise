/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.UserDataBody
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.UserViewModel
import com.writer_hub.novelserise.databinding.ActivityEditProfileBinding
import kotlinx.coroutines.launch

class EditProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var userViewModel: UserViewModel
    private var selectedAvatarUri: Uri? = null

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        selectedAvatarUri = uri
        binding.ivAvatar.setImageURI(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root)

        userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)

        setupToolbar()
        loadUserData()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.layoutToolbar.tvTitle.text = "Edit Profile"
        binding.layoutToolbar.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadUserData() {
        val user = GlobalAppData.userDetails?.userData ?: return

        binding.etName.setText(decodeHtmlToTextV2(user.name))
        binding.etFirstName.setText(decodeHtmlToTextV2(user.firstName))
        binding.etLastName.setText(decodeHtmlToTextV2(user.lastName))
        binding.etAbout.setText(decodeHtmlToTextV2(user.about))

        // Handle gender RadioGroup selection
        when (user.gender?.lowercase()) {
            "male" -> binding.rbMale.isChecked = true
            "female" -> binding.rbFemale.isChecked = true
            // "other" -> binding.rbOther.isChecked = true
        }
        
        binding.etPhone.setText(user.phnNumber)
        binding.etEmail.setText(user.email)
        binding.etFacebook.setText(extractUsername(user.facebook))
        binding.etInstagram.setText(extractUsername(user.instagram))
        binding.tvFollowersCount.text = "${user.details?.followerCount ?: "0"} Followers"

        if (!user.phnNumber.isNullOrEmpty()) {
            binding.etPhone.isEnabled = false
            binding.tilPhone.alpha = 0.6f
        }

        if (!user.email.isNullOrEmpty()) {
            binding.etEmail.isEnabled = false
            binding.tilEmail.alpha = 0.6f
        }

        ImageHelper.loadImage(
            this,
            user.avatar,
            binding.ivAvatar,
            com.engine.core.R.drawable.ic_profile
        )
    }

    private fun extractUsername(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        return if (input.contains("/")) {
            input.trim().removeSuffix("/").substringAfterLast("/")
        } else {
            input.trim().removePrefix("@")
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveProfile()
        }
        
        binding.ivAvatar.setOnClickListener {
            pickAvatar.launch("image/*")
        }
        
        binding.btnChangePhoto.setOnClickListener {
            pickAvatar.launch("image/*")
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        val about = binding.etAbout.text.toString().trim()

        // Get gender from RadioGroup
        val selectedGender = when (binding.rgGender.checkedRadioButtonId) {
            R.id.rbMale -> "male"
            R.id.rbFemale -> "female"
            R.id.rbOther -> "other"
            else -> ""
        }
        
        val phone = binding.etPhone.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val facebook = extractUsername(binding.etFacebook.text.toString())
        val instagram = extractUsername(binding.etInstagram.text.toString())

        if (name.isEmpty()) {
            binding.etName.error = "Display Name cannot be empty"
            return
        }
        val userDataBody = UserDataBody(
            firstName = firstName.takeIf { it.isNotEmpty() },
            lastName = lastName.takeIf { it.isNotEmpty() },
            name = name,
            about = about,
            gender = selectedGender.takeIf { it.isNotEmpty() },
            phnNumber = phone.takeIf { binding.etPhone.isEnabled },
            email = email.takeIf { binding.etEmail.isEnabled },
            facebook = facebook,
            instagram = instagram,
            avatar = selectedAvatarUri?.toString()
        )

        lifecycleScope.launch {
            showLoading(true)
            try {
                val result = userViewModel.editUserData(userDataBody)
                if (result.isSuccessful) {
                    Toast.makeText(this@EditProfileActivity, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@EditProfileActivity, "Failed: ${result.message}", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                Toast.makeText(
                    this@EditProfileActivity,
                    error.localizedMessage ?: "Unable to update profile",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !isLoading
    }
}
