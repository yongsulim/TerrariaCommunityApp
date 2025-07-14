package com.example.terrariacommunityapp

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import android.util.Log
import android.net.Uri

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val storage = FirebaseStorage.getInstance()

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

    // 프로필 이미지 업로드
    suspend fun uploadProfileImage(uid: String, imageUri: Uri): String? {
        return try {
            val storageRef = storage.reference
            val imageName = "profile_images/${uid}_${System.currentTimeMillis()}"
            val imageRef = storageRef.child(imageName)

            imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await()
            downloadUrl.toString()
        } catch (e: Exception) {
            Log.e("UserRepository", "Error uploading profile image: ${e.message}", e)
            null
        }
    }

    // 프로필 업데이트
    suspend fun updateProfile(uid: String, displayName: String? = null, bio: String? = null, profileImageUrl: String? = null): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>()
            displayName?.let { updates["displayName"] = it }
            bio?.let { updates["bio"] = it }
            profileImageUrl?.let { updates["profileImageUrl"] = it }
            
            if (updates.isNotEmpty()) {
                usersCollection.document(uid).update(updates).await()
            }
            true
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating profile: ${e.message}", e)
            false
        }
    }

    // 알림 설정 업데이트
    suspend fun updateNotificationSettings(uid: String, settings: NotificationSettings): Boolean {
        return try {
            usersCollection.document(uid).update("notificationSettings", settings).await()
            true
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating notification settings: ${e.message}", e)
            false
        }
    }

    // 마지막 로그인 시간 업데이트
    suspend fun updateLastLogin(uid: String): Boolean {
        return try {
            usersCollection.document(uid).update("lastLoginAt", System.currentTimeMillis()).await()
            true
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating last login: ${e.message}", e)
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