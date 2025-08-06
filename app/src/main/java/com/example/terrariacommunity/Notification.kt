package com.example.terrariacommunity

// 알림 데이터 모델

data class Notification(
    val id: String = "",
    val userId: String = "",
    val type: String = "", // 예: "comment", "like", "mention", "popular"
    val content: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val postId: String = "",
    val commentId: String = ""
) 