package com.example.gzingapp.repositories

import android.util.Log
import com.example.gzingapp.models.EmergencyContact
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await

class EmergencyContactRepository {
    private val database = FirebaseDatabase.getInstance()
    private val emergencyContactsRef = database.getReference("emergency_contacts")

    companion object {
        private const val TAG = "EmergencyContactRepository"
    }

    /**
     * Create a new emergency contact
     */
    suspend fun createEmergencyContact(userId: String, contact: EmergencyContact): Result<EmergencyContact> {
        return try {
            emergencyContactsRef.child(userId).child(contact.id).setValue(contact).await()
            Log.d(TAG, "Emergency contact created: ${contact.id}")
            Result.success(contact)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating emergency contact: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get all emergency contacts for a user
     */
    suspend fun getEmergencyContacts(userId: String): Result<List<EmergencyContact>> {
        return try {
            val snapshot = emergencyContactsRef.child(userId).get().await()
            val contacts = mutableListOf<EmergencyContact>()

            snapshot.children.forEach { childSnapshot ->
                childSnapshot.getValue(EmergencyContact::class.java)?.let { contact ->
                    contacts.add(contact)
                }
            }

            Log.d(TAG, "Retrieved ${contacts.size} emergency contacts for user: $userId")
            Result.success(contacts)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting emergency contacts: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get a specific emergency contact
     */
    suspend fun getEmergencyContact(userId: String, contactId: String): Result<EmergencyContact?> {
        return try {
            val snapshot = emergencyContactsRef.child(userId).child(contactId).get().await()
            val contact = snapshot.getValue(EmergencyContact::class.java)
            Log.d(TAG, "Emergency contact retrieved: $contactId")
            Result.success(contact)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting emergency contact: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update an emergency contact
     */
    suspend fun updateEmergencyContact(userId: String, contact: EmergencyContact): Result<EmergencyContact> {
        return try {
            emergencyContactsRef.child(userId).child(contact.id).setValue(contact).await()
            Log.d(TAG, "Emergency contact updated: ${contact.id}")
            Result.success(contact)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating emergency contact: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update specific fields of an emergency contact
     */
    suspend fun updateEmergencyContact(userId: String, contactId: String, updates: Map<String, Any>): Result<Boolean> {
        return try {
            emergencyContactsRef.child(userId).child(contactId).updateChildren(updates).await()
            Log.d(TAG, "Emergency contact updated: $contactId")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating emergency contact: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete an emergency contact
     */
    suspend fun deleteEmergencyContact(userId: String, contactId: String): Result<Boolean> {
        return try {
            emergencyContactsRef.child(userId).child(contactId).removeValue().await()
            Log.d(TAG, "Emergency contact deleted: $contactId")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting emergency contact: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete all emergency contacts for a user
     */
    suspend fun deleteAllEmergencyContacts(userId: String): Result<Boolean> {
        return try {
            emergencyContactsRef.child(userId).removeValue().await()
            Log.d(TAG, "All emergency contacts deleted for user: $userId")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all emergency contacts: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Listen for real-time updates to emergency contacts
     */
    suspend fun listenToEmergencyContacts(userId: String, onContactsUpdated: (List<EmergencyContact>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val contacts = mutableListOf<EmergencyContact>()
                snapshot.children.forEach { childSnapshot ->
                    childSnapshot.getValue(EmergencyContact::class.java)?.let { contact ->
                        contacts.add(contact)
                    }
                }
                onContactsUpdated(contacts)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Emergency contacts listener cancelled: ${error.message}")
                onContactsUpdated(emptyList())
            }
        }

        emergencyContactsRef.child(userId).addValueEventListener(listener)
        return listener
    }

    /**
     * Remove listener
     */
    fun removeEmergencyContactsListener(userId: String, listener: ValueEventListener) {
        emergencyContactsRef.child(userId).removeEventListener(listener)
    }
}