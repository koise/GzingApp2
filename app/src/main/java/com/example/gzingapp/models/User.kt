package com.example.gzingapp.models

data class User(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val username: String = "",
    val isAnonymous: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Empty constructor for Firebase
    constructor() : this("", "", "", "", "", "", false, 0)
} 