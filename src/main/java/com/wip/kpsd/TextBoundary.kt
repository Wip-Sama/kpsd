package com.wip.kpsd

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Represents the rectangular dimensions (width, height) or exact box bounds (left, top, right, bottom).
 */
data class PsdBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        fun fromDimensions(width: Float, height: Float): PsdBounds {
            return PsdBounds(0f, 0f, width, height)
        }
    }
}

/**
 * Contract for a shape that constraints text.
 */
interface TextBoundary {
    /**
     * Determines the maximum allowed text width at a specific Y level within the shape.
     *
     * @param y The current vertical offset relative to the center of the shape (0 is center).
     * @param bounds The total maximum bounding box encompassing the shape.
     * @return The maximum allowed text width at this specific Y level.
     */
    fun getAvailableWidth(y: Float, bounds: PsdBounds): Float
}

/**
 * Standard rectangular text boundary.
 */
class RectangleBoundary(val padding: Float = 0f) : TextBoundary {
    override fun getAvailableWidth(y: Float, bounds: PsdBounds): Float {
        val usableHeight = bounds.height - (padding * 2f)
        val usableWidth = bounds.width - (padding * 2f)
        
        val dy = abs(y)
        if (dy >= usableHeight / 2f) return 0f
        return usableWidth
    }
}

/**
 * Standard elliptical text boundary.
 */
class EllipseBoundary(val padding: Float = 0f) : TextBoundary {
    override fun getAvailableWidth(y: Float, bounds: PsdBounds): Float {
        val usableHeight = bounds.height - (padding * 2f)
        val usableWidth = bounds.width - (padding * 2f)
        
        val dy = abs(y)
        if (dy >= usableHeight / 2f || usableHeight <= 0f || usableWidth <= 0f) return 0f
        
        // Ellipse equation: x^2 / a^2 + y^2 / b^2 = 1
        // x = a * sqrt(1 - y^2 / b^2)
        // Width = 2 * x
        val b = usableHeight / 2f
        val a = usableWidth / 2f
        val x = a * sqrt(1.0 - (dy / b).toDouble().pow(2.0)).toFloat()
        return x * 2f
    }
}
