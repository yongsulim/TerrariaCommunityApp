package com.yongsulim.terrariacommunity

data class ChatRoom(
    val id: String = "",
    val name: String = "", // 방 이름(1:1은 상대 닉네임, 그룹은 그룹명)
    val participantIds: List<String> = emptyList(), // 참여자 UID 목록
    val isGroup: Boolean = false, // 그룹 채팅 여부
    val createdAt: Long = System.currentTimeMillis(),
    val profileImageUrl: String = "", // 채팅방 프로필 이미지 URL
    val description: String = "" // 채팅방 설명
) 