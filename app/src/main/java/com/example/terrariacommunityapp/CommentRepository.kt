package com.example.terrariacommunityapp

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class CommentRepository {
    private val db = FirebaseFirestore.getInstance()
    private val commentsCollection = db.collection("comments")
    private val postRepository = PostRepository()
    private val userRepository = UserRepository()
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val NOTIFICATION_SERVER_URL = "http://10.0.2.2:3000/sendNotification"

    // 댓글 추가
    suspend fun addComment(comment: Comment): String? {
        return try {
            val newDocRef = commentsCollection.add(comment).await()
            commentsCollection.document(newDocRef.id).update("id", newDocRef.id).await()

            // 게시물 작성자에게 알림 보내기
            val post = postRepository.getPost(comment.postId)
            val postAuthorUid = post?.authorId // Post 객체에 authorId 필드가 있어야 함
            val commentAuthor = comment.author
            val postTitle = post?.title

            if (postAuthorUid != null && postAuthorUid != comment.author) { // 게시물 작성자와 댓글 작성자가 다를 경우에만 알림
                val recipientUser = userRepository.getUser(postAuthorUid)
                val recipientFcmToken = recipientUser?.fcmToken

                if (recipientFcmToken != null) {
                    sendNotificationToBackend(
                        recipientFcmToken,
                        "${commentAuthor}님이 게시물에 댓글을 남겼습니다.",
                        "${postTitle ?: "제목 없음"}: ${comment.content}"
                    )
                }
            }

            // 멘션 알림 처리
            val mentionedNicknames = extractMentionedNicknames(comment.content)
            val notificationRepository = NotificationRepository()
            for (nickname in mentionedNicknames) {
                val mentionedUserId = userRepository.getUserIdByNickname(nickname)
                if (mentionedUserId != null && mentionedUserId != postAuthorUid) { // 자기 자신/게시글 작성자 중복 방지
                    notificationRepository.sendMentionNotification(
                        mentionedUserId = mentionedUserId,
                        content = comment.content,
                        postId = comment.postId,
                        commentId = newDocRef.id,
                        senderName = comment.author
                    )
                }
            }

            newDocRef.id
        } catch (e: Exception) {
            Log.e("CommentRepository", "Error adding comment or sending notification: ${e.message}", e)
            null
        }
    }

    private suspend fun sendNotificationToBackend(token: String, title: String, body: String) {
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
            Log.d("CommentRepository", "Notification response: ${response.body?.string()}")
        } catch (e: Exception) {
            Log.e("CommentRepository", "Error sending notification to backend: ${e.message}", e)
        }
    }

    // 특정 게시물의 댓글 조회 (최신순)
    suspend fun getCommentsForPost(postId: String): List<Comment> {
        return try {
            commentsCollection.whereEqualTo("postId", postId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Comment::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 댓글 좋아요 토글
    suspend fun toggleCommentLike(commentId: String, userId: String): Boolean {
        return try {
            val commentRef = commentsCollection.document(commentId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(commentRef)
                @Suppress("UNCHECKED_CAST")
                val likes: List<String> = snapshot.get("likedBy") as? List<String> ?: emptyList()
                val dislikes: List<String> = snapshot.get("dislikedBy") as? List<String> ?: emptyList()
                
                val newLikesCount = if (likes.contains(userId)) {
                    // 좋아요 취소
                    transaction.update(commentRef, "likedBy", likes - userId)
                    snapshot.getLong("likesCount")?.minus(1) ?: 0
                } else {
                    // 좋아요 추가 (싫어요가 있다면 제거)
                    val newLikes = likes + userId
                    val newDislikes = dislikes - userId
                    transaction.update(commentRef, "likedBy", newLikes)
                    transaction.update(commentRef, "dislikedBy", newDislikes)
                    snapshot.getLong("likesCount")?.plus(1) ?: 0
                }
                transaction.update(commentRef, "likesCount", newLikesCount)
            }.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 댓글 싫어요 토글
    suspend fun toggleCommentDislike(commentId: String, userId: String): Boolean {
        return try {
            val commentRef = commentsCollection.document(commentId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(commentRef)
                @Suppress("UNCHECKED_CAST")
                val likes: List<String> = snapshot.get("likedBy") as? List<String> ?: emptyList()
                val dislikes: List<String> = snapshot.get("dislikedBy") as? List<String> ?: emptyList()
                
                val newLikesCount = if (dislikes.contains(userId)) {
                    // 싫어요 취소
                    transaction.update(commentRef, "dislikedBy", dislikes - userId)
                    snapshot.getLong("likesCount") ?: 0
                } else {
                    // 싫어요 추가 (좋아요가 있다면 제거)
                    val newDislikes = dislikes + userId
                    val newLikes = likes - userId
                    transaction.update(commentRef, "dislikedBy", newDislikes)
                    transaction.update(commentRef, "likedBy", newLikes)
                    snapshot.getLong("likesCount")?.minus(1) ?: 0
                }
                transaction.update(commentRef, "likesCount", newLikesCount)
            }.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 댓글 수정
    suspend fun updateComment(commentId: String, newContent: String): Boolean {
        return try {
            commentsCollection.document(commentId).update("content", newContent).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 특정 댓글 조회 (좋아요 토글 시 사용)
    suspend fun getComment(commentId: String): Comment? {
        return try {
            commentsCollection.document(commentId).get().await().toObject(Comment::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 댓글 삭제
    suspend fun deleteComment(commentId: String): Boolean {
        return try {
            commentsCollection.document(commentId).delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 댓글 내용에서 @닉네임 패턴 추출
    fun extractMentionedNicknames(content: String): List<String> {
        val regex = Regex("@([a-zA-Z0-9가-힣_]+)")
        return regex.findAll(content).map { it.groupValues[1] }.toList()
    }
} 