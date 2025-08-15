package com.example.gzingapp.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R
import com.example.gzingapp.models.EmergencyContact
import com.example.gzingapp.models.User
import com.example.gzingapp.repositories.EmergencyContactRepository
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.ui.auth.LoginActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.util.UUID

class ProfileActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var sessionManager: SessionManager
    private lateinit var emergencyContactRepository: EmergencyContactRepository

    // Profile UI elements
    private lateinit var ivProfilePicture: ImageView
    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var btnSaveProfile: Button

    // Emergency contacts UI elements
    private lateinit var rvEmergencyContacts: RecyclerView
    private lateinit var llEmptyState: LinearLayout
    private lateinit var btnAddEmergencyContact: Button

    // Account actions
    private lateinit var btnChangePassword: Button
    private lateinit var btnDeleteAccount: Button

    private lateinit var emergencyContactsAdapter: EmergencyContactsAdapter
    private val emergencyContacts = mutableListOf<EmergencyContact>()
    private var emergencyContactsListener: ValueEventListener? = null

    companion object {
        private const val TAG = "ProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)
        emergencyContactRepository = EmergencyContactRepository()

        // Check if user has an active session and is not anonymous
        if (sessionManager.needsAuthentication()) {
            Log.d(TAG, "No active session found, redirecting to login")
            navigateToLogin()
            return
        } else {
            Log.d(TAG, "Authenticated user accessing profile")
        }

        setupUI()
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        loadUserProfile()
        loadEmergencyContacts()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove emergency contacts listener
        val userId = sessionManager.getCurrentUserId()
        if (userId != null && emergencyContactsListener != null) {
            emergencyContactRepository.removeEmergencyContactsListener(userId, emergencyContactsListener!!)
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupUI() {
        toolbar = findViewById(R.id.toolbar)

        // Profile elements
        ivProfilePicture = findViewById(R.id.ivProfilePicture)
        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)

        // Emergency contacts elements
        rvEmergencyContacts = findViewById(R.id.rvEmergencyContacts)
        llEmptyState = findViewById(R.id.llEmptyState)
        btnAddEmergencyContact = findViewById(R.id.btnAddEmergencyContact)

        // Account actions
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Profile"
        }
    }

    private fun setupRecyclerView() {
        emergencyContactsAdapter = EmergencyContactsAdapter(
            emergencyContacts,
            onEditClick = { contact -> showEditContactDialog(contact) },
            onDeleteClick = { contact -> showDeleteContactDialog(contact) }
        )

        rvEmergencyContacts.apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            adapter = emergencyContactsAdapter
        }
    }

    private fun setupListeners() {
        btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }

        btnAddEmergencyContact.setOnClickListener {
            showAddContactDialog()
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                btnSaveProfile.isEnabled = false

                val userId = sessionManager.getCurrentUserId()
                if (userId != null) {
                    val user = sessionManager.fetchCurrentUserData(userId)
                    if (user != null) {
                        populateUserFields(user)
                        Log.d(TAG, "User profile loaded from Firebase")
                    } else {
                        // Use session data as fallback
                        val session = sessionManager.getSession()
                        etFirstName.setText(session["firstName"] as String? ?: "")
                        etLastName.setText(session["lastName"] as String? ?: "")
                        etEmail.setText(session["email"] as String? ?: "")
                        etPhone.setText(session["phone"] as String? ?: "")
                        Log.d(TAG, "User profile loaded from session fallback")
                    }
                } else {
                    Toast.makeText(this@ProfileActivity, "Error: No user ID found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile: ${e.message}", e)
                Toast.makeText(this@ProfileActivity, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSaveProfile.isEnabled = true
            }
        }
    }

    private fun populateUserFields(user: User) {
        etFirstName.setText(user.firstName)
        etLastName.setText(user.lastName)
        etEmail.setText(user.email)
        etPhone.setText(user.phoneNumber)
    }

    private fun saveUserProfile() {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                btnSaveProfile.isEnabled = false
                btnSaveProfile.text = "Saving..."

                // Create updates map
                val updates = mapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "email" to email,
                    "phoneNumber" to phone
                )

                // Update user data in Firebase and session
                val success = sessionManager.updateUserData(updates)

                if (success) {
                    Toast.makeText(this@ProfileActivity, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Profile updated successfully")
                } else {
                    Toast.makeText(this@ProfileActivity, "Failed to update profile", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error updating profile: ${e.message}", e)
                Toast.makeText(this@ProfileActivity, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSaveProfile.isEnabled = true
                btnSaveProfile.text = "Save Profile"
            }
        }
    }

    private fun loadEmergencyContacts() {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            Toast.makeText(this, "Error: No user ID found", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Load initial data
                val result = emergencyContactRepository.getEmergencyContacts(userId)
                result.fold(
                    onSuccess = { contacts ->
                        emergencyContacts.clear()
                        emergencyContacts.addAll(contacts)
                        emergencyContactsAdapter.notifyDataSetChanged()
                        updateEmergencyContactsUI()
                        Log.d(TAG, "Loaded ${contacts.size} emergency contacts")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error loading emergency contacts: ${exception.message}")
                        Toast.makeText(this@ProfileActivity, "Error loading emergency contacts", Toast.LENGTH_SHORT).show()
                    }
                )

                // Set up real-time listener
                emergencyContactsListener = emergencyContactRepository.listenToEmergencyContacts(userId) { contacts ->
                    runOnUiThread {
                        emergencyContacts.clear()
                        emergencyContacts.addAll(contacts)
                        emergencyContactsAdapter.notifyDataSetChanged()
                        updateEmergencyContactsUI()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error setting up emergency contacts: ${e.message}", e)
                Toast.makeText(this@ProfileActivity, "Error loading emergency contacts", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddContactDialog() {
        showContactDialog(null)
    }

    private fun showEditContactDialog(contact: EmergencyContact) {
        showContactDialog(contact)
    }

    private fun showContactDialog(existingContact: EmergencyContact?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_emergency_contact, null)

        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etContactName = dialogView.findViewById<TextInputEditText>(R.id.etContactName)
        val etContactPhone = dialogView.findViewById<TextInputEditText>(R.id.etContactPhone)
        val etContactRelationship = dialogView.findViewById<TextInputEditText>(R.id.etContactRelationship)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

        // Set title and populate fields if editing
        if (existingContact != null) {
            tvDialogTitle.text = "Edit Emergency Contact"
            etContactName.setText(existingContact.name)
            etContactPhone.setText(existingContact.phoneNumber)
            etContactRelationship.setText(existingContact.relationship)
            btnSave.text = "Update"
        } else {
            tvDialogTitle.text = "Add Emergency Contact"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val name = etContactName.text.toString().trim()
            val phone = etContactPhone.text.toString().trim()
            val relationship = etContactRelationship.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty() || relationship.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = sessionManager.getCurrentUserId()
            if (userId == null) {
                Toast.makeText(this@ProfileActivity, "Error: No user ID found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            btnSave.text = "Saving..."

            val contact = EmergencyContact(
                id = existingContact?.id ?: UUID.randomUUID().toString(),
                name = name,
                phoneNumber = phone,
                relationship = relationship
            )

            lifecycleScope.launch {
                try {
                    val result = if (existingContact != null) {
                        emergencyContactRepository.updateEmergencyContact(userId, contact)
                    } else {
                        emergencyContactRepository.createEmergencyContact(userId, contact)
                    }

                    result.fold(
                        onSuccess = {
                            val message = if (existingContact != null) "Contact updated" else "Contact added"
                            Toast.makeText(this@ProfileActivity, message, Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        },
                        onFailure = { exception ->
                            Log.e(TAG, "Error saving contact: ${exception.message}")
                            Toast.makeText(this@ProfileActivity, "Error saving contact", Toast.LENGTH_SHORT).show()
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving emergency contact: ${e.message}", e)
                    Toast.makeText(this@ProfileActivity, "Error saving contact", Toast.LENGTH_SHORT).show()
                } finally {
                    btnSave.isEnabled = true
                    btnSave.text = if (existingContact != null) "Update" else "Save"
                }
            }
        }

        dialog.show()
    }

    private fun showDeleteContactDialog(contact: EmergencyContact) {
        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteEmergencyContact(contact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteEmergencyContact(contact: EmergencyContact) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            Toast.makeText(this@ProfileActivity, "Error: No user ID found", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val result = emergencyContactRepository.deleteEmergencyContact(userId, contact.id)
                result.fold(
                    onSuccess = {
                        Toast.makeText(this@ProfileActivity, "Contact deleted", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Emergency contact deleted: ${contact.id}")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error deleting contact: ${exception.message}")
                        Toast.makeText(this@ProfileActivity, "Error deleting contact", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting emergency contact: ${e.message}", e)
                Toast.makeText(this@ProfileActivity, "Error deleting contact", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEmergencyContactsUI() {
        if (emergencyContacts.isEmpty()) {
            llEmptyState.visibility = View.VISIBLE
            rvEmergencyContacts.visibility = View.GONE
        } else {
            llEmptyState.visibility = View.GONE
            rvEmergencyContacts.visibility = View.VISIBLE
        }
    }

    private fun showChangePasswordDialog() {
        // Implement password change functionality
        Toast.makeText(this@ProfileActivity, "Password change functionality coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Delete Account") { _, _ ->
                performAccountDeletion()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performAccountDeletion() {
        lifecycleScope.launch {
            try {
                val userId = sessionManager.getCurrentUserId()
                if (userId == null) {
                    Toast.makeText(this@ProfileActivity, "Error: No user ID found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Delete all emergency contacts first
                emergencyContactRepository.deleteAllEmergencyContacts(userId)

                // Delete user account (this also clears the session)
                val success = sessionManager.deleteUserAccount()

                if (success) {
                    Toast.makeText(this@ProfileActivity, "Account deleted successfully", Toast.LENGTH_SHORT).show()

                    // Navigate to login
                    val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@ProfileActivity, "Failed to delete account", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting account: ${e.message}", e)
                Toast.makeText(this@ProfileActivity, "Error deleting account: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}   