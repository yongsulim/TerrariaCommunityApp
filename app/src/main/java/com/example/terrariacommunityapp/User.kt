package com.example.terrariacommunityapp

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val bio: String = "",
    val points: Long = 0L,
    val badges: List<String> = emptyList(),
    val fcmToken: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val notificationSettings: NotificationSettings = NotificationSettings()
)

data class NotificationSettings(
    val newComment: Boolean = true,
    val popularPost: Boolean = true,
    val mention: Boolean = true,
    val marketing: Boolean = false
) 