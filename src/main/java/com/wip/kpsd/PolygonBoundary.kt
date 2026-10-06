package com.wip.kpsd

import java.awt.geom.Point2D
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Custom [TextBoundary] implementation that dynamically constrains text line spans and widths
 * to the interior geometry of an arbitrary polygon (e.g. comic or manga speech bubble contours).
 *
 * @param polygon The list of vertices defining the closed polygon boundary.
 * @param padding Inset distance in pixels applied from the polygon edges.
 * @param visualCenter Optional center point. If null, automatically computed via centroid or polylabel.
 * @param usePolylabel Whether to use pole-of-inaccessibility (polylabel) instead of centroid when [visualCenter] is null.
 */
class PolygonBoundary(
    val polygon: List<Point2D>,
    val padding: Float = 0f,
    visualCenter: Point2D? = null,
    val usePolylabel: Boolean = false
) : TextBoundary {

    constructor(
        polygon: List<Point2D>,
        visualCenter: Point2D?,
        padding: Float = 0f
    ) : this(polygon, padding, visualCenter, false)

    override val naturalBounds: PsdBounds? by lazy {
        if (polygon.isEmpty()) {
            null
        } else {
            var minX = polygon[0].x
            var maxX = polygon[0].x
            var minY = polygon[0].y
            var maxY = polygon[0].y
            for (p in polygon) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            PsdBounds(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
        }
    }

    override val visualCenter: Point2D? = visualCenter
        ?: if (polygon.size >= 3) {
            if (usePolylabel) computePoleOfInaccessibility(polygon) else computeCentroid(polygon)
        } else {
            computeCentroid(polygon)
        }

    override val anchorCenter: Point2D? get() = this.visualCenter

    /**
     * Creates a copy of this boundary with updated [padding].
     */
    fun withPadding(padding: Float): PolygonBoundary {
        return PolygonBoundary(
            polygon = polygon,
            padding = padding,
            visualCenter = this.visualCenter,
            usePolylabel = usePolylabel
        )
    }

    /**
     * Calculates the horizontal span [leftOffset, rightOffset] relative to [visualCenter]
     * at a vertical offset [y] (where 0 is [visualCenter].y).
     */
    override fun getAvailableSpan(y: Float, bounds: PsdBounds): HorizontalSpan {
        if (polygon.size < 3) {
            val usableHeight = bounds.height - (padding * 2f)
            val usableWidth = bounds.width - (padding * 2f)
            val dy = abs(y)
            if (dy >= usableHeight / 2f || usableWidth <= 0f) return HorizontalSpan(0f, 0f)
            val halfW = usableWidth / 2f
            return HorizontalSpan(-halfW, halfW)
        }

        val centerY = visualCenter?.y ?: ((bounds.top + bounds.bottom) / 2.0)
        val centerX = visualCenter?.x ?: ((bounds.left + bounds.right) / 2.0)
        val scanlineY = centerY + y

        // Find all X coordinates where the horizontal scanline intersects polygon segments
        val intersections = mutableListOf<Double>()
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val p1 = polygon[i]
            val p2 = polygon[j]

            val y1 = p1.y
            val y2 = p2.y

            if ((y1 <= scanlineY && y2 > scanlineY) || (y2 <= scanlineY && y1 > scanlineY)) {
                val dy = y2 - y1
                if (dy != 0.0) {
                    val x = p1.x + (scanlineY - y1) * (p2.x - p1.x) / dy
                    intersections.add(x)
                }
            }
            j = i
        }

        if (intersections.size < 2) {
            return HorizontalSpan(0f, 0f)
        }

        intersections.sort()

        // For speech balloons, find the interior span containing centerX
        var spanLeft = 0.0
        var spanRight = 0.0
        var foundSpan = false
        for (k in 0 until intersections.size - 1 step 2) {
            val l = intersections[k]
            val r = intersections[k + 1]
            if (centerX in l..r) {
                spanLeft = l
                spanRight = r
                foundSpan = true
                break
            }
        }
        if (!foundSpan) {
            return HorizontalSpan(0f, 0f)
        }

        var leftOffset = (spanLeft - centerX).toFloat() + padding
        var rightOffset = (spanRight - centerX).toFloat() - padding

        if (bounds.width > 0f) {
            val maxHalf = max(0f, (bounds.width - 2f * padding) / 2f)
            leftOffset = max(leftOffset, -maxHalf)
            rightOffset = min(rightOffset, maxHalf)
        }

        if (leftOffset >= rightOffset) {
            return HorizontalSpan(0f, 0f)
        }

        return HorizontalSpan(leftOffset, rightOffset)
    }

    override fun getAvailableWidth(y: Float, bounds: PsdBounds): Float {
        return getAvailableSpan(y, bounds).symmetricWidth
    }

    companion object {
        /**
         * Creates a [PolygonBoundary] from coordinate pairs (x, y).
         */
        fun fromPoints(
            points: List<Pair<Number, Number>>,
            padding: Float = 0f,
            visualCenter: Point2D? = null,
            usePolylabel: Boolean = false
        ): PolygonBoundary {
            return PolygonBoundary(
                polygon = points.map { Point2D.Double(it.first.toDouble(), it.second.toDouble()) },
                padding = padding,
                visualCenter = visualCenter,
                usePolylabel = usePolylabel
            )
        }

        /**
         * Computes the geometric centroid of a polygon.
         */
        fun computeCentroid(polygon: List<Point2D>): Point2D? {
            if (polygon.isEmpty()) return null
            if (polygon.size == 1) return Point2D.Double(polygon[0].x, polygon[0].y)
            if (polygon.size == 2) {
                return Point2D.Double(
                    (polygon[0].x + polygon[1].x) / 2.0,
                    (polygon[0].y + polygon[1].y) / 2.0
                )
            }

            var sumX = 0.0
            var sumY = 0.0
            var signedArea = 0.0

            for (i in polygon.indices) {
                val p0 = polygon[i]
                val p1 = polygon[(i + 1) % polygon.size]
                val a = p0.x * p1.y - p1.x * p0.y
                signedArea += a
                sumX += (p0.x + p1.x) * a
                sumY += (p0.y + p1.y) * a
            }

            signedArea *= 0.5
            if (abs(signedArea) > 1e-6) {
                val cx = sumX / (6.0 * signedArea)
                val cy = sumY / (6.0 * signedArea)
                return Point2D.Double(cx, cy)
            }

            val meanX = polygon.sumOf { it.x } / polygon.size
            val meanY = polygon.sumOf { it.y } / polygon.size
            return Point2D.Double(meanX, meanY)
        }

        /**
         * Computes the pole of inaccessibility (polylabel) of a polygon: the interior point
         * furthest from any contour edge. Ideal for irregular speech balloons with tails.
         *
         * @param polygon Vertices of the polygon.
         * @param precision Precision threshold in pixels.
         */
        fun computePoleOfInaccessibility(polygon: List<Point2D>, precision: Double = 1.0): Point2D {
            if (polygon.isEmpty()) return Point2D.Double(0.0, 0.0)
            if (polygon.size == 1) return Point2D.Double(polygon[0].x, polygon[0].y)
            if (polygon.size == 2) {
                return Point2D.Double(
                    (polygon[0].x + polygon[1].x) / 2.0,
                    (polygon[0].y + polygon[1].y) / 2.0
                )
            }

            var minX = polygon[0].x
            var maxX = polygon[0].x
            var minY = polygon[0].y
            var maxY = polygon[0].y
            for (p in polygon) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }

            val width = maxX - minX
            val height = maxY - minY
            val cellSize = min(width, height)
            if (cellSize <= 0.0) {
                return Point2D.Double(minX, minY)
            }

            val h = cellSize / 2.0
            val cellQueue = PriorityQueue<Cell>()

            val centroid = computeCentroid(polygon) ?: Point2D.Double(minX + width / 2.0, minY + height / 2.0)
            var bestCell = Cell(centroid.x, centroid.y, 0.0, polygon)

            val bboxCell = Cell(minX + width / 2.0, minY + height / 2.0, 0.0, polygon)
            if (bboxCell.d > bestCell.d) {
                bestCell = bboxCell
            }

            var x = minX
            while (x < maxX) {
                var y = minY
                while (y < maxY) {
                    cellQueue.add(Cell(x + h, y + h, h, polygon))
                    y += cellSize
                }
                x += cellSize
            }

            var iterations = 0
            val maxIterations = 5000
            while (!cellQueue.isEmpty() && iterations++ < maxIterations) {
                val cell = cellQueue.poll() ?: break
                if (cell.d > bestCell.d) {
                    bestCell = cell
                }
                if (cell.max - bestCell.d <= precision) continue

                val nextH = cell.h / 2.0
                cellQueue.add(Cell(cell.x - nextH, cell.y - nextH, nextH, polygon))
                cellQueue.add(Cell(cell.x + nextH, cell.y - nextH, nextH, polygon))
                cellQueue.add(Cell(cell.x - nextH, cell.y + nextH, nextH, polygon))
                cellQueue.add(Cell(cell.x + nextH, cell.y + nextH, nextH, polygon))
            }

            return Point2D.Double(bestCell.x, bestCell.y)
        }

        private class Cell(
            val x: Double,
            val y: Double,
            val h: Double,
            polygon: List<Point2D>
        ) : Comparable<Cell> {
            val d: Double = pointToPolygonDist(x, y, polygon)
            val max: Double = d + h * sqrt(2.0)

            override fun compareTo(other: Cell): Int {
                return other.max.compareTo(this.max)
            }
        }

        private fun pointToPolygonDist(px: Double, py: Double, polygon: List<Point2D>): Double {
            var inside = false
            var minDistSq = Double.POSITIVE_INFINITY
            var j = polygon.size - 1
            for (i in polygon.indices) {
                val a = polygon[i]
                val b = polygon[j]
                val ax = a.x
                val ay = a.y
                val bx = b.x
                val by = b.y

                if ((ay > py != by > py) && (px < (bx - ax) * (py - ay) / (by - ay) + ax)) {
                    inside = !inside
                }
                minDistSq = min(minDistSq, getSqSegDist(px, py, ax, ay, bx, by))
                j = i
            }
            val dist = sqrt(minDistSq)
            return if (inside) dist else -dist
        }

        private fun getSqSegDist(
            px: Double,
            py: Double,
            x1: Double,
            y1: Double,
            x2: Double,
            y2: Double
        ): Double {
            var x = x1
            var y = y1
            var dx = x2 - x
            var dy = y2 - y

            if (dx != 0.0 || dy != 0.0) {
                val t = ((px - x) * dx + (py - y) * dy) / (dx * dx + dy * dy)
                if (t > 1.0) {
                    x = x2
                    y = y2
                } else if (t > 0.0) {
                    x += dx * t
                    y += dy * t
                }
            }
            dx = px - x
            dy = py - y
            return dx * dx + dy * dy
        }
    }
}
