package com.wip.kpsd

/**
 * Configuration for auto-fitting text to a boundary.
 * The text engine will attempt to find the optimal font size within the specified range.
 *
 * @param minSize The minimum allowed font size.
 * @param maxSize The maximum allowed font size.
 */
data class AutoFit(
    val minSize: Float,
    val maxSize: Float
)
