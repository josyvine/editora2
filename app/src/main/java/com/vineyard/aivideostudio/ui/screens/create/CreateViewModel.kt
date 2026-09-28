package com.vineyard.aivideostudio.ui.screens.create

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.VideoMetadata
import com.vineyard.aivideostudio.core.util.FileUtils
import com.vineyard.aivideostudio.core.util.JsonUtils
import com.vineyard.aivideostudio.core.util.UriUtils
import com.vineyard.aivideostudio.data.storage.ProjectStorageManager
import com.vineyard.aivideostudio.media.video.VideoMetadataReader
import com.vineyard.aivideostudio.media.video.VideoValidator
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class CreateUiState(
    val projectName: String = "",
    val selectedVideoUri: Uri? = null,
    val selectedVideoFileName: String? = null,
    val videoMetadata: VideoMetadata? = null,
    val targetAspectRatio: String = "ORIGINAL", // ORIGINAL, 9:16, 16:9, 1:1
    val youtubeUrl: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val createdProjectId: String? = null
) {
    val isYoutubeUrlValid: Boolean
        get() = isValidYoutubeUrl(youtubeUrl)

    val isShortsFormat: Boolean
        get() {
            val isVertical = (videoMetadata?.height ?: 0) > (videoMetadata?.width ?: 0)
            val lowerUrl = youtubeUrl.lowercase()
            val isShortsUrl = lowerUrl.contains("/shorts/") || lowerUrl.contains("/reel/") || lowerUrl.contains("/tiktok/")
            return isVertical || isShortsUrl
        }

    val isReadyToCreate: Boolean
        get() = selectedVideoUri != null && isYoutubeUrlValid && !isLoading

    companion object {
        /**
         * Validates that the provided reference URL is a valid public web video URL.
         * Supports any public domain (YouTube, Vimeo, example.com, custom streaming hosts).
         */
        fun isValidYoutubeUrl(url: String): Boolean {
            val trimmed = url.trim().lowercase()
            if (trimmed.isEmpty()) return false
            val isHttp = trimmed.startsWith("http://") || trimmed.startsWith("https://")
            val hasValidDomain = trimmed.contains(".") && trimmed.length > 8
            return isHttp && hasValidDomain
        }
    }
}

class CreateViewModel(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val storageManager: ProjectStorageManager,
    private val metadataReader: VideoMetadataReader
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    fun onProjectNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(projectName = name, errorMessage = null)
    }

    fun onAspectRatioChanged(ratio: String) {
        _uiState.value = _uiState.value.copy(targetAspectRatio = ratio)
    }

    fun onYoutubeUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(youtubeUrl = url, errorMessage = null)
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val metadata = metadataReader.readMetadata(uri)
            val validationError = VideoValidator.validateSourceVideo(metadata)
            if (validationError != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = validationError
                )
            } else {
                val fullFileName = UriUtils.getFileName(context, uri)
                val baseName = fullFileName.substringBeforeLast(".")
                val defaultName = if (_uiState.value.projectName.isBlank()) baseName else _uiState.value.projectName

                _uiState.value = _uiState.value.copy(
                    selectedVideoUri = uri,
                    selectedVideoFileName = fullFileName,
                    videoMetadata = metadata,
                    projectName = defaultName,
                    targetAspectRatio = "ORIGINAL",
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    fun createProject() {
        val state = _uiState.value

        // Mandatory validation 1: Source video file must be provided
        if (state.selectedVideoUri == null) {
            _uiState.value = state.copy(
                errorMessage = "Please select the source video file from your device (Shorts or Long-Form)."
            )
            return
        }

        // Mandatory validation 2: Source context link must be valid
        if (state.youtubeUrl.isBlank() || !state.isYoutubeUrlValid) {
            _uiState.value = state.copy(
                errorMessage = "Please provide a valid source context URL (e.g. https://example.com/video/... or a public video link)."
            )
            return
        }

        val name = if (state.projectName.isBlank()) {
            "Studio Video ${System.currentTimeMillis() % 1000}"
        } else {
            state.projectName.trim()
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val projectId = "proj_${UUID.randomUUID().toString().take(8)}"
                storageManager.setupProjectStructure(projectId)

                val metadata = state.videoMetadata ?: VideoMetadata()

                // Copy source media into sandboxed project storage
                val destFile = storageManager.getSourceFile(projectId)
                FileUtils.copyUriToFile(context, state.selectedVideoUri, destFile)
                val sourcePath = destFile.absolutePath
                val sourceUriString = Uri.fromFile(destFile).toString()

                val initialTimelineMap = TimelineMap.identity(projectId, metadata.durationSeconds)

                val project = Project(
                    id = projectId,
                    name = name,
                    sourceUri = sourceUriString,
                    sourcePath = sourcePath,
                    currentVideoUri = sourceUriString,
                    sourceYoutubeUrl = state.youtubeUrl.trim(),
                    metadata = metadata,
                    currentStage = PipelineStatus.IDLE,
                    status = PipelineStatus.IDLE,
                    targetAspectRatio = state.targetAspectRatio,
                    timelineMapJson = JsonUtils.toJson(initialTimelineMap)
                )

                projectRepository.saveProject(project)
                projectRepository.saveTimelineMap(projectId, initialTimelineMap)

                _uiState.value = state.copy(
                    isLoading = false,
                    createdProjectId = projectId
                )
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to initialize studio project: ${e.message}"
                )
            }
        }
    }

    fun resetCreatedState() {
        _uiState.value = _uiState.value.copy(createdProjectId = null)
    }
}