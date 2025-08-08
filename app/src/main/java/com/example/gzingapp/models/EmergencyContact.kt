package com.example.gzingapp.models

data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Get formatted phone number for display
     */
    fun getFormattedPhoneNumber(): String {
        // Simple formatting for Philippine numbers
        return when {
            phoneNumber.startsWith("+63") -> phoneNumber
            phoneNumber.startsWith("09") && phoneNumber.length == 11 -> {
                "+63${phoneNumber.substring(1)}"
            }
            phoneNumber.startsWith("9") && phoneNumber.length == 10 -> {
                "+63$phoneNumber"
            }
            else -> phoneNumber
        }
    }

    /**
     * Validate if contact data is complete
     */
    fun isValid(): Boolean {
        return name.isNotBlank() && 
               phoneNumber.isNotBlank() && 
               relationship.isNotBlank()
    }

    /**
     * Get contact initials for display
     */
    fun getInitials(): String {
        val nameParts = name.trim().split(" ")
        return when {
            nameParts.size >= 2 -> "${nameParts[0].firstOrNull()?.uppercaseChar()}${nameParts[1].firstOrNull()?.uppercaseChar()}"
            nameParts.size == 1 && nameParts[0].length >= 2 -> "${nameParts[0][0].uppercaseChar()}${nameParts[0][1].uppercaseChar()}"
            nameParts.size == 1 && nameParts[0].length == 1 -> nameParts[0].uppercase()
            else -> "??"
        }
    }
}