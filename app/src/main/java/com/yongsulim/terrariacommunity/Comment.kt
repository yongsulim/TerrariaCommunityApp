package com.yongsulim.terrariacommunity

data class Comment(
    val id: String = "",
    val postId: String = "", // 댓글이 속한 게시물의 ID
    val author: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Long = 0, // 댓글 좋아요 수
    val likedBy: List<String> = emptyList(), // 좋아요 누른 유저
    val dislikedBy: List<String> = emptyList(), // 싫어요 누른 유저
    val parentCommentId: String? = , 일반 댓글은 
) 