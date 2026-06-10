package com.wip.kpsd

/**
 * Calculates the exact pixel dimensions of this layer, accounting for Photoshop effects
 * like stroke and drop shadow.
 *
 * @return the precise bounding box of the layer content and effects.
 */
fun Layer.calculateBounds(): PsdBounds {
    return TextFormatter.calculateLayerBounds(this)
}
