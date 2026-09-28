package com.vineyard.aivideostudio.media.timeline

import com.vineyard.aivideostudio.ai.model.TrimSegment
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.TimeRange
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.TimelineSegment
import java.util.UUID

object TimelineMapper {

    /**
     * Updates an existing timeline map by cutting out the specified trim segments.
     */
    fun applyTrim(
        currentMap: TimelineMap,
        segmentsToRemove: List<TrimSegment>,
        stage: PipelineStatus = PipelineStatus.TRIM_EXECUTION
    ): TimelineMap {
        if (segmentsToRemove.isEmpty()) return currentMap

        val sortedCuts = segmentsToRemove.sortedBy { it.start }
        val newRemovedRanges = currentMap.removedRanges.toMutableList()
        val newRetainedSegments = mutableListOf<TimelineSegment>()

        var currentCursor = 0.0
        var segmentCounter = 0

        // For each currently retained segment, calculate what portions remain after applying cuts
        for (seg in currentMap.segments.filter { it.isRetained }) {
            var segStartInCurrent = seg.currentStart
            val segEndInCurrent = seg.currentEnd

            // Find all cuts that overlap with this segment in current timeline
            val overlappingCuts = sortedCuts.filter { cut ->
                cut.end > segStartInCurrent && cut.start < segEndInCurrent
            }

            var subCursor = segStartInCurrent
            for (cut in overlappingCuts) {
                val cutStartClamped = cut.start.coerceIn(segStartInCurrent, segEndInCurrent)
                val cutEndClamped = cut.end.coerceIn(segStartInCurrent, segEndInCurrent)

                if (cutStartClamped > subCursor) {
                    val pieceDuration = cutStartClamped - subCursor
                    val origStart = seg.originalStart + (subCursor - segStartInCurrent)
                    val origEnd = origStart + pieceDuration

                    newRetainedSegments.add(
                        TimelineSegment(
                            id = "seg_${stage.name.lowercase()}_${segmentCounter++}",
                            projectId = currentMap.projectId,
                            originalStart = origStart,
                            originalEnd = origEnd,
                            currentStart = currentCursor,
                            currentEnd = currentCursor + pieceDuration,
                            isRetained = true,
                            stageApplied = stage
                        )
                    )
                    currentCursor += pieceDuration
                }

                newRemovedRanges.add(TimeRange(cutStartClamped, cutEndClamped))
                subCursor = cutEndClamped
            }

            if (subCursor < segEndInCurrent) {
                val pieceDuration = segEndInCurrent - subCursor
                val origStart = seg.originalStart + (subCursor - segStartInCurrent)
                val origEnd = origStart + pieceDuration

                newRetainedSegments.add(
                    TimelineSegment(
                        id = "seg_${stage.name.lowercase()}_${segmentCounter++}",
                        projectId = currentMap.projectId,
                        originalStart = origStart,
                        originalEnd = origEnd,
                        currentStart = currentCursor,
                        currentEnd = currentCursor + pieceDuration,
                        isRetained = true,
                        stageApplied = stage
                    )
                )
                currentCursor += pieceDuration
            }
        }

        return TimelineMap(
            projectId = currentMap.projectId,
            originalDuration = currentMap.originalDuration,
            currentDuration = currentCursor,
            segments = newRetainedSegments,
            removedRanges = newRemovedRanges
        )
    }
}
