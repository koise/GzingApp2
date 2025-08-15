package com.example.gzingapp.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.gzingapp.models.User
import com.example.gzingapp.repositories.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class SessionManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "GzingAppPrefs", Context.MODE_PRIVATE
    )
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val userRepository = UserRepository()
    
    companion object {
        private const val TAG = "SessionManager"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_USER_ID = "userId"
        private const val KEY_USER_NAME = "userName"

        private const val KEY_FIRST_NAME = "firstName"
        private const val KEY_LAST_NAME = "lastName"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
    }
    
    fun saveSession(userId: String, userName: String) {
        Log.d(TAG, "Saving session - UserId: $userId, UserName: $userName")
        
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_USER_ID, userId)
        editor.putString(KEY_USER_NAME, userName)
        editor.apply()
        
        Log.d(TAG, "Session saved successfully")
    }
    
    fun getSession(): Map<String, Any?> {
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        val userName = sharedPreferences.getString(KEY_USER_NAME, null)

        val firstName = sharedPreferences.getString(KEY_FIRST_NAME, null)
        val lastName = sharedPreferences.getString(KEY_LAST_NAME, null)
        val email = sharedPreferences.getString(KEY_EMAIL, null)
        val phone = sharedPreferences.getString(KEY_PHONE, null)
        
        val session = mapOf(
            "isLoggedIn" to isLoggedIn,
            "userId" to userId,
            "userName" to userName,

            "firstName" to firstName,
            "lastName" to lastName,
            "email" to email,
            "phone" to phone
        )
        
        Log.d(TAG, "Retrieved session - IsLoggedIn: $isLoggedIn, UserId: $userId, UserName: $userName")
        
        return session
    }
    
    fun isLoggedIn(): Boolean {
        val loggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        Log.d(TAG, "Checking login status: $loggedIn")
        return loggedIn
    }
    

    
    /**
     * Check if there's an active session
     * This should be used instead of isLoggedIn() to prevent redirect loops
     */
    fun hasActiveSession(): Boolean {
        val hasSession = isLoggedIn()
        val userId = getUserId()
        Log.d(TAG, "Checking active session - hasSession: $hasSession, userId: $userId")
        return hasSession && !userId.isNullOrEmpty()
    }
    
    /**
     * Check if user needs authentication
     */
    fun needsAuthentication(): Boolean {
        val needsAuth = !hasActiveSession()
        Log.d(TAG, "Checking if authentication needed: $needsAuth")
        return needsAuth
    }
    
    fun clearSession() {
        Log.d(TAG, "Clearing session...")
        
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
        
        Log.d(TAG, "Session cleared successfully")
    }
    
    // Added logout method to handle logout functionality
    fun logout() {
        Log.d(TAG, "Logging out user...")
        auth.signOut()
        clearSession()
        Log.d(TAG, "User logged out successfully")
    }
    
    suspend fun fetchCurrentUserData(userId: String): User? {
        return try {
            val result = userRepository.getUserById(userId)
            result.fold(
                onSuccess = { user ->
                    Log.d(TAG, "User data fetched successfully: ${user?.email}")
                    user
                },
                onFailure = { exception ->
                    Log.e(TAG, "Error fetching user data: ${exception.message}")
                    null
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching user data: ${e.message}")
            null
        }
    }
    
    suspend fun getCurrentUser(): User? {
        val session = getSession()
        val userId = session["userId"] as String? ?: return null
        return fetchCurrentUserData(userId)
    }
    
    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }
    
    /**
     * Get current user ID (alias for getUserId for compatibility)
     */
    fun getCurrentUserId(): String? {
        return getUserId()
    }
    
    /**
     * Update user data in Firebase and local session
     */
    suspend fun updateUserData(updates: Map<String, Any>): Boolean {
        return try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "Cannot update user data: No user ID found")
                return false
            }
            
            // Update in Firebase
            val result = userRepository.updateUser(userId, updates)
            result.fold(
                onSuccess = { success ->
                    if (success) {
                        // Update local session data
                        val editor = sharedPreferences.edit()
                        updates.forEach { (key, value) ->
                            when (key) {
                                "firstName" -> editor.putString(KEY_FIRST_NAME, value.toString())
                                "lastName" -> editor.putString(KEY_LAST_NAME, value.toString())
                                "email" -> editor.putString(KEY_EMAIL, value.toString())
                                "phoneNumber" -> editor.putString(KEY_PHONE, value.toString())
                            }
                        }
                        editor.apply()
                        
                        Log.d(TAG, "User data updated successfully in Firebase and local session")
                        true
                    } else {
                        Log.e(TAG, "Failed to update user data in Firebase")
                        false
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "Error updating user data: ${exception.message}")
                    false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating user data: ${e.message}")
            false
        }
    }
    
    /**
     * Delete user account and clear session
     */
    suspend fun deleteUserAccount(): Boolean {
        return try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "Cannot delete account: No user ID found")
                return false
            }
            
            // Delete from Firebase
            val result = userRepository.deleteUser(userId)
            result.fold(
                onSuccess = { success ->
                    if (success) {
                        // Sign out from Firebase Auth
                        auth.currentUser?.let { user ->
                            user.delete().await()
                        }
                        
                        // Clear local session
                        clearSession()
                        
                        Log.d(TAG, "User account deleted successfully")
                        true
                    } else {
                        Log.e(TAG, "Failed to delete user account from Firebase")
                        false
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "Error deleting user account: ${exception.message}")
                    false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting user account: ${e.message}")
            false
        }
    }
    
    /**
     * Create new user in Firebase
     */
    suspend fun createUser(user: User): Result<User> {
        return try {
            val result = userRepository.createUser(user)
            result.fold(
                onSuccess = { createdUser ->
                    // Save session data
                    val editor = sharedPreferences.edit()
                    editor.putString(KEY_FIRST_NAME, createdUser.firstName)
                    editor.putString(KEY_LAST_NAME, createdUser.lastName)
                    editor.putString(KEY_EMAIL, createdUser.email)
                    editor.putString(KEY_PHONE, createdUser.phoneNumber)
                    editor.apply()
                    
                    Log.d(TAG, "User created successfully: ${createdUser.uid}")
                    Result.success(createdUser)
                },
                onFailure = { exception ->
                    Log.e(TAG, "Error creating user: ${exception.message}")
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating user: ${e.message}")
            Result.failure(e)
        }
    }
}