package com.example.terrariacommunityapp

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import android.util.Log
import android.net.Uri

class PostRepository {
    private val db = FirebaseFirestore.getInstance()
    private val postsCollection = db.collection("posts")
    private val storage = FirebaseStorage.getInstance()

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
            null
        }
    }

    // 특정 게시물 조회
    suspend fun getPost(postId: String): Post? {
        return try {
            postsCollection.document(postId).get().await().toObject(Post::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 모든 게시물 조회 (최신순)
    suspend fun getPosts(category: String? = null): List<Post> {
        return try {
            var query: Query = postsCollection
            if (category != null && category != "전체") {
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
    suspend fun searchPosts(query: String, category: String? = null): List<Post> {
        return try {
            var baseQuery = postsCollection.orderBy("timestamp", Query.Direction.DESCENDING)
            if (category != null && category != "전체") {
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
            db.runTransaction { transaction ->
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
            }.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
} 