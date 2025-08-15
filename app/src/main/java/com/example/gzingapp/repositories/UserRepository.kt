package com.example.gzingapp.repositories

import android.util.Log
import com.example.gzingapp.models.User
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class UserRepository {
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")

    companion object {
        private const val TAG = "UserRepository"
    }

    /**
     * Create a new user in Firebase
     */
    suspend fun createUser(user: User): Result<User> {
        return try {
            usersRef.child(user.uid).setValue(user).await()
            Log.d(TAG, "User created successfully: ${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Read/Get a user by ID
     */
    suspend fun getUserById(userId: String): Result<User?> {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            val user = snapshot.getValue(User::class.java)
            Log.d(TAG, "User retrieved: ${user?.email ?: "null"}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update user information
     */
    suspend fun updateUser(userId: String, updates: Map<String, Any>): Result<Boolean> {
        return try {
            usersRef.child(userId).updateChildren(updates).await()
            Log.d(TAG, "User updated successfully: $userId")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update complete user object
     */
    suspend fun updateUser(user: User): Result<User> {
        return try {
            usersRef.child(user.uid).setValue(user).await()
            Log.d(TAG, "User updated successfully: ${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a user
     */
    suspend fun deleteUser(userId: String): Result<Boolean> {
        return try {
            usersRef.child(userId).removeValue().await()
            Log.d(TAG, "User deleted successfully: $userId")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Check if user exists
     */
    suspend fun userExists(userId: String): Result<Boolean> {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            val exists = snapshot.exists()
            Log.d(TAG, "User exists check: $userId = $exists")
            Result.success(exists)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if user exists: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get user by email (for login purposes)
     */
    suspend fun getUserByEmail(email: String): Result<User?> {
        return try {
            val snapshot = usersRef.orderByChild("email").equalTo(email).get().await()
            val user = snapshot.children.firstOrNull()?.getValue(User::class.java)
            Log.d(TAG, "User retrieved by email: ${user?.uid ?: "null"}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by email: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get all users (admin function)
     */
    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val snapshot = usersRef.get().await()
            val users = mutableListOf<User>()
            snapshot.children.forEach { childSnapshot ->
                childSnapshot.getValue(User::class.java)?.let { user ->
                    users.add(user)
                }
            }
            Log.d(TAG, "Retrieved ${users.size} users")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all users: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Listen for real-time user updates
     */
    suspend fun listenToUserUpdates(userId: String, onUserUpdated: (User?) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                onUserUpdated(user)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "User listener cancelled: ${error.message}")
                onUserUpdated(null)
            }
        }

        usersRef.child(userId).addValueEventListener(listener)
        return listener
    }

    /**
     * Remove listener
     */
    fun removeUserListener(userId: String, listener: ValueEventListener) {
        usersRef.child(userId).removeEventListener(listener)
    }

    /**
     * Batch update multiple users
     */
    suspend fun batchUpdateUsers(updates: Map<String, Map<String, Any>>): Result<Boolean> {
        return try {
            val batchUpdates = mutableMapOf<String, Any>()
            updates.forEach { (userId, userUpdates) ->
                userUpdates.forEach { (field, value) ->
                    batchUpdates["users/$userId/$field"] = value
                }
            }

            database.reference.updateChildren(batchUpdates).await()
            Log.d(TAG, "Batch update completed for ${updates.size} users")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error in batch update: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Search users by name
     */
    suspend fun searchUsersByName(searchTerm: String): Result<List<User>> {
        return try {
            val snapshot = usersRef.get().await()
            val users = mutableListOf<User>()

            snapshot.children.forEach { childSnapshot ->
                childSnapshot.getValue(User::class.java)?.let { user ->
                    val fullName = "${user.firstName} ${user.lastName}".lowercase()
                    if (fullName.contains(searchTerm.lowercase()) ||
                        user.firstName.lowercase().contains(searchTerm.lowercase()) ||
                        user.lastName.lowercase().contains(searchTerm.lowercase())) {
                        users.add(user)
                    }
                }
            }

            Log.d(TAG, "Search found ${users.size} users for term: $searchTerm")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users: ${e.message}", e)
            Result.failure(e)
        }
    }
}