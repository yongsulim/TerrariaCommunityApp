package com.example.terrariacommunity

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ReportRepository {
    private val db = FirebaseFirestore.getInstance()
    private val reportsCollection = db.collection("reports")

    // 신고 추가
    suspend fun addReport(report: Report): String? {
        return try {
            val newDocRef = reportsCollection.add(report).await()
            reportsCollection.document(newDocRef.id).update("id", newDocRef.id).await()
            newDocRef.id
        } catch (e: Exception) {
            null
        }
    }

    // (선택) 전체 신고 내역 조회 (관리자용)
    suspend fun getAllReports(): List<Report> {
        return try {
            reportsCollection.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get().await().toObjects(Report::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // (선택) 특정 대상에 대한 신고 내역 조회
    suspend fun getReportsForTarget(targetId: String): List<Report> {
        return try {
            reportsCollection.whereEqualTo("targetId", targetId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get().await().toObjects(Report::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
} 