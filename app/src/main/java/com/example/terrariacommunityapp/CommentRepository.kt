package com.example.terrariacommunityapp

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class CommentRepository {
    private val db = FirebaseFirestore.getInstance()
    private val commentsCollection = db.collection("comments")

    // 댓글 추가
    suspend fun addComment(comment: Comment): String? {
        return try {
            val newDocRef = commentsCollection.add(comment).await()
            commentsCollection.document(newDocRef.id).update("id", newDocRef.id).await()
            newDocRef.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
                val newLikesCount = if (likes.contains(userId)) {
                    transaction.update(commentRef, "likedBy", likes - userId)
                    snapshot.getLong("likesCount")?.minus(1) ?: 0
                } else {
                    transaction.update(commentRef, "likedBy", likes + userId)
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
} 