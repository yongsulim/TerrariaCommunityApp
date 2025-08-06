package com.example.terrariacommunity

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ChatRepository {
    private val db = FirebaseFirestore.getInstance()
    private val roomsCollection = db.collection("chatRooms")
    private val messagesCollection = db.collection("chatMessages")
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val NOTIFICATION_SERVER_URL = "http://10.0.2.2:3000/sendNotification"

    // 채팅방 생성
    suspend fun createRoom(room: ChatRoom): String? {
        return try {
            val newDocRef = roomsCollection.add(room).await()
            roomsCollection.document(newDocRef.id).update("id", newDocRef.id).await()
            newDocRef.id
        } catch (e: Exception) {
            null
        }
    }

    // 내 채팅방 목록 조회
    suspend fun getRoomsForUser(userId: String): List<ChatRoom> {
        return try {
            roomsCollection.whereArrayContains("participantIds", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get().await().toObjects(ChatRoom::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 메시지 전송
    suspend fun sendMessage(message: ChatMessage): String? {
        return try {
            val newDocRef = messagesCollection.add(message).await()
            messagesCollection.document(newDocRef.id).update("id", newDocRef.id).await()
            newDocRef.id
        } catch (e: Exception) {
            null
        }
    }

    // 채팅 메시지 전송 시, 참여자들에게 FCM 푸시 알림 전송
    suspend fun sendChatNotificationToParticipants(room: ChatRoom, senderId: String, message: String, userRepository: UserRepository) {
        for (participantId in room.participantIds) {
            if (participantId == senderId) continue
            val user = userRepository.getUser(participantId)
            val fcmToken = user?.fcmToken
            if (!fcmToken.isNullOrBlank()) {
                try {
                    val json = JSONObject()
                    json.put("token", fcmToken)
                    json.put("title", "${room.name}")
                    json.put("body", message)
                    val requestBody = json.toString().toRequestBody(JSON)
                    val request = Request.Builder()
                        .url(NOTIFICATION_SERVER_URL)
                        .post(requestBody)
                        .build()
                    client.newCall(request).execute()
                } catch (_: Exception) {}
            }
        }
    }
    // sendMessage 호출 후 sendChatNotificationToParticipants를 호출해야 알림이 전송됩니다.

    // 특정 채팅방의 메시지 실시간 수신 (최신순)
    fun listenMessages(roomId: String, onMessagesChanged: (List<ChatMessage>) -> Unit): ListenerRegistration {
        return messagesCollection.whereEqualTo("roomId", roomId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val messages = snapshot.toObjects(ChatMessage::class.java)
                    onMessagesChanged(messages)
                }
            }
    }

    // 채팅방 퇴장(나가기)
    suspend fun leaveRoom(roomId: String, userId: String): Boolean {
        return try {
            val roomRef = roomsCollection.document(roomId)
            val snapshot = roomRef.get().await()
            val room = snapshot.toObject(ChatRoom::class.java)
            if (room != null) {
                val updatedParticipants = room.participantIds.filter { it != userId }
                if (updatedParticipants.isEmpty()) {
                    // 마지막 사용자가 나가면 방 삭제
                    roomRef.delete().await()
                } else {
                    roomRef.update("participantIds", updatedParticipants).await()
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
            }
    }
} 