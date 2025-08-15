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

class LoginFragment : Fragment() {
    
    private lateinit var authService: AuthService
    private lateinit var sessionManager: SessionManager
    
    private lateinit var etUsernameEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var tvLoginError: TextView
    private lateinit var progressBar: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        authService = AuthService()
        sessionManager = SessionManager(requireContext())
        
        initViews(view)
        setupListeners()
    }
    
    private fun initViews(view: View) {
        etUsernameEmail = view.findViewById(R.id.etUsernameEmail)
        etPassword = view.findViewById(R.id.etPassword)
        btnLogin = view.findViewById(R.id.btnLogin)
        tvLoginError = view.findViewById(R.id.tvLoginError)
        progressBar = view.findViewById(R.id.progressBar)
    }
    
    private fun setupListeners() {
        btnLogin.setOnClickListener {
            loginUser()
        }
    }
    
    private fun loginUser() {
        val usernameEmail = etUsernameEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        
        // Input validation
        if (usernameEmail.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.error_missing_fields))
            return
        }
        
        // Show loading
        setLoading(true)
        
        // Clear previous errors
        hideError()
        
        lifecycleScope.launch {
            try {
                // Check if input is email or username
                val result = if (android.util.Patterns.EMAIL_ADDRESS.matcher(usernameEmail).matches()) {
                    authService.signInWithEmail(usernameEmail, password)
                } else {
                    authService.signInWithUsername(usernameEmail, password)
                }
                
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    if (user != null) {
                        // Get user display name or use the username
                        val displayName = user.displayName ?: usernameEmail
                        
                        // Save user session
                        sessionManager.saveSession(
                            userId = user.uid,
                            userName = displayName,

                        )
                        
                        // Navigate to dashboard
                        (activity as? LoginActivity)?.navigateToDashboard()
                    } else {
                        showError(getString(R.string.error_login_failed))
                    }
                } else {
                    showError(result.exceptionOrNull()?.message ?: getString(R.string.error_login_failed))
                }
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.error_login_failed))
            } finally {
                setLoading(false)
            }
        }
    }
    
    private fun showError(message: String) {
        tvLoginError.visibility = View.VISIBLE
        tvLoginError.text = message
    }
    
    private fun hideError() {
        tvLoginError.visibility = View.GONE
    }
    
    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !isLoading
    }
} 