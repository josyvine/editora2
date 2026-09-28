package com.vineyard.aivideostudio.ai.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.vineyard.aivideostudio.core.model.Caption

@JsonClass(generateAdapter = true)
data class MasterRecipe(
    val projectInfo: ProjectInfoDto? = null,
    val editingPlan: EditingPlanDto,
    val captions: List<RecipeCaptionDto> = emptyList(),
    val commentary: RecipeCommentaryDto
)

@JsonClass(generateAdapter = true)
data class ProjectInfoDto(
    val title: String? = null,
    val targetFormat: String? = "SHORTS", // "SHORTS" or "VIDEO"
    val targetAspectRatio: String? = "9:16", // "9:16", "16:9", "1:1", "ORIGINAL"
    val targetDurationSeconds: Double? = 60.0
)

@JsonClass(generateAdapter = true)
data class EditingPlanDto(
    val segmentsToKeep: List<HighlightSegmentDto> = emptyList(),
    val crop: RecipeCropDto? = null,
    val zoom: RecipeZoomDto? = null
)

@JsonClass(generateAdapter = true)
data class HighlightSegmentDto(
    val start: Double,
    val end: Double,
    val title: String? = "",
    val description: String? = ""
) {
    fun toHighlightSegment(): HighlightSegment {
        return HighlightSegment(
            start = start,
            end = end,
            title = title ?: "",
            description = description ?: "",
            importance = "CRITICAL"
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeCropDto(
    val isNecessary: Boolean = true,
    val x: Float = 0.0f,
    val y: Float = 0.0f,
    val width: Float = 1.0f,
    val height: Float = 1.0f,
    val targetAspectRatio: String = "9:16"
)

@JsonClass(generateAdapter = true)
data class RecipeZoomDto(
    val isNecessary: Boolean = true,
    val scale: Float = 1.25f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f
)

@JsonClass(generateAdapter = true)
data class RecipeCaptionDto(
    val start: Double,
    val end: Double,
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.90f,
    val colorHex: String = "#FFFFFF",
    val style: String = "BOLD"
) {
    fun toCaption(projectId: String, index: Int): Caption {
        return Caption(
            id = "cap_recipe_${index}_${System.currentTimeMillis()}",
            projectId = projectId,
            text = text,
            start = start,
            end = end,
            x = x,
            y = if (y in 0.75f..0.96f) 0.90f else y,
            fontSizeSp = 22f,
            fontColorHex = colorHex,
            backgroundColorHex = "#FF000000", // Solid opaque concealer mask
            style = style
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeCommentaryDto(
    val tone: String? = "Genre-adapted dynamic commentary",
    val voiceName: String? = "Puck",
    val fullScript: String,
    val segments: List<CommentarySegmentDto>? = null
)

@JsonClass(generateAdapter = true)
data class CommentarySegmentDto(
    val start: Double? = 0.0,
    val end: Double? = 0.0,
    val text: String
)