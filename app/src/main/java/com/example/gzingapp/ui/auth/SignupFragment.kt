package com.example.gzingapp.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.gzingapp.R
import com.example.gzingapp.services.AuthService
import com.example.gzingapp.services.SessionManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SignupFragment : Fragment() {
    
    private lateinit var authService: AuthService
    private lateinit var sessionManager: SessionManager
    
    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnSignUp: Button
    private lateinit var tvSignupError: TextView
    private lateinit var progressBar: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_signup, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        authService = AuthService()
        sessionManager = SessionManager(requireContext())
        
        initViews(view)
        setupListeners()
    }
    
    private fun initViews(view: View) {
        etFirstName = view.findViewById(R.id.etFirstName)
        etLastName = view.findViewById(R.id.etLastName)
        etEmail = view.findViewById(R.id.etEmail)
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        etUsername = view.findViewById(R.id.etUsername)
        etPassword = view.findViewById(R.id.etPassword)
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword)
        btnSignUp = view.findViewById(R.id.btnSignUp)
        tvSignupError = view.findViewById(R.id.tvSignupError)
        progressBar = view.findViewById(R.id.progressBar)
    }
    
    private fun setupListeners() {
        btnSignUp.setOnClickListener {
            signUpUser()
        }
    }
    
    private fun signUpUser() {
        // Get user input
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phoneNumber = etPhoneNumber.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        
        // Input validation
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() ||
            phoneNumber.isEmpty() || username.isEmpty() || password.isEmpty() ||
            confirmPassword.isEmpty()
        ) {
            showError(getString(R.string.error_missing_fields))
            return
        }
        
        if (password != confirmPassword) {
            showError(getString(R.string.error_passwords_dont_match))
            return
        }
        
        // Show loading
        setLoading(true)
        
        // Clear previous errors
        hideError()
        
        // Check if user is anonymous and convert account
        lifecycleScope.launch {
            try {
                val result = if (sessionManager.isAnonymous() && authService.isUserLoggedIn()) {
                    // Link anonymous user with real account
                    authService.linkAnonymousUser(
                        firstName, lastName, email, phoneNumber, username, password
                    )
                } else {
                    // Create a new user
                    authService.signUp(
                        firstName, lastName, email, phoneNumber, username, password
                    )
                }
                
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    if (user != null) {
                        // Save user session
                        sessionManager.saveSession(
                            userId = user.uid,
                            userName = "$firstName $lastName",
                            isAnonymous = false
                        )
                        
                        // Navigate to dashboard
                        (activity as? LoginActivity)?.navigateToDashboard()
                    } else {
                        showError(getString(R.string.error_signup_failed))
                    }
                } else {
                    showError(result.exceptionOrNull()?.message ?: getString(R.string.error_signup_failed))
                }
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.error_signup_failed))
            } finally {
                setLoading(false)
            }
        }
    }
    
    private fun showError(message: String) {
        tvSignupError.visibility = View.VISIBLE
        tvSignupError.text = message
    }
    
    private fun hideError() {
        tvSignupError.visibility = View.GONE
    }
    
    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSignUp.isEnabled = !isLoading
    }
} 