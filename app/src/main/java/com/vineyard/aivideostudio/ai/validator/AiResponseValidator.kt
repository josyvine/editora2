package com.vineyard.aivideostudio.ai.validator

import com.vineyard.aivideostudio.ai.model.CaptionDecision
import com.vineyard.aivideostudio.ai.model.CommentaryDecision
import com.vineyard.aivideostudio.ai.model.CropDecision
import com.vineyard.aivideostudio.ai.model.SourceAnalysis
import com.vineyard.aivideostudio.ai.model.TrimDecision
import com.vineyard.aivideostudio.ai.model.ZoomDecision
import com.vineyard.aivideostudio.core.validation.ValidationResult

object AiResponseValidator {

    /**
     * Validates source video analysis output.
     */
    fun validateSourceAnalysis(analysis: SourceAnalysis): ValidationResult {
        if (analysis.duration <= 0.0) {
            return ValidationResult.Invalid("Source analysis duration must be positive (got ${analysis.duration})", "duration")
        }
        if (analysis.summary.isBlank()) {
            return ValidationResult.Invalid("Source analysis summary cannot be blank", "summary")
        }
        for (scene in analysis.scenes) {
            if (scene.start < 0.0 || scene.end <= scene.start || scene.end > analysis.duration + 1.0) {
                return ValidationResult.Invalid(
                    "Invalid scene timestamp bounds: [${scene.start}, ${scene.end}] for duration ${analysis.duration}",
                    "scenes"
                )
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validates trim decisions. When enforceTransformativeCut is true,
     * returning 0 cuts is rejected to guarantee derivative video editing.
     */
    fun validateTrim(
        decision: TrimDecision,
        currentDuration: Double,
        enforceTransformativeCut: Boolean = true
    ): ValidationResult {
        if (enforceTransformativeCut && (!decision.isNecessary || decision.segmentsToRemove.isEmpty())) {
            return ValidationResult.Invalid(
                "Transformative editing mandate requires at least one cut segment to tighten pacing and alter source structure.",
                "segmentsToRemove"
            )
        }

        if (!decision.isNecessary || decision.segmentsToRemove.isEmpty()) {
            return ValidationResult.Valid
        }

        val sorted = decision.segmentsToRemove.sortedBy { it.start }
        var lastEnd = 0.0

        for (segment in sorted) {
            if (segment.start < 0.0) {
                return ValidationResult.Invalid("Trim start timestamp must be >= 0.0 (got ${segment.start})", "start")
            }
            if (segment.end <= segment.start) {
                return ValidationResult.Invalid("Trim end must be greater than start (got ${segment.start} to ${segment.end})", "end")
            }
            if (segment.end > currentDuration + 0.2) {
                return ValidationResult.Invalid("Trim cut end (${segment.end}s) exceeds video duration (${currentDuration}s)", "end")
            }
            if (segment.start < lastEnd) {
                return ValidationResult.Invalid("Overlapping trim cuts detected at ${segment.start}s", "segments")
            }
            lastEnd = segment.end
        }

        // Verify that the cut plan does not delete the entire video
        val totalRemoved = sorted.sumOf { it.end - it.start }
        if (totalRemoved >= currentDuration - 0.5) {
            return ValidationResult.Invalid("Trim plan would remove almost entire video ($totalRemoved of $currentDuration s)", "segments")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates normalized crop and reframing coordinates.
     */
    fun validateCrop(decision: CropDecision): ValidationResult {
        if (!decision.isNecessary) return ValidationResult.Valid

        if (decision.x < 0f || decision.x >= 1f) {
            return ValidationResult.Invalid("Crop x coordinate must be within [0, 1) (got ${decision.x})", "x")
        }
        if (decision.y < 0f || decision.y >= 1f) {
            return ValidationResult.Invalid("Crop y coordinate must be within [0, 1) (got ${decision.y})", "y")
        }
        if (decision.width <= 0f || decision.width > 1.05f) {
            return ValidationResult.Invalid("Crop width must be within (0, 1] (got ${decision.width})", "width")
        }
        if (decision.height <= 0f || decision.height > 1.05f) {
            return ValidationResult.Invalid("Crop height must be within (0, 1] (got ${decision.height})", "height")
        }
        if (decision.x + decision.width > 1.05f) {
            return ValidationResult.Invalid("Crop right edge (x + width) exceeds frame bounds (got ${decision.x + decision.width})", "width")
        }
        if (decision.y + decision.height > 1.05f) {
            return ValidationResult.Invalid("Crop bottom edge (y + height) exceeds frame bounds (got ${decision.y + decision.height})", "height")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates punch-in zoom scale and centering.
     */
    fun validateZoom(decision: ZoomDecision, currentDuration: Double): ValidationResult {
        if (!decision.isNecessary) return ValidationResult.Valid

        if (decision.start < 0.0 || decision.start >= currentDuration) {
            return ValidationResult.Invalid("Zoom start outside timeline: ${decision.start}", "start")
        }
        if (decision.end <= decision.start || decision.end > currentDuration + 0.2) {
            return ValidationResult.Invalid("Zoom end must be > start and <= duration (got ${decision.end})", "end")
        }
        if (decision.fromScale <= 0f || decision.toScale <= 0f) {
            return ValidationResult.Invalid("Zoom scale must be positive", "scale")
        }
        if (decision.toScale < 1.0f || decision.toScale > 2.5f) {
            return ValidationResult.Invalid("Zoom toScale must be within [1.0, 2.5] (got ${decision.toScale})", "toScale")
        }
        if (decision.centerX !in 0f..1f || decision.centerY !in 0f..1f) {
            return ValidationResult.Invalid("Zoom center must be normalized within [0, 1]", "center")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates generated burned-in caption segments.
     */
    fun validateCaptions(decision: CaptionDecision, currentDuration: Double): ValidationResult {
        if (!decision.isNecessary || decision.captions.isEmpty()) return ValidationResult.Valid

        for (caption in decision.captions) {
            if (caption.text.isBlank()) {
                return ValidationResult.Invalid("Caption text cannot be blank", "text")
            }
            if (caption.start < 0.0 || caption.end <= caption.start || caption.end > currentDuration + 0.5) {
                return ValidationResult.Invalid("Invalid caption timing [${caption.start}, ${caption.end}] for duration $currentDuration", "timing")
            }
            if (caption.x !in 0f..1f || caption.y !in 0f..1f) {
                return ValidationResult.Invalid("Caption position coordinates must be normalized within [0, 1]", "position")
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validates commentary segments that will replace the purged original audio.
     */
    fun validateCommentary(
        decision: CommentaryDecision,
        currentDuration: Double,
        requireCommentary: Boolean = true
    ): ValidationResult {
        if (requireCommentary && (!decision.isNecessary || decision.commentarySegments.isEmpty())) {
            return ValidationResult.Invalid(
                "Copyright-safe production requires voiceover commentary to replace the purged original audio track.",
                "commentarySegments"
            )
        }

        if (!decision.isNecessary || decision.commentarySegments.isEmpty()) {
            return ValidationResult.Valid
        }

        for (seg in decision.commentarySegments) {
            if (seg.text.isBlank()) {
                return ValidationResult.Invalid("Commentary narration text cannot be blank", "text")
            }
            if (seg.start < 0.0 || seg.end <= seg.start || seg.end > currentDuration + 1.0) {
                return ValidationResult.Invalid("Invalid commentary timing [${seg.start}, ${seg.end}] for video duration $currentDuration", "timing")
            }
        }

        return ValidationResult.Valid
    }
}