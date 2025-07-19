package com.example.terrariacommunityapp

data class ChatMessage(
    val id: String = "",
    val roomId: String = "", // 채팅방 ID
    val senderId: String = "", // 보낸 사람 UID
    val senderName: String = "", // 보낸 사람 닉네임
    val content: String = "", // 메시지 내용
    val timestamp: Long = System.currentTimeMillis()
) 