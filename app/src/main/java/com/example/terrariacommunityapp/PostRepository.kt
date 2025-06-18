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

    // 모든 게시물 조회 (최신순)
    suspend fun getPosts(): List<Post> {
        return try {
            postsCollection.orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
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
} 