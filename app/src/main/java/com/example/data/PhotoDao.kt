package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<PhotoItem>>

    @Query("SELECT * FROM photos WHERE id = :id")
    fun getPhotoById(id: Long): Flow<PhotoItem?>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoByIdSync(id: Long): PhotoItem?

    @Query("SELECT * FROM photos ORDER BY timestamp DESC LIMIT 1")
    fun getLatestPhoto(): Flow<PhotoItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoItem): Long

    @Update
    suspend fun updatePhoto(photo: PhotoItem)

    @Delete
    suspend fun deletePhoto(photo: PhotoItem)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)
}
