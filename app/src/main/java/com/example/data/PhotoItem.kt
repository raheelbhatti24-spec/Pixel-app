package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rawFilePath: String,
    val editedFilePath: String? = null,
    val processingStrength: String = "standard", // subtle, standard, strong
    val status: String = "PROCESSING", // PROCESSING, SUCCESS, FAILED
    val errorMessage: String? = null,
    val isSavedToGallery: Boolean = false
)
