package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PhotoItem
import com.example.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class CameraViewModel(private val repository: PhotoRepository) : ViewModel() {

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _processingStrength = MutableStateFlow("standard") // subtle, standard, strong
    val processingStrength: StateFlow<String> = _processingStrength.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    val latestPhoto: StateFlow<PhotoItem?> = repository.latestPhoto
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleFlash() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun switchCamera() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun setProcessingStrength(strength: String) {
        _processingStrength.value = strength
    }

    fun capturePhoto(
        imageCapture: ImageCapture,
        executor: Executor,
        onPhotoCaptured: (Long) -> Unit
    ) {
        if (_isCapturing.value) return
        _isCapturing.value = true

        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val bitmap = imageProxy.toBitmapWithRotation()
                imageProxy.close()

                viewModelScope.launch {
                    val strength = _processingStrength.value
                    val photoItem = repository.saveCapturedRawPhoto(bitmap, strength)
                    _isCapturing.value = false

                    // Trigger Gemini processing asynchronously
                    launch {
                        repository.processPhotoWithGemini(photoItem.id, strength)
                    }

                    // Navigate to review screen
                    onPhotoCaptured(photoItem.id)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                _isCapturing.value = false
            }
        })
    }

    private fun ImageProxy.toBitmapWithRotation(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val rotationDegrees = imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    class Factory(private val repository: PhotoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CameraViewModel(repository) as T
        }
    }
}
