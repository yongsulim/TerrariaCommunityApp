package com.example.terrariacommunityapp

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val notificationsCollection = db.collection("notifications")

    // 알림 목록 불러오기 (userId 기준)
    suspend fun getNotifications(userId: String): List<Notification> {
        return try {
            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Notification::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 알림 읽음 처리
    suspend fun markAsRead(notificationId: String) {
        try {
            notificationsCollection.document(notificationId)
                .update("isRead", true)
                .await()
        } catch (e: Exception) {
            // 예외 무시 또는 로깅
        }
    }

    // 모든 알림을 읽음 처리
    suspend fun markAllAsRead(userId: String) {
        try {
            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()
        } catch (e: Exception) {
            // 예외 무시 또는 로깅
        }
    }

    // 알림 삭제
    suspend fun deleteNotification(notificationId: String) {
        try {
            notificationsCollection.document(notificationId)
                .delete()
                .await()
        } catch (e: Exception) {
            // 예외 무시 또는 로깅
        }
    }

    // 멘션 알림 생성
    suspend fun sendMentionNotification(
        mentionedUserId: String,
        content: String,
        postId: String = "",
        commentId: String = "",
        senderName: String = ""
    ) {
        try {
            val notification = Notification(
                userId = mentionedUserId,
                type = "mention",
                content = "${senderName}님이 회원님을 멘션했습니다: $content",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                postId = postId,
                commentId = commentId
            )
            notificationsCollection.add(notification).await()
        } catch (e: Exception) {
            // 예외 무시 또는 로깅
        }
    }
} 