package com.sparkynox.lumix.helper

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sparkynox.lumix.model.HistoryItem
import kotlinx.coroutines.tasks.await

object FirestoreHelper {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun historyCollection() = auth.currentUser?.uid?.let { uid ->
        db.collection("users").document(uid).collection("history")
    }

    suspend fun saveHistory(item: HistoryItem) {
        val col = historyCollection() ?: return
        val data = hashMapOf(
            "videoId" to item.videoId,
            "title" to item.title,
            "uploader" to item.uploader,
            "thumbnailUrl" to item.thumbnailUrl,
            "watchedAt" to item.watchedAt
        )
        // Use videoId as doc ID so duplicates overwrite (moves to top via timestamp)
        col.document(item.videoId).set(data).await()
    }

    suspend fun getHistory(limit: Long = 50): List<HistoryItem> {
        val col = historyCollection() ?: return emptyList()
        val snapshot = col
            .orderBy("watchedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            try {
                HistoryItem(
                    videoId = doc.getString("videoId") ?: return@mapNotNull null,
                    title = doc.getString("title") ?: "Unknown",
                    uploader = doc.getString("uploader") ?: "",
                    thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
                    watchedAt = doc.getLong("watchedAt") ?: 0L
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun clearHistory() {
        val col = historyCollection() ?: return
        val docs = col.get().await()
        for (doc in docs.documents) {
            doc.reference.delete().await()
        }
    }
}
