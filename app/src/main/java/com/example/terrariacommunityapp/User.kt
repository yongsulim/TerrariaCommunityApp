package com.example.terrariacommunityapp

data class User(
    val uid: String = "",
    val displayName: String = "",
    val points: Long = 0L,
    val badges: List<String> = emptyList(),
    val fcmToken: String? = null
) 