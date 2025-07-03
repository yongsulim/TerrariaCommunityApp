package com.example.terrariacommunityapp

data class Comment(
    val id: String = "",
    val postId: String = "", // 댓글이 속한 게시물의 ID
    val author: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Int = 0, // 댓글 좋아요 수
    val likedBy: List<String> = emptyList() // Add this line to store user IDs who liked the comment
) 