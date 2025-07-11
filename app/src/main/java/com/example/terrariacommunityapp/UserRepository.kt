package com.example.terrariacommunityapp

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    // 사용자 정보 가져오기 (UID 기준)
    suspend fun getUser(uid: String): User? {
        return try {
            usersCollection.document(uid).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting user: ${e.message}", e)
            null
        }
    }

    // 사용자 생성 또는 업데이트 (새 사용자이거나 닉네임 변경 시)
    suspend fun createUserOrUpdate(user: User): Boolean {
        return try {
            usersCollection.document(user.uid).set(user).await()
            true
        } catch (e: Exception) {
            Log.e("UserRepository", "Error creating or updating user: ${e.message}", e)
            false
        }
    }

    // 사용자 포인트 업데이트
    suspend fun updatePoints(uid: String, pointsToAdd: Long): Boolean {
        return try {
            db.runTransaction { transaction ->
                val userRef = usersCollection.document(uid)
                val snapshot = transaction.get(userRef)
                val currentPoints = snapshot.getLong("points") ?: 0L
                transaction.update(userRef, "points", currentPoints + pointsToAdd)
            }.await()
            true
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating points: ${e.message}", e)
            false
        }
    }

    // 사용자 뱃지 추가 (중복 방지)
    suspend fun addBadge(uid: String, badge: String): Boolean {
        return try {
            db.runTransaction { transaction ->
                val userRef = usersCollection.document(uid)
                val snapshot = transaction.get(userRef)
                @Suppress("UNCHECKED_CAST")
                val currentBadges = snapshot.get("badges") as? List<String> ?: emptyList()
                if (!currentBadges.contains(badge)) {
                    transaction.update(userRef, "badges", currentBadges + badge)
                }
            }.await()
            true
        } catch (e: Exception) {
            Log.e("UserRepository", "Error adding badge: ${e.message}", e)
            false
        }
    }
} 