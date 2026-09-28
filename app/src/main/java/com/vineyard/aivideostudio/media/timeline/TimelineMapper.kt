package com.vineyard.aivideostudio.media.timeline

import com.vineyard.aivideostudio.ai.model.HighlightSegment
import com.vineyard.aivideostudio.ai.model.TrimSegment
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.TimeRange
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.TimelineSegment

object TimelineMapper {

    /**
     * Updates timeline coordinates when multiple highlight segments are spliced into a montage.
     */
    fun applyHighlightSplice(
        currentMap: TimelineMap,
        segmentsToKeep: List<HighlightSegment>,
        stage: PipelineStatus = PipelineStatus.TRIM_EXECUTION
    ): TimelineMap {
        if (segmentsToKeep.isEmpty()) return currentMap

        val newRetainedSegments = mutableListOf<TimelineSegment>()
        val newRemovedRanges = mutableListOf<TimeRange>()
        var currentCursor = 0.0
        var segmentCounter = 0

        val sortedKeeps = segmentsToKeep.sortedBy { it.start }
        var lastEnd = 0.0

        for (keep in sortedKeeps) {
            if (keep.start > lastEnd) {
                newRemovedRanges.add(TimeRange(lastEnd, keep.start))
            }
            val duration = (keep.end - keep.start).coerceAtLeast(0.1)
            newRetainedSegments.add(
                TimelineSegment(
                    id = "seg_${stage.name.lowercase()}_${segmentCounter++}",
                    projectId = currentMap.projectId,
                    originalStart = keep.start,
                    originalEnd = keep.end,
                    currentStart = currentCursor,
                    currentEnd = currentCursor + duration,
                    isRetained = true,
                    stageApplied = stage
                )
            )
            currentCursor += duration
            lastEnd = keep.end
        }

        if (lastEnd < currentMap.originalDuration) {
            newRemovedRanges.add(TimeRange(lastEnd, currentMap.originalDuration))
        }

        return TimelineMap(
            projectId = currentMap.projectId,
            originalDuration = currentMap.originalDuration,
            currentDuration = currentCursor,
            segments = newRetainedSegments,
            removedRanges = newRemovedRanges
        )
    }

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

        for (seg in currentMap.segments.filter { it.isRetained }) {
            val segStartInCurrent = seg.currentStart
            val segEndInCurrent = seg.currentEnd

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

    /**
     * Translates a timestamp from the original raw video timeline to the current edited montage timeline.
     */
    fun mapOriginalToCurrent(timelineMap: TimelineMap, originalSec: Double): Double? {
        val matchingSegment = timelineMap.segments.firstOrNull {
            it.isRetained && originalSec >= it.originalStart && originalSec <= it.originalEnd
        } ?: return null

        val offset = originalSec - matchingSegment.originalStart
        return matchingSegment.currentStart + offset
    }

    /**
     * Translates a timestamp from the current edited montage timeline back to the original raw video timeline.
     */
    fun mapCurrentToOriginal(timelineMap: TimelineMap, currentSec: Double): Double? {
        val matchingSegment = timelineMap.segments.firstOrNull {
            it.isRetained && currentSec >= it.currentStart && currentSec <= it.currentEnd
        } ?: return null

        val offset = currentSec - matchingSegment.currentStart
        return matchingSegment.originalStart + offset
    }
}