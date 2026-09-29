package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass
import com.vineyard.aivideostudio.core.model.effects.SpeedRampSpec

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
    val stageApplied: PipelineStatus = PipelineStatus.IDLE,
    val speedMultiplier: Double = 1.0 // 1.0 = normal, 2.0 = 2x speed (half duration)
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
     * Takes into account cuts, trims, and speed multipliers (e.g., fast-forward 2x/4x).
     * Returns null if the original timestamp falls in a removed segment.
     */
    fun mapOriginalToCurrent(originalTime: Double): Double? {
        for (seg in segments.filter { it.isRetained }) {
            if (originalTime in seg.originalStart..seg.originalEnd) {
                val origOffset = originalTime - seg.originalStart
                val scaledOffset = if (seg.speedMultiplier > 0.0) origOffset / seg.speedMultiplier else origOffset
                return (seg.currentStart + scaledOffset).coerceIn(0.0, currentDuration)
            }
        }
        return null
    }

    /**
     * Map a timestamp in current edited video back to original source video,
     * taking into account speed multipliers and trims.
     */
    fun mapCurrentToOriginal(currentTime: Double): Double {
        for (seg in segments.filter { it.isRetained }) {
            if (currentTime in seg.currentStart..seg.currentEnd) {
                val currOffset = currentTime - seg.currentStart
                val scaledOffset = currOffset * seg.speedMultiplier
                return (seg.originalStart + scaledOffset).coerceIn(0.0, originalDuration)
            }
        }
        return currentTime.coerceIn(0.0, originalDuration)
    }

    /**
     * Reshapes the timeline with speed-ramping specifications (e.g. 2x fast-forward sections).
     */
    fun withSpeedRamps(speedRamps: List<SpeedRampSpec>): TimelineMap {
        if (speedRamps.isEmpty()) return this

        val updatedSegments = mutableListOf<TimelineSegment>()
        var runningCurrentTime = 0.0

        for (seg in segments.filter { it.isRetained }) {
            // Find applicable speed ramp for this segment range
            val ramp = speedRamps.firstOrNull { r ->
                val rampStartSec = r.startTimeMs / 1000.0
                val rampEndSec = r.endTimeMs / 1000.0
                seg.originalStart >= rampStartSec - 0.05 && seg.originalEnd <= rampEndSec + 0.05
            }

            val multiplier = ramp?.speedMultiplier?.toDouble() ?: seg.speedMultiplier
            val effectiveDuration = seg.originalDuration / multiplier
            val segCurrentStart = runningCurrentTime
            val segCurrentEnd = runningCurrentTime + effectiveDuration

            updatedSegments.add(
                seg.copy(
                    currentStart = segCurrentStart,
                    currentEnd = segCurrentEnd,
                    speedMultiplier = multiplier
                )
            )

            runningCurrentTime = segCurrentEnd
        }

        return copy(
            currentDuration = runningCurrentTime,
            segments = updatedSegments
        )
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
                stageApplied = PipelineStatus.IDLE,
                speedMultiplier = 1.0
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