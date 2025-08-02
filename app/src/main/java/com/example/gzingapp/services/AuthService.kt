package com.example.gzingapp.services

import android.util.Log
import com.example.gzingapp.models.User
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthService {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val usernameCollection = db.collection("usernames")
    
    companion object {
        private const val TAG = "AuthService"
    }
    
    // Check if user is logged in
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }
    
    // Get current user
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    // Check if user is anonymous
    fun isUserAnonymous(): Boolean {
        return auth.currentUser?.isAnonymous ?: false
    }
    
    // Sign in anonymously
    suspend fun signInAnonymously(): Result<FirebaseUser> {
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user
            if (user != null) {
                // Just return success without creating a Firestore document
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to sign in anonymously"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signing in anonymously", e)
            Result.failure(e)
        }
    }
    
    // Sign up with email and password
    suspend fun signUp(
        firstName: String,
        lastName: String,
        email: String,
        phoneNumber: String,
        username: String,
        password: String
    ): Result<FirebaseUser> {
        return try {
            // Check if username is already taken
            val usernameDoc = usernameCollection.document(username).get().await()
            if (usernameDoc.exists()) {
                return Result.failure(Exception("Username already taken"))
            }
            
            // Create the user with email and password
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            if (firebaseUser != null) {
                // Update display name
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName("$firstName $lastName")
                    .build()
                
                firebaseUser.updateProfile(profileUpdates).await()
                
                // Create user document in Firestore
                val user = User(
                    uid = firebaseUser.uid,
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    phoneNumber = phoneNumber,
                    username = username,
                    isAnonymous = false
                )
                
                // Save the user to the database
                usersCollection.document(firebaseUser.uid).set(user).await()
                
                // Save username to a separate collection for uniqueness check
                usernameCollection.document(username).set(mapOf("uid" to firebaseUser.uid)).await()
                
                Result.success(firebaseUser)
            } else {
                Result.failure(Exception("Failed to create user"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signing up", e)
            Result.failure(e)
        }
    }
    
    // Sign in with username and password
    suspend fun signInWithUsername(username: String, password: String): Result<FirebaseUser> {
        return try {
            // First find the email associated with this username
            val usernameDoc = usernameCollection.document(username).get().await()
            
            if (!usernameDoc.exists()) {
                return Result.failure(Exception("Username not found"))
            }
            
            val uid = usernameDoc.getString("uid") ?: return Result.failure(Exception("Invalid username data"))
            val userDoc = usersCollection.document(uid).get().await()
            
            if (!userDoc.exists()) {
                return Result.failure(Exception("User data not found"))
            }
            
            val email = userDoc.getString("email") ?: return Result.failure(Exception("Email not found"))
            
            // Now sign in with the email and password
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("Authentication failed"))
            
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error signing in with username", e)
            Result.failure(e)
        }
    }
    
    // Sign in with email and password
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("Authentication failed"))
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error signing in with email", e)
            Result.failure(e)
        }
    }
    
    // Sign out
    fun signOut() {
        auth.signOut()
    }
    
    // Convert anonymous user to permanent account
    suspend fun linkAnonymousUser(
        firstName: String,
        lastName: String,
        email: String,
        phoneNumber: String,
        username: String,
        password: String
    ): Result<FirebaseUser> {
        val currentUser = auth.currentUser
        
        if (currentUser == null || !currentUser.isAnonymous) {
            return Result.failure(Exception("Not an anonymous user or user not logged in"))
        }
        
        return try {
            // Check if username is already taken
            val usernameDoc = usernameCollection.document(username).get().await()
            if (usernameDoc.exists()) {
                return Result.failure(Exception("Username already taken"))
            }
            
            // Update the user document with the new information
            val user = User(
                uid = currentUser.uid,
                firstName = firstName,
                lastName = lastName,
                email = email,
                phoneNumber = phoneNumber,
                username = username,
                isAnonymous = false
            )
            
            // Save the user to the database
            usersCollection.document(currentUser.uid).set(user).await()
            
            // Save username to a separate collection for uniqueness check
            usernameCollection.document(username).set(mapOf("uid" to currentUser.uid)).await()
            
            Result.success(currentUser)
        } catch (e: Exception) {
            Log.e(TAG, "Error linking anonymous user", e)
            Result.failure(e)
        }
    }
} 