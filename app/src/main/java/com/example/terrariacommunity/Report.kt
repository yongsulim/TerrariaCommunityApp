package com.example.terrariacommunity

data class Report(
    val id: String = "",
    val reporterId: String = "", // 신고자 UID
    val targetId: String = "", // 신고 대상 ID (게시글ID 또는 댓글ID)
    val targetType: String = "", // "post" 또는 "comment"
    val reason: String = "", // 신고 사유
    val timestamp: Long = System.currentTimeMillis()
) 