package com.yongsulim.terrariacommunity

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import android.util.Log
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

class PostRepository {
    private val db = FirebaseFirestore.getInstance()
    private val postsCollection = db.collection("posts")
    private val storage = FirebaseStorage.getInstance()
    private val client = OkHttpClient()

    // 게시물 추가 또는 업데이트
    suspend fun addOrUpdatePost(post: Post): Boolean {
        return try {
            if (post.id.isEmpty()) {
            val newDocRef = postsCollection.add(post).await()
            postsCollection.document(newDocRef.id).update("id", newDocRef.id).await()
            } else {
                postsCollection.document(post.id).set(post).await()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 이미지 업로드
    suspend fun uploadImage(imageUri: Uri): String? {
        return try {
            val storageRef = storage.reference
            val imageName = "images/${System.currentTimeMillis()}_${imageUri.lastPathSegment}"
            val imageRef = storageRef.child(imageName)

            imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await()
            downloadUrl.toString()
        } catch (e: Exception) {
            Log.e("PostRepository", "Error uploading image: ${e.message}", e)
            
        }
    }

    // 특정 게시물 조회
    suspend fun getPost(postId: String): Post? {
        return try {
            postsCollection.document(postId).get().await().toObject(Post::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            
        }
    }

    // 모든 게시물 조회 (최신순)
    suspend fun getPosts(category: String? = ): List<Post> {
        return try {
            var query: Query = postsCollection
            if (category != ) {
                query = query.whereEqualTo("category", category)
            }
            query.orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Post::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 인기 게시물 조회 (좋아요 수 기준 상위 10개)
    suspend fun getPopularPosts(): List<Post> {
        return try {
            postsCollection.orderBy("likesCount", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()
                .toObjects(Post::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 게시물 삭제
    suspend fun deletePost(postId: String): Boolean {
        return try {
            postsCollection.document(postId).delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 게시물 검색
    suspend fun searchPosts(query: String, category: String? = ): List<Post> {
        return try {
            var baseQuery = postsCollection.orderBy("timestamp", Query.Direction.DESCENDING)
            if (category != ) {
                baseQuery = baseQuery.whereEqualTo("category", category)
            }
            val results = baseQuery
                .get()
                .await()
                .toObjects(Post::class.java)

            // 클라이언트 측에서 제목 또는 내용에 검색어가 포함된 게시물 필터링
            results.filter { it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e("PostRepository", "Error searching posts: ${e.message}", e)
            emptyList()
        }
    }

    // 게시물 좋아요 토글
    suspend fun togglePostLike(postId: String, userId: String): Boolean {
        return try {
            val postRef = postsCollection.document(postId)
            val newLikesCount = db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                @Suppress("UNCHECKED_CAST")
                val likes: List<String> = snapshot.get("likedBy") as? List<String> ?: emptyList()
                val newLikesCount = if (likes.contains(userId)) {
                    transaction.update(postRef, "likedBy", likes - userId)
                    snapshot.getLong("likesCount")?.minus(1) ?: 0
                } else {
                    transaction.update(postRef, "likedBy", likes + userId)
                    snapshot.getLong("likesCount")?.plus(1) ?: 0
                }
                transaction.update(postRef, "likesCount", newLikesCount)
                newLikesCount
            }.await()
            
            // 인기글 선정 알림 체크
            checkAndSendPopularPostNotification(postId, newLikesCount)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 내가 작성한 글 목록 조회
    suspend fun getPostsByAuthorId(authorId: String): List<Post> {
        return try {
            postsCollection.whereEqualTo("authorId", authorId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Post::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 사용자가 좋아요(즐겨찾기)한 글 목록 조회
    suspend fun getFavoritePostsByUserId(userId: String): List<Post> {
        return try {
            postsCollection.whereArrayContains("likedBy", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Post::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 인기글 선정 알림 (좋아요 수 기준)
    suspend fun checkAndSendPopularPostNotification(postId: String, currentLikesCount: Long) {
        val POPULAR_THRESHOLD = 10L // 인기글 기준 (좋아요 10개)
        
        if (currentLikesCount >= POPULAR_THRESHOLD) {
            try {
                val post = getPost(postId)
                post?.let { p ->
                    // 게시물 작성자에게 인기글 선정 알림
                    val postAuthorUid = p.authorId
                    if (postAuthorUid.isNotEmpty()) {
                        val userRepository = UserRepository()
                        val recipientUser = userRepository.getUser(postAuthorUid)
                        val recipientFcmToken = recipientUser?.fcmToken

                        if (recipientFcmToken != ) {
                            sendNotificationToBackend(
                                recipientFcmToken,
                                "인기글 선정!",
                                "${p.title}이 인기글에 선정되었습니다!"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PostRepository", "Error sending popular post notification: ${e.message}", e)
            }
        }
    }

    private suspend fun sendNotificationToBackend(token: String, title: String, body: String) {
        try {
            val json = JSONObject()
            json.put("token", token)
            json.put("title", title)
            json.put("body", body)

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://your-backend-url.com/send-notification") // 실제 백엔드 URL로 변경 필요
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            Log.d("PostRepository", "Notification response: ${response.body?.string()}")
        } catch (e: Exception) {
            Log.e("PostRepository", "Error sending notification to backend: ${e.message}", e)
        }
    }
} 