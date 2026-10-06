package com.wip.kpsd

import java.awt.geom.Point2D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
 * Represents a horizontal range [leftOffset, rightOffset] relative to the shape center.
 * By convention, [leftOffset] is negative (or 0) indicating distance to the left of the center,
 * and [rightOffset] is positive (or 0) indicating distance to the right of the center.
 */
data class HorizontalSpan(val leftOffset: Float, val rightOffset: Float) {
    val width: Float
        get() {
            val w = rightOffset - leftOffset
            return if (w <= 0f) 0f else w
        }

    val symmetricWidth: Float
        get() {
            val sym = 2f * minOf(-leftOffset, rightOffset)
            return if (sym <= 0f) 0f else sym
        }
}

/**
 * Contract for a shape that constrains text.
 */
interface TextBoundary {
    /**
     * Visual center or anchor point of the shape in canvas coordinate space, if known.
     */
    val visualCenter: Point2D?
        get() = naturalBounds?.let {
            Point2D.Double(((it.left + it.right) / 2f).toDouble(), ((it.top + it.bottom) / 2f).toDouble())
        }

    /**
     * Alias for [visualCenter].
     */
    val anchorCenter: Point2D? get() = visualCenter

    /**
     * Natural encompassing bounding box for the shape, if known.
     */
    val naturalBounds: PsdBounds? get() = null

    /**
     * Determines the available horizontal span relative to the shape's center at a specific Y level.
     *
     * @param y The vertical offset relative to the center of the shape (0 is center).
     * @param bounds The total maximum bounding box encompassing the shape.
     * @return The horizontal span [leftOffset, rightOffset] relative to center.
     */
    fun getAvailableSpan(y: Float, bounds: PsdBounds): HorizontalSpan {
        val w = getAvailableWidth(y, bounds)
        return HorizontalSpan(-w / 2f, w / 2f)
    }

    /**
     * Determines the maximum allowed symmetric text width at a specific Y level within the shape.
     *
     * @param y The current vertical offset relative to the center of the shape (0 is center).
     * @param bounds The total maximum bounding box encompassing the shape.
     * @return The maximum allowed symmetric text width at this specific Y level.
     */
    fun getAvailableWidth(y: Float, bounds: PsdBounds): Float {
        val w = getAvailableSpan(y, bounds).symmetricWidth
        return if (w <= 0f) 0f else w
    }
}

/**
 * Standard rectangular text boundary.
 */
class RectangleBoundary(
    val padding: Float = 0f,
    override val naturalBounds: PsdBounds? = null
) : TextBoundary {
    override fun getAvailableSpan(y: Float, bounds: PsdBounds): HorizontalSpan {
        val usableHeight = bounds.height - (padding * 2f)
        val usableWidth = bounds.width - (padding * 2f)

        val dy = abs(y)
        if (dy >= usableHeight / 2f || usableWidth <= 0f) return HorizontalSpan(0f, 0f)
        val halfW = usableWidth / 2f
        return HorizontalSpan(-halfW, halfW)
    }

    override fun getAvailableWidth(y: Float, bounds: PsdBounds): Float {
        return getAvailableSpan(y, bounds).symmetricWidth
    }
}

/**
 * Standard elliptical text boundary.
 */
class EllipseBoundary(
    val padding: Float = 0f,
    override val naturalBounds: PsdBounds? = null
) : TextBoundary {
    override fun getAvailableSpan(y: Float, bounds: PsdBounds): HorizontalSpan {
        val usableHeight = bounds.height - (padding * 2f)
        val usableWidth = bounds.width - (padding * 2f)

        val dy = abs(y)
        if (dy >= usableHeight / 2f || usableHeight <= 0f || usableWidth <= 0f) return HorizontalSpan(0f, 0f)

        // Ellipse equation: x^2 / a^2 + y^2 / b^2 = 1
        // x = a * sqrt(1 - y^2 / b^2)
        val b = usableHeight / 2f
        val a = usableWidth / 2f
        val x = (a * sqrt(1.0 - (dy / b).toDouble().pow(2.0))).toFloat()
        return HorizontalSpan(-x, x)
    }

    override fun getAvailableWidth(y: Float, bounds: PsdBounds): Float {
        return getAvailableSpan(y, bounds).symmetricWidth
    }
}

/**
 * Convenience factory functions for constructing [Point2D.Double].
 */
fun Point2D(x: Double, y: Double): Point2D = Point2D.Double(x, y)
fun Point2D(x: Float, y: Float): Point2D = Point2D.Double(x.toDouble(), y.toDouble())
fun Point2D(x: Int, y: Int): Point2D = Point2D.Double(x.toDouble(), y.toDouble())

/**
 * Resolved geometric layout parameters for positioning and rotating a text layer around a shape's visual center.
 */
data class EffectiveTextLayout(
    val boundaryShape: TextBoundary,
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val boxWidth: Int,
    val boxHeight: Int,
    val tx: Double,
    val ty: Double
) {
    companion object {
        /**
         * Computes optimal bounding box coordinates, centered dimensions, and rotation transform matrix
         * for a given [boundary] shape.
         */
        fun compute(
            boundary: TextBoundary,
            rotation: Double = 0.0,
            paddingPercentage: Float = 0f,
            bounds: PsdBounds? = null
        ): EffectiveTextLayout {
            val vCenter = boundary.visualCenter
                ?: boundary.anchorCenter
                ?: bounds?.let { Point2D.Double(((it.left + it.right) / 2.0), ((it.top + it.bottom) / 2.0)) }
                ?: boundary.naturalBounds?.let { Point2D.Double(((it.left + it.right) / 2.0), ((it.top + it.bottom) / 2.0)) }
                ?: throw IllegalArgumentException("Cannot resolve visual center: boundary has no visualCenter/naturalBounds and no bounds were provided.")

            val vcx = vCenter.x
            val vcy = vCenter.y

            val pBounds = bounds ?: boundary.naturalBounds
            val (halfW, halfH) = if (pBounds != null) {
                val distLeft = max(1.0, vcx - pBounds.left)
                val distRight = max(1.0, pBounds.right - vcx)
                val distTop = max(1.0, vcy - pBounds.top)
                val distBottom = max(1.0, pBounds.bottom - vcy)
                Pair(min(distLeft, distRight), min(distTop, distBottom))
            } else {
                throw IllegalArgumentException("Cannot resolve layout extents: boundary has no naturalBounds and no bounds were provided.")
            }

            val effBoxWidth = max(1, (halfW * 2.0).toInt())
            val effBoxHeight = max(1, (halfH * 2.0).toInt())

            val effLeft = (vcx - halfW).toInt()
            val effTop = (vcy - halfH).toInt()
            val effRight = effLeft + effBoxWidth
            val effBottom = effTop + effBoxHeight

            val theta = Math.toRadians(rotation)
            val cos = kotlin.math.cos(theta)
            val sin = kotlin.math.sin(theta)

            val tx = vcx - (cos * (effBoxWidth / 2.0) - sin * (effBoxHeight / 2.0))
            val ty = vcy - (sin * (effBoxWidth / 2.0) + cos * (effBoxHeight / 2.0))

            val finalBoundary = if (paddingPercentage > 0f) {
                val bPadding = min(effBoxWidth, effBoxHeight) * paddingPercentage
                when (boundary) {
                    is PolygonBoundary -> if (boundary.padding == 0f) boundary.withPadding(bPadding) else boundary
                    is RectangleBoundary -> if (boundary.padding == 0f) RectangleBoundary(bPadding, boundary.naturalBounds) else boundary
                    is EllipseBoundary -> if (boundary.padding == 0f) EllipseBoundary(bPadding, boundary.naturalBounds) else boundary
                    else -> boundary
                }
            } else {
                boundary
            }

            return EffectiveTextLayout(
                boundaryShape = finalBoundary,
                top = effTop,
                left = effLeft,
                bottom = effBottom,
                right = effRight,
                boxWidth = effBoxWidth,
                boxHeight = effBoxHeight,
                tx = tx,
                ty = ty
            )
        }
    }
}
