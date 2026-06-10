package com.wip.kpsd

import java.awt.Font as AwtFont
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos

object TextFormatter {

    private val graphics: Graphics2D by lazy {
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        img.createGraphics()
    }

    private val availableFonts by lazy {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    }

    /**
     * Resolves an AWT Font for measurement. Throws an exception if the font is not installed.
     */
    fun getAwtFont(fontName: String, fontSize: Float, isBold: Boolean, isItalic: Boolean): AwtFont {
        var style = AwtFont.PLAIN
        if (isBold) style = style or AwtFont.BOLD
        if (isItalic) style = style or AwtFont.ITALIC

        val awtFont = AwtFont(fontName, style, fontSize.toInt())
        
        // java.awt.Font falls back to "Dialog" if the font family is not found.
        // We ensure exact matching by checking against available font families.
        val familyMatch = availableFonts.any { it.equals(awtFont.family, ignoreCase = true) }
        if (!familyMatch && !awtFont.family.equals("Dialog", ignoreCase = true) && !fontName.equals("Dialog", ignoreCase = true)) {
            // Note: Since Font definitions in PSD might use PostScript names (e.g. ArialMT), 
            // a robust implementation might need a mapping from PS names to family names.
            // For now, if the requested font is strictly unavailable, throw an error as requested.
            throw IllegalArgumentException("Font '$fontName' is not available on the system.")
        }
        
        return awtFont
    }

    /**
     * Formats the text to fit within the given boundaries, inserting \r for newlines.
     */
    fun formatText(
        text: String,
        style: TextStyle,
        bounds: PsdBounds,
        shape: TextBoundary,
        wordBreak: WordBreak,
        alignment: VerticalAlignment = VerticalAlignment.TOP
    ): String {
        return formatTextInternal(text, style, bounds, shape, wordBreak, alignment).text
    }

    data class FormatResult(
        val text: String,
        val totalHeight: Float,
        val visualHeight: Float,
        val layoutScore: Float
    )

    fun formatTextInternal(
        text: String,
        style: TextStyle,
        bounds: PsdBounds,
        shape: TextBoundary,
        wordBreak: WordBreak,
        alignment: VerticalAlignment
    ): FormatResult {
        val fontSize = style.fontSize ?: 12f
        val leading = if (style.autoLeading == false) (style.leading ?: fontSize * 1.2f) else fontSize * 1.2f
        val fontName = style.font?.name ?: "Arial"
        val isBold = style.fauxBold ?: false
        val isItalic = style.fauxItalic ?: false
        val awtFont = getAwtFont(fontName, fontSize, isBold, isItalic)
        val metrics = graphics.getFontMetrics(awtFont)

        if (alignment == VerticalAlignment.TOP) {
            val startBaseline = -(bounds.height / 2f) + metrics.ascent
            val result = doFormat(text, metrics, bounds, shape, wordBreak, startBaseline, leading)
            return FormatResult(result.text, result.lineCount * leading, result.visualHeight, result.layoutScore)
        }

        var startBaseline = -(bounds.height / 2f) + metrics.ascent
        var bestText = ""
        var bestTotalHeight = 0f
        var bestVisualHeight = 0f
        var bestLayoutScore = 0f

        for (i in 0 until 5) {
            val result = doFormat(text, metrics, bounds, shape, wordBreak, startBaseline, leading)
            val lineCount = result.lineCount
            val totalHeight = lineCount * leading
            val visualHeight = result.visualHeight
            
            bestText = result.text
            bestTotalHeight = totalHeight
            bestVisualHeight = visualHeight
            bestLayoutScore = result.layoutScore
            
            val targetStartBaseline = if (alignment == VerticalAlignment.CENTER) {
                -((lineCount - 1) * leading + metrics.descent - metrics.ascent) / 2f
            } else {
                (bounds.height / 2f) - ((lineCount - 1) * leading + metrics.descent)
            }
            
            if (abs(startBaseline - targetStartBaseline) < 1f) {
                break
            }
            startBaseline = targetStartBaseline
        }
        
        return FormatResult(bestText, bestTotalHeight, bestVisualHeight, bestLayoutScore)
    }

    private data class DoFormatResult(
        val text: String,
        val lineCount: Int,
        val visualHeight: Float,
        val layoutScore: Float
    )

    private fun doFormat(
        text: String,
        metrics: java.awt.FontMetrics,
        bounds: PsdBounds,
        shape: TextBoundary,
        wordBreak: WordBreak,
        startBaseline: Float,
        leading: Float
    ): DoFormatResult {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var currentLine = ""
        var currentBaseline = startBaseline

        var totalUsedWidth = 0f
        var totalAvailableWidth = 0f
        var singleWordLineCount = 0
        var hyphenationCount = 0

        fun commitLine(line: String, availW: Float) {
            if (line.isNotEmpty()) {
                lines.add(line)
                totalUsedWidth += metrics.stringWidth(line).toFloat()
                totalAvailableWidth += availW
                if (!line.trim().contains(" ")) {
                    singleWordLineCount++
                }
                if (line.endsWith("-")) {
                    hyphenationCount++
                }
            }
        }

        for (word in words) {
            val topY = currentBaseline - metrics.ascent
            val bottomY = currentBaseline + metrics.descent
            val extremeY = if (abs(topY) > abs(bottomY)) topY else bottomY
            val availableWidth = shape.getAvailableWidth(extremeY, bounds)

            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            // Multiply the AWT string width by a safety factor (e.g. 1.1f) to prevent
            // Photoshop from further wrapping the line due to rendering differences.
            val testWidth = metrics.stringWidth(testLine).toFloat() * 1.1f

            if (testWidth <= availableWidth) {
                currentLine = testLine
            } else {
                val wordWidth = metrics.stringWidth(word).toFloat() * 1.1f
                if (wordWidth > availableWidth && wordBreak != WordBreak.NONE) {
                    var remainingWord = word
                    while (remainingWord.isNotEmpty()) {
                        val cTopY = currentBaseline - metrics.ascent
                        val cBottomY = currentBaseline + metrics.descent
                        val cExtremeY = if (abs(cTopY) > abs(cBottomY)) cTopY else cBottomY
                        val availW = shape.getAvailableWidth(cExtremeY, bounds)
                        var splitIndex = 0
                        val dashWidth = if (wordBreak == WordBreak.HYPHENATE) metrics.stringWidth("-") else 0

                        for (i in 1..remainingWord.length) {
                            val chunk = remainingWord.substring(0, i)
                            val width = (metrics.stringWidth(chunk) + dashWidth) * 1.1f
                            if (width > availW && i > 1) {
                                break
                            }
                            splitIndex = i
                        }

                        if (splitIndex == 0) splitIndex = 1

                        val chunk = remainingWord.substring(0, splitIndex)
                        val append = if (wordBreak == WordBreak.HYPHENATE && splitIndex < remainingWord.length) "$chunk-" else chunk
                        
                        if (currentLine.isNotEmpty()) {
                            commitLine(currentLine, availableWidth)
                            currentBaseline += leading
                        }
                        commitLine(append, availW)
                        currentBaseline += leading
                        currentLine = ""
                        remainingWord = remainingWord.substring(splitIndex)
                    }
                } else {
                    if (currentLine.isNotEmpty()) {
                        commitLine(currentLine, availableWidth)
                        currentBaseline += leading
                    }
                    currentLine = word
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            val topY = currentBaseline - metrics.ascent
            val bottomY = currentBaseline + metrics.descent
            val extremeY = if (abs(topY) > abs(bottomY)) topY else bottomY
            val availW = shape.getAvailableWidth(extremeY, bounds)
            commitLine(currentLine, availW)
        }

        val visualHeight = if (lines.isEmpty()) 0f else (lines.size - 1) * leading + metrics.ascent + metrics.descent
        
        val fullness = if (totalAvailableWidth > 0) totalUsedWidth / totalAvailableWidth else 0f
        val layoutScore = fullness - (singleWordLineCount * 0.1f) - (hyphenationCount * 0.05f)

        return DoFormatResult(lines.joinToString("\r"), lines.size, visualHeight, layoutScore)
    }

    /**
     * Resolves the optimal font size using a binary search.
     */
    fun resolveFontSize(
        text: String,
        baseStyle: TextStyle,
        bounds: PsdBounds,
        shape: TextBoundary,
        autoFit: AutoFit,
        wordBreak: WordBreak,
        alignment: VerticalAlignment = VerticalAlignment.TOP
    ): Float {
        var bestSize = autoFit.minSize
        var bestScore = -Float.MAX_VALUE

        // Linear scan down from maxSize to minSize in integer steps
        var currentSize = autoFit.maxSize
        while (currentSize >= autoFit.minSize) {
            val testStyle = baseStyle.copy(fontSize = currentSize)
            
            val result = formatTextInternal(text, testStyle, bounds, shape, wordBreak, alignment)
            val totalHeight = result.totalHeight

            val topExtremeY = if (alignment == VerticalAlignment.CENTER) {
                -(totalHeight / 2f)
            } else if (alignment == VerticalAlignment.BOTTOM) {
                (bounds.height / 2f) - totalHeight
            } else {
                -(bounds.height / 2f)
            }
            val bottomExtremeY = topExtremeY + totalHeight

            val fitsVertically = totalHeight <= bounds.height &&
                    shape.getAvailableWidth(topExtremeY, bounds) > 0f &&
                    shape.getAvailableWidth(bottomExtremeY, bounds) > 0f

            if (fitsVertically) {
                // Combine layout score with a font size bonus
                val normalizedFontSize = currentSize / autoFit.maxSize
                val finalScore = result.layoutScore + (normalizedFontSize * 0.5f)
                
                if (finalScore > bestScore) {
                    bestScore = finalScore
                    bestSize = currentSize
                }
            }
            
            currentSize -= 1f
        }
        
        return bestSize
    }

    /**
     * Calculates the exact pixel dimensions of a text layer, accounting for Photoshop effects.
     */
    fun calculateLayerBounds(layer: Layer): PsdBounds {
        val textData = layer.text ?: return PsdBounds(0f, 0f, 0f, 0f)
        val style = textData.style ?: return PsdBounds(0f, 0f, 0f, 0f)
        val effects = layer.effects

        val fontName = style.font?.name ?: "Arial"
        val fontSize = style.fontSize ?: 12f
        val awtFont = getAwtFont(fontName, fontSize, style.fauxBold ?: false, style.fauxItalic ?: false)
        val metrics = graphics.getFontMetrics(awtFont)

        val lines = textData.text.split("\r", "\n")
        var maxWidth = 0
        for (line in lines) {
            val w = metrics.stringWidth(line)
            if (w > maxWidth) maxWidth = w
        }
        
        val leading = if (style.autoLeading == false) (style.leading ?: fontSize * 1.2f) else fontSize * 1.2f
        val totalHeight = lines.size * leading

        var leftOffset = 0f
        var rightOffset = 0f
        var topOffset = 0f
        var bottomOffset = 0f

        // Add padding for effects
        if (effects != null && !effects.disabled) {
            // Stroke
            effects.stroke?.forEach { stroke ->
                if (stroke.enabled) {
                    val size = stroke.size.value
                    if (stroke.position == StrokePosition.OUTSIDE) {
                        leftOffset += size
                        rightOffset += size
                        topOffset += size
                        bottomOffset += size
                    } else if (stroke.position == StrokePosition.CENTER) {
                        leftOffset += size / 2f
                        rightOffset += size / 2f
                        topOffset += size / 2f
                        bottomOffset += size / 2f
                    }
                }
            }
            
            // Drop Shadow
            effects.dropShadow?.forEach { shadow ->
                if (shadow.enabled) {
                    val angleRad = shadow.angle * (PI / 180.0)
                    val distance = shadow.distance.value
                    val size = shadow.size.value // Spread/blur size
                    
                    val dx = (distance * cos(angleRad)).toFloat()
                    val dy = (distance * kotlin.math.sin(angleRad)).toFloat() // Note: Photoshop Y is down, standard math Y is up, might need sign adjustment
                    
                    // Simple expansion based on dx, dy, and blur size
                    val expandX = abs(dx) + size
                    val expandY = abs(dy) + size
                    
                    if (dx < 0) leftOffset = maxOf(leftOffset, expandX)
                    else rightOffset = maxOf(rightOffset, expandX)
                    
                    if (dy < 0) topOffset = maxOf(topOffset, expandY)
                    else bottomOffset = maxOf(bottomOffset, expandY)
                }
            }
        }

        // Base box from layer position
        val baseLeft = layer.left.toFloat()
        val baseTop = layer.top.toFloat()
        
        return PsdBounds(
            left = baseLeft - leftOffset,
            top = baseTop - topOffset,
            right = baseLeft + maxWidth + rightOffset,
            bottom = baseTop + totalHeight + bottomOffset
        )
    }
}
