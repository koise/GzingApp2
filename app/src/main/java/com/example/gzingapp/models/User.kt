package com.example.gzingapp.models

data class User(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val username: String = "",

    val createdAt: Long = System.currentTimeMillis()
) {
    // Alias for uid to maintain compatibility with code expecting 'id'
    val id: String
        get() = uid
    // Empty constructor for Firebase
    constructor() : this("", "", "", "", "", "", 0)
}