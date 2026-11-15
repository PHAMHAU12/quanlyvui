package com.example.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirestoreClientProvider {
    private const val TAG = "FirestoreProvider"

    fun getDatabase(context: Context): FirebaseFirestore {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        try {
            db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024L * 1024L)
                        .build()
                )
                .build()
        } catch (e: IllegalStateException) {
            Log.d(TAG, "Firestore settings already initialized: ${e.message}")
        }
        return db
    }
}
