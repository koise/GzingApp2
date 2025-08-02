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
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_USER_ID, userId)
        editor.putString(KEY_USER_NAME, userName)
        editor.putBoolean(KEY_IS_ANONYMOUS, isAnonymous)
        editor.apply()
    }
    
    fun getSession(): Map<String, Any?> {
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        val userName = sharedPreferences.getString(KEY_USER_NAME, null)
        val isAnonymous = sharedPreferences.getBoolean(KEY_IS_ANONYMOUS, false)
        
        return mapOf(
            "isLoggedIn" to isLoggedIn,
            "userId" to userId,
            "userName" to userName,
            "isAnonymous" to isAnonymous
        )
    }
    
    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    fun isAnonymous(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_ANONYMOUS, false)
    }
    
    fun clearSession() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
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
} 