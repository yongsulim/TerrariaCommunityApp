package com.example.terrariacommunityapp

data class Post(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val author: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "",
    val likesCount: Int = 0,
    val likedBy: List<String> = emptyList()
) 