package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TimeRange(
    val start: Double,
    val end: Double
) {
    val duration: Double get() = Math.max(0.0, end - start)
}

@JsonClass(generateAdapter = true)
data class TimelineSegment(
    val id: String,
    val projectId: String,
    val originalStart: Double,
    val originalEnd: Double,
    val currentStart: Double,
    val currentEnd: Double,
    val isRetained: Boolean = true,
    val stageApplied: PipelineStatus = PipelineStatus.IDLE
) {
    val originalDuration: Double get() = Math.max(0.0, originalEnd - originalStart)
    val currentDuration: Double get() = Math.max(0.0, currentEnd - currentStart)
}

@JsonClass(generateAdapter = true)
data class TimelineMap(
    val projectId: String,
    val originalDuration: Double,
    val currentDuration: Double,
    val segments: List<TimelineSegment> = emptyList(),
    val removedRanges: List<TimeRange> = emptyList()
) {
    /**
     * Map a timestamp in original video to the timestamp in current edited video.
     * Returns null if the original timestamp falls in a removed segment.
     */
    fun mapOriginalToCurrent(originalTime: Double): Double? {
        for (seg in segments.filter { it.isRetained }) {
            if (originalTime in seg.originalStart..seg.originalEnd) {
                val offset = originalTime - seg.originalStart
                return (seg.currentStart + offset).coerceIn(0.0, currentDuration)
            }
        }
        return null
    }

    /**
     * Map a timestamp in current edited video back to original source video.
     */
    fun mapCurrentToOriginal(currentTime: Double): Double {
        for (seg in segments.filter { it.isRetained }) {
            if (currentTime in seg.currentStart..seg.currentEnd) {
                val offset = currentTime - seg.currentStart
                return (seg.originalStart + offset).coerceIn(0.0, originalDuration)
            }
        }
        return currentTime.coerceIn(0.0, originalDuration)
    }

    companion object {
        fun identity(projectId: String, duration: Double): TimelineMap {
            val fullSegment = TimelineSegment(
                id = "seg_init_0",
                projectId = projectId,
                originalStart = 0.0,
                originalEnd = duration,
                currentStart = 0.0,
                currentEnd = duration,
                isRetained = true,
                stageApplied = PipelineStatus.IDLE
            )
            return TimelineMap(
                projectId = projectId,
                originalDuration = duration,
                currentDuration = duration,
                segments = listOf(fullSegment),
                removedRanges = emptyList()
            )
        }
    }
}
