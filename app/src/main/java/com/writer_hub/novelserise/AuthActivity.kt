/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.writer_hub.novelserise.databinding.ActivityAuthBinding
import com.writer_hub.novelserise.databinding.LayoutBottomSheetEmailLoginBinding
import com.writer_hub.novelserise.databinding.LayoutBottomSheetPhoneLoginBinding
import com.engine.core.helper.AlertUtils
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.helper.ToastHelper
import com.engine.core.manager.GlobalAppData
import com.engine.core.manager.TokenManager
import com.engine.core.models.MiniUser
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.AuthViewModel
import com.engine.core.viewmodel.SplashViewModel
import com.engine.core.viewmodel.UserViewModel
import kotlinx.coroutines.launch

class AuthActivity : BaseActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var authViewModel: AuthViewModel
    private lateinit var splashViewModel: SplashViewModel
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private val TAG = "AuthActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isSwitchingAccount = intent.getBooleanExtra("is_switching_account", false)
        splashViewModel = AppProvider.getViewModel(application, SplashViewModel::class.java)

        // Check if user is already logged in
        val tokenManager = TokenManager.getInstance(this)
        if (!isSwitchingAccount && tokenManager.getTokenAndUserId().first != null) {
            navigateToNextScreen()
            return
        }

        if (isSwitchingAccount) {
            // Ensure fresh state for account switching
            AppProvider.clearAllViewModels(application)
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        applyEdgeToEdge(binding.root)

        authViewModel = AppProvider.getViewModel(application, AuthViewModel::class.java)
        oneTapClient = Identity.getSignInClient(this)
        
        val webClientId = getString(R.string.default_web_client_id)
        
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(false)
            .build()

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnGoogleLogin.setOnClickListener {
            signInWithGoogle()
        }

        binding.btnPhoneLogin.setOnClickListener {
            showPhoneLoginBottomSheet()
        }

        binding.btnOtherLogin.setOnClickListener {
            showEmailLoginBottomSheet()
        }
    }

    private fun showPhoneLoginBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val sheetBinding = LayoutBottomSheetPhoneLoginBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        var sessionId: String? = null

        sheetBinding.btnContinue.setOnClickListener {
            val phone = sheetBinding.etPhone.text.toString().trim()
            val phoneDigits = phone.filter(Char::isDigit)

            if (sessionId == null) {
                if (phoneDigits.length !in 10..15) {
                    ToastHelper.makeText(this, "Please enter a valid mobile number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    sheetBinding.btnContinue.isEnabled = false
                    AlertUtils.showLoadingDialog(this@AuthActivity, "Sending OTP...")
                    try {
                        val result = authViewModel.sendOtp2Factor(phone)
                        if (result.apiStatus == 200 && !result.sessionId.isNullOrBlank()) {
                            sessionId = result.sessionId
                            sheetBinding.tvTitle.text = "Verify OTP"
                            sheetBinding.tvSubtitle.text = "Enter the OTP sent to $phone"
                            sheetBinding.etPhone.isEnabled = false
                            sheetBinding.cardOtp.visibility = View.VISIBLE
                            sheetBinding.btnContinue.text = "Verify & Login"
                            sheetBinding.etOtp.requestFocus()
                        } else {
                            val message = result.message ?: result.errorMessage ?: result.errorText
                            ToastHelper.makeText(
                                this@AuthActivity,
                                message ?: "Unable to send OTP",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        ToastHelper.makeText(
                            this@AuthActivity,
                            "Unable to send OTP: ${e.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        AlertUtils.dismissLoadingDialog()
                        sheetBinding.btnContinue.isEnabled = true
                    }
                }
                return@setOnClickListener
            }

            val otp = sheetBinding.etOtp.text.toString().trim()
            if (otp.length != 4 || !otp.all(Char::isDigit)) {
                ToastHelper.makeText(this, "Please enter the 4-digit OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                sheetBinding.btnContinue.isEnabled = false
                AlertUtils.showLoadingDialog(this@AuthActivity, "Verifying OTP...")
                try {
                    val result = authViewModel.phoneLogin2Factor(phone, otp, sessionId!!)
                    if (result.apiStatus == 200) {
                        dialog.dismiss()
                        handleSuccessfulLogin(result.accessToken, result.userId, null)
                    } else {
                        val message = result.message ?: result.errorMessage ?: result.errorText
                        ToastHelper.makeText(
                            this@AuthActivity,
                            message ?: "OTP verification failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    ToastHelper.makeText(
                        this@AuthActivity,
                        "OTP verification failed: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    AlertUtils.dismissLoadingDialog()
                    sheetBinding.btnContinue.isEnabled = true
                }
            }
        }

        dialog.show()
    }

    private fun showEmailLoginBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val sheetBinding = LayoutBottomSheetEmailLoginBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(sheetBinding.root)

        sheetBinding.btnLogin.setOnClickListener {
            val email = sheetBinding.etEmail.text.toString().trim()
            val password = sheetBinding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                ToastHelper.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginWithEmail(email, password, bottomSheetDialog)
        }

        bottomSheetDialog.show()
    }

    private fun loginWithEmail(email: String, password: String, dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            AlertUtils.showLoadingDialog(this@AuthActivity, "Logging in...")
            try {
                val result = authViewModel.login(email, password)
                AlertUtils.dismissLoadingDialog()

                if (result.apiStatus == 200) {
                    dialog.dismiss()
                    handleSuccessfulLogin(result.accessToken, result.userId, result.userData?.let { 
                        MiniUser(result.userId!!, it.userName, it.name, it.avatar, result.accessToken!!, it.brandTag)
                    })
                } else {
                    ToastHelper.makeText(this@AuthActivity, "Login Failed: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AlertUtils.dismissLoadingDialog()
                ToastHelper.makeText(this@AuthActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSuccessfulLogin(token: String?, userId: String?, miniUser: MiniUser?) {
        if (token != null && userId != null) {
            val tokenManager = TokenManager.getInstance(this)
            tokenManager.saveTokenAndUserId(token, userId)
            miniUser?.let { tokenManager.saveUser(it) }
            
            // CRITICAL: Re-init SharedPreferences and clear ViewModels to switch to user-specific storage
            AppProvider.initSharedPreferences(application)
            AppProvider.clearAllViewModels(application)

            // NEW: Clear GlobalAppData to avoid stale data and fetch fresh user details (diamonds, subs, etc.)
            GlobalAppData.clearData()
            val userViewModel = AppProvider.getViewModel(application, UserViewModel::class.java)
            lifecycleScope.launch {
                userViewModel.getUserDataDirect(true)
            }
            
            // Re-fetch SplashViewModel to check language selection in the NEW user-specific prefs
            splashViewModel = AppProvider.getViewModel(application, SplashViewModel::class.java)
            
            navigateToNextScreen()
        }
    }

    private fun signInWithGoogle() {
        AlertUtils.showLoadingDialog(this, "Opening Google Sign-In...")
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                AlertUtils.dismissLoadingDialog()
                try {
                    startIntentSenderForResult(
                        result.pendingIntent.intentSender,
                        1001,
                        null,
                        0,
                        0,
                        0
                    )
                } catch (e: Exception) {
                    AppLoggerHelper.e(TAG, "Error starting sign-in: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener(this) { e ->
                AlertUtils.dismissLoadingDialog()
                AppLoggerHelper.e(TAG, "Google Sign-In failed: ${e.localizedMessage}")
                ToastHelper.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001) {
            if (resultCode == RESULT_OK) {
                try {
                    val credential: SignInCredential = oneTapClient.getSignInCredentialFromIntent(data)
                    val idToken = credential.googleIdToken
                    if (idToken != null) {
                        loginWithSocialToken(idToken)
                    }
                } catch (e: Exception) {
                    AppLoggerHelper.e(TAG, "Credential error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun loginWithSocialToken(idToken: String) {
        lifecycleScope.launch {
            AlertUtils.showLoadingDialog(this@AuthActivity, "Authenticating...")
            try {
                val result = authViewModel.loginBySocial(idToken, "google")
                AlertUtils.dismissLoadingDialog()

                if (result.apiStatus == 200) {
                    handleSuccessfulLogin(result.accessToken, result.userId, result.userData?.let { 
                        MiniUser(result.userId!!, it.userName, it.name, it.avatar, result.accessToken!!, it.brandTag)
                    })
                } else {
                    ToastHelper.makeText(this@AuthActivity, "Login Failed: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AlertUtils.dismissLoadingDialog()
                ToastHelper.makeText(this@AuthActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToNextScreen() {
        if (!splashViewModel.isLanguageSelected()) {
            startActivity(Intent(this, LanguageSelectionActivity::class.java))
        } else {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        finish()
    }
}
