package com.wip.kpsd

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for domain enums, helper conversion methods, and model types.
 */
class EnumsAndTypesTest {

    @Test
    fun testAntiAliasFromString() {
        assertEquals(AntiAlias.NONE, AntiAlias.fromString("none"))
        assertEquals(AntiAlias.SHARP, AntiAlias.fromString("sharp"))
        assertEquals(AntiAlias.CRISP, AntiAlias.fromString("crisp"))
        assertEquals(AntiAlias.STRONG, AntiAlias.fromString("strong"))
        assertEquals(AntiAlias.SMOOTH, AntiAlias.fromString("smooth"))
        assertEquals(AntiAlias.SHARP, AntiAlias.fromString("unknown_val"))
        assertEquals(AntiAlias.SHARP, AntiAlias.fromString(null))
    }

    @Test
    fun testBlendModeFromString() {
        assertEquals(BlendMode.PASS_THROUGH, BlendMode.fromString("pass through"))
        assertEquals(BlendMode.NORMAL, BlendMode.fromString("normal"))
        assertEquals(BlendMode.MULTIPLY, BlendMode.fromString("multiply"))
        assertEquals(BlendMode.SCREEN, BlendMode.fromString("screen"))
        assertEquals(BlendMode.OVERLAY, BlendMode.fromString("overlay"))
        assertEquals(BlendMode.NORMAL, BlendMode.fromString("unknown_mode"))
        assertEquals(BlendMode.NORMAL, BlendMode.fromString(null))
    }

    @Test
    fun testStrokePositionFromString() {
        assertEquals(StrokePosition.INSIDE, StrokePosition.fromString("inside"))
        assertEquals(StrokePosition.CENTER, StrokePosition.fromString("center"))
        assertEquals(StrokePosition.OUTSIDE, StrokePosition.fromString("outside"))
        assertEquals(StrokePosition.OUTSIDE, StrokePosition.fromString("unknown_position"))
        assertEquals(StrokePosition.OUTSIDE, StrokePosition.fromString(null))
    }

    @Test
    fun testVerticalAlignmentEntries() {
        val values = VerticalAlignment.entries
        assertEquals(4, values.size)
        assertNotNull(VerticalAlignment.valueOf("TOP"))
        assertNotNull(VerticalAlignment.valueOf("CENTER"))
        assertNotNull(VerticalAlignment.valueOf("BOTTOM"))
        assertNotNull(VerticalAlignment.valueOf("CENTER_OPTICAL"))
    }

    @Test
    fun testWordBreakEntries() {
        val values = WordBreak.entries
        assertEquals(3, values.size)
        assertNotNull(WordBreak.valueOf("NONE"))
        assertNotNull(WordBreak.valueOf("HYPHENATE"))
        assertNotNull(WordBreak.valueOf("BREAK_WORD"))
    }

    @Test
    fun testAutoFitProperties() {
        val autoFit = AutoFit(minSize = 10.0f, maxSize = 36.0f)
        assertEquals(10.0f, autoFit.minSize)
        assertEquals(36.0f, autoFit.maxSize)

        val modified = autoFit.copy(minSize = 12.0f)
        assertEquals(12.0f, modified.minSize)
        assertEquals(36.0f, modified.maxSize)
    }
}
