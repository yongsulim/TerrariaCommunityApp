package com.example.terrariacommunityapp

data class Post(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val author: String = "",
    val authorId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "자유",
    val likesCount: Long = 0,
    val likedBy: List<String> = emptyList(),
    val imageUrl: String? = null
) 