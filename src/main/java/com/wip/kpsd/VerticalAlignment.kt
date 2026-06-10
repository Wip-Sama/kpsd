package com.wip.kpsd

/**
 * Vertical alignment strategy for text inside a bounding shape.
 */
enum class VerticalAlignment {
    /** Align text to the top of the shape bounds. */
    TOP,
    
    /** Center the text vertically inside the shape. */
    CENTER,
    
    /** Align the text to the bottom of the shape bounds. */
    BOTTOM,
    
    /** 
     * Optically center the text. The visual mass of the text (ignoring descenders)
     * is placed perfectly in the center of the shape bounds.
     */
    CENTER_OPTICAL
}
