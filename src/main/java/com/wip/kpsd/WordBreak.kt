package com.wip.kpsd

/**
 * Defines a strategy for handling words that exceed the available horizontal space.
 */
enum class WordBreak {
    /** Do not break words; they will overflow the boundary if they are too long. */
    NONE,
    
    /** Break words using a hyphen when they exceed the boundary width. */
    HYPHENATE,
    
    /** Break words at any character when they exceed the boundary width. */
    BREAK_WORD
}
