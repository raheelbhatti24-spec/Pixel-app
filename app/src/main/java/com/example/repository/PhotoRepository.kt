package com.example.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.api.GeminiApiService
import com.example.api.GeminiContent
import com.example.api.GeminiGenerationConfig
import com.example.api.GeminiInlineData
import com.example.api.GeminiPart
import com.example.api.GeminiRequest
import com.example.data.PhotoDao
import com.example.data.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class PhotoRepository(
    private val context: Context,
    private val photoDao: PhotoDao,
    private val apiService: GeminiApiService = GeminiApiService.create()
) {
    val allPhotos: Flow<List<PhotoItem>> = photoDao.getAllPhotos()
    val latestPhoto: Flow<PhotoItem?> = photoDao.getLatestPhoto()

    fun getPhotoById(id: Long): Flow<PhotoItem?> = photoDao.getPhotoById(id)

    suspend fun saveCapturedRawPhoto(bitmap: Bitmap, strength: String = "standard"): PhotoItem = withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val timestamp = System.currentTimeMillis()
        val rawFile = File(photosDir, "raw_$timestamp.jpg")

        FileOutputStream(rawFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        val photoItem = PhotoItem(
            timestamp = timestamp,
            rawFilePath = rawFile.absolutePath,
            processingStrength = strength,
            status = "PROCESSING"
        )

        val newId = photoDao.insertPhoto(photoItem)
        return@withContext photoItem.copy(id = newId)
    }

    suspend fun processPhotoWithGemini(photoId: Long, strength: String? = null) = withContext(Dispatchers.IO) {
        val photo = photoDao.getPhotoByIdSync(photoId) ?: return@withContext
        val activeStrength = strength ?: photo.processingStrength

        // Update status to PROCESSING
        photoDao.updatePhoto(photo.copy(status = "PROCESSING", errorMessage = null, processingStrength = activeStrength))

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            photoDao.updatePhoto(photo.copy(
                status = "FAILED",
                errorMessage = "Gemini API Key is missing or default. Please add your key in the AI Studio Secrets panel."
            ))
            return@withContext
        }

        val rawFile = File(photo.rawFilePath)
        if (!rawFile.exists()) {
            photoDao.updatePhoto(photo.copy(status = "FAILED", errorMessage = "Original photo file not found"))
            return@withContext
        }

        try {
            val base64Image = encodeFileToBase64(rawFile)
            val promptText = buildPixelPrompt(activeStrength)

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = promptText),
                            GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image))
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    responseModalities = listOf("IMAGE", "TEXT"),
                    temperature = 0.3f
                )
            )

            val response = try {
                apiService.processImage(model = "gemini-2.5-flash-image", apiKey = apiKey, request = request)
            } catch (e: Exception) {
                Log.w("PhotoRepository", "Primary model failed, trying fallback model", e)
                apiService.processImage(model = "gemini-3.1-flash-image-preview", apiKey = apiKey, request = request)
            }

            if (response.error != null) {
                photoDao.updatePhoto(photo.copy(
                    status = "FAILED",
                    errorMessage = response.error.message ?: "Gemini processing failed with code ${response.error.code}"
                ))
                return@withContext
            }

            val parts = response.candidates?.firstOrNull()?.content?.parts
            var editedImageBytes: ByteArray? = null

            if (parts != null) {
                for (part in parts) {
                    if (part.inlineData?.data != null) {
                        editedImageBytes = Base64.decode(part.inlineData.data, Base64.DEFAULT)
                        break
                    }
                }
            }

            if (editedImageBytes != null && editedImageBytes.isNotEmpty()) {
                val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
                val editedFile = File(photosDir, "edited_${photo.timestamp}_$activeStrength.jpg")
                FileOutputStream(editedFile).use { it.write(editedImageBytes) }

                photoDao.updatePhoto(photo.copy(
                    editedFilePath = editedFile.absolutePath,
                    status = "SUCCESS",
                    processingStrength = activeStrength,
                    errorMessage = null
                ))
            } else {
                val responseText = parts?.firstOrNull { it.text != null }?.text
                photoDao.updatePhoto(photo.copy(
                    status = "FAILED",
                    errorMessage = responseText ?: "No image returned from Gemini edit call"
                ))
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Error processing image with Gemini", e)
            photoDao.updatePhoto(photo.copy(
                status = "FAILED",
                errorMessage = e.localizedMessage ?: "Network or processing timeout error"
            ))
        }
    }

    suspend fun saveToGallery(photoId: Long): Boolean = withContext(Dispatchers.IO) {
        val photo = photoDao.getPhotoByIdSync(photoId) ?: return@withContext false
        val sourceFilePath = photo.editedFilePath ?: photo.rawFilePath
        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) return@withContext false

        try {
            val filename = "PixelShot_${photo.timestamp}_${photo.processingStrength}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelShotAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                photoDao.updatePhoto(photo.copy(isSavedToGallery = true))
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to save photo to gallery", e)
        }
        return@withContext false
    }

    suspend fun deletePhoto(photoId: Long) = withContext(Dispatchers.IO) {
        val photo = photoDao.getPhotoByIdSync(photoId) ?: return@withContext
        try {
            File(photo.rawFilePath).takeIf { it.exists() }?.delete()
            photo.editedFilePath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Error deleting photo files", e)
        }
        photoDao.deletePhotoById(photoId)
    }

    private fun encodeFileToBase64(file: File): String {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val resized = resizeBitmapIfNeeded(bitmap, 1500) // Keep dimensions reasonable for Gemini API
        val byteArrayOutputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width > height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun buildPixelPrompt(strength: String): String {
        val strengthInstruction = when (strength.lowercase()) {
            "subtle" -> "Apply a subtle Google Pixel camera tone enhancement: slight shadow detail recovery, accurate neutral white balance, and natural soft sharpening."
            "strong" -> "Apply strong Google Pixel camera post-processing: rich HDR+ tone mapping, dramatic shadow detail recovery without blowing out highlights, vibrant signature Pixel color science, sharp subject detail, portrait depth blur if applicable, and smooth noise reduction."
            else -> "Apply signature Google Pixel camera post-processing: HDR+ style dynamic range recovery in shadows and highlights, true-to-life skin tones, slightly boosted punchy color science, strong natural local contrast and sharpening on subjects, clean realistic noise reduction, and neutral white balance."
        }

        return """
            You are an advanced computational photography engine simulating Google Pixel Camera post-processing.
            $strengthInstruction
            Please return ONLY the processed photo with these precise image enhancements applied. Maintain the original composition and elements.
        """.trimIndent()
    }
}
