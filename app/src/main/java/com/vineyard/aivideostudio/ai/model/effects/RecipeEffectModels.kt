package com.vineyard.aivideostudio.core.model.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain and JSON data models representing granular editing tools
 * configured exclusively via the Master Recipe JSON script.
 */

@Serializable
enum class BlurShape {
    @SerialName("rectangle") RECTANGLE,
    @SerialName("circle") CIRCLE,
    @SerialName("full_frame") FULL_FRAME
}

@Serializable
enum class BlurType {
    @SerialName("gaussian") GAUSSIAN,
    @SerialName("mosaic") MOSAIC,
    @SerialName("privacy_box") PRIVACY_BOX
}

@Serializable
data class NormalizedBounds(
    @SerialName("left") val left: Float,
    @SerialName("top") val top: Float,
    @SerialName("right") val right: Float,
    @SerialName("bottom") val bottom: Float
) {
    init {
        require(left in 0.0f..1.0f) { "left bound must be between 0.0 and 1.0" }
        require(top in 0.0f..1.0f) { "top bound must be between 0.0 and 1.0" }
        require(right in 0.0f..1.0f) { "right bound must be between 0.0 and 1.0" }
        require(bottom in 0.0f..1.0f) { "bottom bound must be between 0.0 and 1.0" }
        require(left <= right) { "left must be <= right" }
        require(top <= bottom) { "top must be <= bottom" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = left + (width / 2.0f)
    val centerY: Float get() = top + (height / 2.0f)
}

@Serializable
data class BlurSpec(
    @SerialName("start_time_ms") val startTimeMs: Long,
    @SerialName("end_time_ms") val endTimeMs: Long,
    @SerialName("shape") val shape: BlurShape = BlurShape.RECTANGLE,
    @SerialName("type") val type: BlurType = BlurType.GAUSSIAN,
    @SerialName("bounds") val bounds: NormalizedBounds,
    @SerialName("intensity") val intensity: Float = 15.0f // 1.0 to 50.0 radius / pixel size
)

@Serializable
data class SpeedRampSpec(
    @SerialName("start_time_ms") val startTimeMs: Long,
    @SerialName("end_time_ms") val endTimeMs: Long,
    @SerialName("speed_multiplier") val speedMultiplier: Float // e.g. 0.5x, 2.0x, 4.0x
) {
    init {
        require(speedMultiplier in 0.25f..8.0f) { "Speed multiplier must be between 0.25x and 8.0x" }
        require(startTimeMs < endTimeMs) { "startTimeMs must be less than endTimeMs" }
    }
}

@Serializable
enum class OverlayType {
    @SerialName("emoji") EMOJI,
    @SerialName("brand_logo") BRAND_LOGO,
    @SerialName("solid_badge") SOLID_BADGE,
    @SerialName("sticker") STICKER
}

@Serializable
data class ReplacementOverlaySpec(
    @SerialName("id") val id: String,
    @SerialName("start_time_ms") val startTimeMs: Long,
    @SerialName("end_time_ms") val endTimeMs: Long,
    @SerialName("type") val type: OverlayType,
    @SerialName("content_value") val contentValue: String, // Emoji unicode, asset path, or base64
    @SerialName("bounds") val bounds: NormalizedBounds,
    @SerialName("rotation_degrees") val rotationDegrees: Float = 0.0f,
    @SerialName("opacity") val opacity: Float = 1.0f
)

@Serializable
enum class ColorPreset {
    @SerialName("none") NONE,
    @SerialName("vintage") VINTAGE,
    @SerialName("dawn") DAWN,
    @SerialName("dusk") DUSK,
    @SerialName("halo") HALO,
    @SerialName("retro_film") RETRO_FILM,
    @SerialName("bw") BW,
    @SerialName("high_contrast") HIGH_CONTRAST,
    @SerialName("cyberpunk") CYBERPUNK,
    @SerialName("warm") WARM,
    @SerialName("cool") COOL
}

@Serializable
data class ColorGradeSpec(
    @SerialName("preset") val preset: ColorPreset = ColorPreset.NONE,
    @SerialName("brightness") val brightness: Float = 0.0f,    // -1.0 to 1.0 (0.0 = neutral)
    @SerialName("contrast") val contrast: Float = 0.0f,        // -1.0 to 1.0 (0.0 = neutral)
    @SerialName("saturation") val saturation: Float = 1.0f,    // 0.0 (B&W) to 2.0 (vibrant)
    @SerialName("sharpness") val sharpness: Float = 0.0f,      // 0.0 to 1.0
    @SerialName("hue") val hue: Float = 0.0f                  // -180.0 to 180.0 degrees
)

@Serializable
enum class TrackingStyle {
    @SerialName("red_box") RED_BOX,
    @SerialName("highlight_circle") HIGHLIGHT_CIRCLE,
    @SerialName("flashing_arrow") FLASHING_ARROW,
    @SerialName("spotlight") SPOTLIGHT
}

@Serializable
data class TrackingKeyframe(
    @SerialName("time_ms") val timeMs: Long,
    @SerialName("x") val x: Float, // Normalized 0.0 - 1.0
    @SerialName("y") val y: Float, // Normalized 0.0 - 1.0
    @SerialName("width") val width: Float = 0.15f,
    @SerialName("height") val height: Float = 0.15f
)

@Serializable
data class TrackingIndicatorSpec(
    @SerialName("id") val id: String,
    @SerialName("style") val style: TrackingStyle = TrackingStyle.RED_BOX,
    @SerialName("color_hex") val colorHex: String = "#FF0000",
    @SerialName("stroke_width_px") val strokeWidthPx: Float = 6.0f,
    @SerialName("label") val label: String? = null,
    @SerialName("keyframes") val keyframes: List<TrackingKeyframe> = emptyList()
)