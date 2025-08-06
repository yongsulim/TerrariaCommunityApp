package com.yongsulim.terrariacommunity

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val notificationsCollection = db.collection("notifications")
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val NOTIFICATION_SERVER_URL = "http://10.0.2.2:3000/sendNotification"

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

    // FCM 푸시 전송 함수
    suspend fun sendFcmPush(token: String, title: String, body: String) {
        try {
            val json = JSONObject()
            json.put("token", token)
            json.put("title", title)
            json.put("body", body)

            val requestBody = json.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url(NOTIFICATION_SERVER_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            // 로그 등 필요시 처리 가능
        } catch (e: Exception) {
            // 예외 무시 또는 로깅
        }
    }

    // 멘션 알림 생성 + FCM 푸시
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
            // FCM 푸시 전송 (유저의 fcmToken 필요)
            val userRepo = UserRepository()
            val user = userRepo.getUser(mentionedUserId)
            val fcmToken = user?.fcmToken
            if (!fcmToken.isNullOrBlank()) {
                sendFcmPush(
                    token = fcmToken,
                    title = "${senderName}님이 회원님을 멘션했습니다",
                    body = content
                )
            }
        } catch (e: Exception) {
            // 예외 무시 또는 로깅
        }
    }
} 