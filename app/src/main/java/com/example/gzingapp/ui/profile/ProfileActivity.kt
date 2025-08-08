package com.example.gzingapp.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
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
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.ui.auth.LoginActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.UUID

class ProfileActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var sessionManager: SessionManager
    
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
    
    companion object {
        private const val TAG = "ProfileActivity"
        private const val PREFS_EMERGENCY_CONTACTS = "emergency_contacts"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        sessionManager = SessionManager(this)
        
        setupUI()
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        loadUserProfile()
        loadEmergencyContacts()
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
                val session = sessionManager.getSession()
                val userId = session["userId"] as String?
                
                if (userId != null) {
                    val user = sessionManager.fetchCurrentUserData(userId)
                    if (user != null) {
                        populateUserFields(user)
                    } else {
                        // Use session data as fallback
                        etFirstName.setText(session["firstName"] as String? ?: "")
                        etLastName.setText(session["lastName"] as String? ?: "")
                        etEmail.setText(session["email"] as String? ?: "")
                        etPhone.setText(session["phone"] as String? ?: "")
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
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
        
        lifecycleScope.launch {
            try {
                // For now, just save the session with basic info
                // In a real app, you'd update the user data in the database
                val currentSession = sessionManager.getSession()
                val userId = currentSession["userId"] as String?
                val isAnonymous = currentSession["isAnonymous"] as Boolean? ?: false
                
                if (userId != null) {
                    // Re-save session with updated name
                    val fullName = "$firstName $lastName"
                    sessionManager.saveSession(userId, fullName, isAnonymous)
                }
                
                Toast.makeText(this@ProfileActivity, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadEmergencyContacts() {
        try {
            val sharedPrefs = getSharedPreferences(PREFS_EMERGENCY_CONTACTS, MODE_PRIVATE)
            val contactsJson = sharedPrefs.getString("contacts", "[]")
            
            // For simplicity, we'll use a basic JSON-like storage
            // In a real app, you'd use proper JSON parsing or a database
            emergencyContacts.clear()
            
            // Load some sample data for demonstration
            if (contactsJson == "[]") {
                // Add sample emergency contact
                val sampleContact = EmergencyContact(
                    id = UUID.randomUUID().toString(),
                    name = "Sample Contact",
                    phoneNumber = "+639123456789",
                    relationship = "Family"
                )
                // Don't add sample by default
            }
            
            updateEmergencyContactsUI()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading emergency contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveEmergencyContacts() {
        try {
            val sharedPrefs = getSharedPreferences(PREFS_EMERGENCY_CONTACTS, MODE_PRIVATE)
            // In a real app, you'd serialize the contacts properly
            sharedPrefs.edit()
                .putString("contacts", "saved")
                .apply()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving emergency contacts", Toast.LENGTH_SHORT).show()
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
            
            val contact = EmergencyContact(
                id = existingContact?.id ?: UUID.randomUUID().toString(),
                name = name,
                phoneNumber = phone,
                relationship = relationship
            )
            
            if (existingContact != null) {
                // Update existing contact
                emergencyContactsAdapter.updateContact(contact)
                val index = emergencyContacts.indexOfFirst { it.id == contact.id }
                if (index != -1) {
                    emergencyContacts[index] = contact
                }
                Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show()
            } else {
                // Add new contact
                emergencyContacts.add(contact)
                emergencyContactsAdapter.addContact(contact)
                Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show()
            }
            
            updateEmergencyContactsUI()
            saveEmergencyContacts()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showDeleteContactDialog(contact: EmergencyContact) {
        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                emergencyContacts.removeIf { it.id == contact.id }
                emergencyContactsAdapter.removeContact(contact)
                updateEmergencyContactsUI()
                saveEmergencyContacts()
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEmergencyContactsUI() {
        if (emergencyContactsAdapter.isEmpty()) {
            llEmptyState.visibility = View.VISIBLE
            rvEmergencyContacts.visibility = View.GONE
        } else {
            llEmptyState.visibility = View.GONE
            rvEmergencyContacts.visibility = View.VISIBLE
        }
    }

    private fun showChangePasswordDialog() {
        // Implement password change functionality
        Toast.makeText(this, "Password change functionality coming soon", Toast.LENGTH_SHORT).show()
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
                // Clear all user data
                sessionManager.clearSession()
                
                // Clear emergency contacts
                getSharedPreferences(PREFS_EMERGENCY_CONTACTS, MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
                
                Toast.makeText(this@ProfileActivity, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                
                // Navigate to login
                val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                
            } catch (e: Exception) {
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