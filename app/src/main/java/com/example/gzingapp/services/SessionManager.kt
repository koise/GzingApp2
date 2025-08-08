package com.example.gzingapp.services

import android.content.Context
import android.content.SharedPreferences
import com.example.gzingapp.models.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class SessionManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "GzingAppPrefs", Context.MODE_PRIVATE
    )
    private val db = FirebaseFirestore.getInstance()
    
    companion object {
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_USER_ID = "userId"
        private const val KEY_USER_NAME = "userName"
        private const val KEY_IS_ANONYMOUS = "isAnonymous"
    }
    
    fun saveSession(userId: String, userName: String, isAnonymous: Boolean) {
        android.util.Log.d("SessionManager", "Saving session - UserId: $userId, UserName: $userName, IsAnonymous: $isAnonymous")
        
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_USER_ID, userId)
        editor.putString(KEY_USER_NAME, userName)
        editor.putBoolean(KEY_IS_ANONYMOUS, isAnonymous)
        editor.apply()
        
        android.util.Log.d("SessionManager", "Session saved successfully")
    }
    
    fun getSession(): Map<String, Any?> {
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        val userName = sharedPreferences.getString(KEY_USER_NAME, null)
        val isAnonymous = sharedPreferences.getBoolean(KEY_IS_ANONYMOUS, false)
        
        val session = mapOf(
            "isLoggedIn" to isLoggedIn,
            "userId" to userId,
            "userName" to userName,
            "isAnonymous" to isAnonymous
        )
        
        android.util.Log.d("SessionManager", "Retrieved session - IsLoggedIn: $isLoggedIn, UserId: $userId, UserName: $userName, IsAnonymous: $isAnonymous")
        
        return session
    }
    
    fun isLoggedIn(): Boolean {
        val loggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        android.util.Log.d("SessionManager", "Checking login status: $loggedIn")
        return loggedIn
    }
    
    fun isAnonymous(): Boolean {
        val anonymous = sharedPreferences.getBoolean(KEY_IS_ANONYMOUS, false)
        android.util.Log.d("SessionManager", "Checking anonymous status: $anonymous")
        return anonymous
    }
    
    fun clearSession() {
        android.util.Log.d("SessionManager", "Clearing session...")
        
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
        
        android.util.Log.d("SessionManager", "Session cleared successfully")
    }
    
    // Added logout method to handle logout functionality
    fun logout() {
        android.util.Log.d("SessionManager", "Logging out user...")
        clearSession()
        android.util.Log.d("SessionManager", "User logged out successfully")
    }
    
    suspend fun fetchCurrentUserData(userId: String): User? {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            
            if (userDoc.exists()) {
                userDoc.toObject(User::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
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
}