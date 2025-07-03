package com.example.terrariacommunityapp

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class PostRepository {
    private val db = FirebaseFirestore.getInstance()
    private val postsCollection = db.collection("posts")

    // 게시물 생성
    suspend fun addPost(post: Post): String? {
        return try {
            val newDocRef = postsCollection.add(post).await()
            // Firestore document ID를 Post 객체에 저장하기 위해 업데이트
            postsCollection.document(newDocRef.id).update("id", newDocRef.id).await()
            newDocRef.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 모든 게시물 조회 (최신순) 또는 특정 카테고리 게시물 조회
    suspend fun getPosts(category: String? = null): List<Post> {
        return try {
            var query: Query = postsCollection.orderBy("timestamp", Query.Direction.DESCENDING)
            if (!category.isNullOrBlank()) {
                query = query.whereEqualTo("category", category)
            }
            query.get()
                .await()
                .toObjects(Post::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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

    // 게시물 업데이트
    suspend fun updatePost(post: Post): Boolean {
        return try {
            postsCollection.document(post.id).set(post).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

    // 게시물 좋아요 토글
    suspend fun togglePostLike(postId: String, userId: String): Boolean {
        return try {
            val postRef = postsCollection.document(postId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                val likedBy: List<String> = snapshot.get("likedBy") as? List<String> ?: emptyList()
                val newLikesCount = if (likedBy.contains(userId)) {
                    transaction.update(postRef, "likedBy", likedBy - userId)
                    snapshot.getLong("likesCount")?.minus(1) ?: 0
                } else {
                    transaction.update(postRef, "likedBy", likedBy + userId)
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

    // 인기 게시물 조회 (좋아요 수 기준 내림차순)
    suspend fun getPopularPosts(): List<Post> {
        return try {
            postsCollection.orderBy("likesCount", Query.Direction.DESCENDING)
                .limit(10) // 상위 10개 게시물만 가져오기 (원하는 개수로 조절 가능)
                .get()
                .await()
                .toObjects(Post::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 게시물 검색 (제목 또는 내용)
    suspend fun searchPosts(query: String, category: String? = null): List<Post> {
        return try {
            val allPosts = getPosts(category) // 카테고리 필터링된 모든 게시물을 가져옴
            if (query.isBlank()) {
                allPosts
            } else {
                allPosts.filter { post ->
                    post.title.contains(query, ignoreCase = true) ||
                            post.content.contains(query, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
} 