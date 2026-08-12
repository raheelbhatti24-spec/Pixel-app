package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PhotoItem
import com.example.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReviewViewModel(
    private val repository: PhotoRepository,
    val photoId: Long
) : ViewModel() {

    val photoState: StateFlow<PhotoItem?> = repository.getPhotoById(photoId)
        .let { flow ->
            val state = MutableStateFlow<PhotoItem?>(null)
            viewModelScope.launch {
                flow.collect { state.value = it }
            }
            state.asStateFlow()
        }

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun reprocessWithStrength(strength: String) {
        viewModelScope.launch {
            repository.processPhotoWithGemini(photoId, strength)
        }
    }

    fun retryGeminiEdit() {
        val currentPhoto = photoState.value ?: return
        viewModelScope.launch {
            repository.processPhotoWithGemini(photoId, currentPhoto.processingStrength)
        }
    }

    fun saveToGallery(onComplete: (Boolean) -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        _saveMessage.value = null

        viewModelScope.launch {
            val success = repository.saveToGallery(photoId)
            _isSaving.value = false
            if (success) {
                _saveMessage.value = "Saved to Gallery (Pictures/PixelShotAI)"
            } else {
                _saveMessage.value = "Failed to save photo to Gallery"
            }
            onComplete(success)
        }
    }

    fun discardPhoto(onDiscarded: () -> Unit) {
        viewModelScope.launch {
            repository.deletePhoto(photoId)
            onDiscarded()
        }
    }

    fun clearMessage() {
        _saveMessage.value = null
    }

    class Factory(
        private val repository: PhotoRepository,
        private val photoId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReviewViewModel(repository, photoId) as T
        }
    }
}
