package com.wip.kpsd

import java.awt.image.BufferedImage

/**
 * DSL Marker to scope the Kotlin DSL builder functions correctly.
 */
@DslMarker
annotation class PsdDsl

/**
 * Top-level builder function to construct a [Psd] document.
 *
 * @param width Width of the document in pixels.
 * @param height Height of the document in pixels.
 * @param channels Number of color/alpha channels (default is 4 for RGBA).
 * @param bitsPerChannel Bit depth per channel (default is 8).
 * @param colorMode Color mode format (default is RGB).
 * @param block Builder function executed within [PsdBuilder] context.
 */
fun psd(
    width: Int,
    height: Int,
    channels: Int = 4,
    bitsPerChannel: Int = 8,
    colorMode: ColorMode = ColorMode.RGB,
    block: PsdBuilder.() -> Unit
): Psd {
    val builder = PsdBuilder(width, height, channels, bitsPerChannel, colorMode)
    builder.block()
    return builder.build()
}

/**
 * Builder class for configuring document-level properties and adding layers/groups to a [Psd].
 */
@PsdDsl
class PsdBuilder(
    var width: Int = 0,
    var height: Int = 0,
    var channels: Int = 4,
    var bitsPerChannel: Int = 8,
    var colorMode: ColorMode = ColorMode.RGB
) {
    val children = mutableListOf<Layer>()
    var imageData: PixelData? = null
    var canvas: BufferedImage? = null
    var palette: MutableList<Rgb>? = null
    var globalLayerMaskInfo: GlobalLayerMaskInfo? = null
    var imageResources: ImageResources? = null

    /**
     * Appends a regular layer with a custom configuration.
     *
     * @param name Optional label name for the layer.
     * @param block Configuration builder lambda.
     */
    fun layer(name: String? = null, block: LayerBuilder.() -> Unit = {}): Layer {
        val builder = LayerBuilder(name)
        builder.block()
        val layer = builder.build()
        children.add(layer)
        return layer
    }

    /**
     * Appends a folder/group layer containing sub-layers.
     *
     * @param name Name of the group folder.
     * @param opened Whether the group is displayed open (expanded) in UI panels.
     * @param block Builder lambda to define nested contents inside this folder group.
     */
    fun group(name: String, opened: Boolean = true, block: PsdBuilder.() -> Unit): Layer {
        val builder = PsdBuilder()
        builder.block()
        val folder = Layer(
            name = name,
            opened = opened,
            children = builder.children
        )
        children.add(folder)
        return folder
    }

    /**
     * Appends a specialized text/typography layer.
     *
     * @param name Optional label name for the layer.
     * @param textValue Plain text string value.
     * @param block Configuration builder lambda.
     */
    fun textLayer(name: String? = null, textValue: String, block: TextLayerBuilder.() -> Unit = {}): Layer {
        val builder = TextLayerBuilder(name, textValue)
        builder.block()
        val layer = builder.build()
        children.add(layer)
        return layer
    }

    /**
     * Builds and returns the configured [Psd] document structure.
     */
    fun build(): Psd {
        return Psd(
            width = width,
            height = height,
            channels = channels,
            bitsPerChannel = bitsPerChannel,
            colorMode = colorMode,
            palette = palette,
            children = children,
            imageData = imageData,
            canvas = canvas,
            globalLayerMaskInfo = globalLayerMaskInfo,
            imageResources = imageResources
        )
    }
}

/**
 * Builder class for configuring individual layer properties, effects, and masks.
 */
@PsdDsl
class LayerBuilder(var name: String? = null) {
    var top: Int = 0
    var left: Int = 0
    var bottom: Int = 0
    var right: Int = 0
    var blendMode: BlendMode = BlendMode.NORMAL
    var opacity: Float = 1f
    var clipping: Boolean = false
    var hidden: Boolean = false
    var transparencyProtected: Boolean = false
    var effectsOpen: Boolean = false
    var imageData: PixelData? = null
    var canvas: BufferedImage? = null
    var id: Int? = null
    var linkGroup: Int? = null
    var linkGroupEnabled: Boolean? = null
    var blendingRanges: BlendingRanges? = null
    
    var mask: LayerMaskData? = null
    var realMask: LayerMaskData? = null
    var effects: LayerEffectsInfo? = null

    /**
     * Configures a user layer mask.
     */
    fun mask(block: LayerMaskBuilder.() -> Unit) {
        mask = LayerMaskBuilder().apply(block).build()
    }

    /**
     * Configures a real mask vector layer reference.
     */
    fun realMask(block: LayerMaskBuilder.() -> Unit) {
        realMask = LayerMaskBuilder().apply(block).build()
    }

    /**
     * Configures layer style effects (stroke, shadow, etc.).
     */
    fun effects(block: EffectsBuilder.() -> Unit) {
        effects = EffectsBuilder().apply(block).build()
    }

    /**
     * Builds and returns the configured [Layer].
     */
    fun build(): Layer {
        return Layer(
            name = name,
            top = top,
            left = left,
            bottom = bottom,
            right = right,
            blendMode = blendMode,
            opacity = opacity,
            clipping = clipping,
            hidden = hidden,
            transparencyProtected = transparencyProtected,
            effectsOpen = effectsOpen,
            imageData = imageData,
            canvas = canvas,
            mask = mask,
            realMask = realMask,
            id = id,
            linkGroup = linkGroup,
            linkGroupEnabled = linkGroupEnabled,
            blendingRanges = blendingRanges,
            effects = effects
        )
    }
}

/**
 * Builder class for constructing a [LayerMaskData] container.
 */
@PsdDsl
class LayerMaskBuilder {
    var top: Int? = null
    var left: Int? = null
    var bottom: Int? = null
    var right: Int? = null
    var defaultColor: Int? = null
    var disabled: Boolean? = null
    var positionRelativeToLayer: Boolean? = null
    var fromVectorData: Boolean? = null
    var userMaskDensity: Float? = null
    var userMaskFeather: Double? = null
    var vectorMaskDensity: Float? = null
    var vectorMaskFeather: Double? = null
    var canvas: BufferedImage? = null
    var imageData: PixelData? = null

    fun build(): LayerMaskData {
        return LayerMaskData(
            top = top,
            left = left,
            bottom = bottom,
            right = right,
            defaultColor = defaultColor,
            disabled = disabled,
            positionRelativeToLayer = positionRelativeToLayer,
            fromVectorData = fromVectorData,
            userMaskDensity = userMaskDensity,
            userMaskFeather = userMaskFeather,
            vectorMaskDensity = vectorMaskDensity,
            vectorMaskFeather = vectorMaskFeather,
            canvas = canvas,
            imageData = imageData
        )
    }
}

/**
 * Builder class for adding stroke and shadow effects.
 */
@PsdDsl
class EffectsBuilder {
    var disabled: Boolean = false
    var scale: Float = 1f
    private val strokes = mutableListOf<LayerEffectStroke>()
    private val dropShadows = mutableListOf<LayerEffectShadow>()
    private val innerShadows = mutableListOf<LayerEffectShadow>()

    /**
     * Adds a stroke layer effect.
     */
    fun stroke(block: StrokeBuilder.() -> Unit) {
        strokes.add(StrokeBuilder().apply(block).build())
    }

    /**
     * Adds a drop shadow layer effect.
     */
    fun dropShadow(block: ShadowBuilder.() -> Unit) {
        dropShadows.add(ShadowBuilder().apply(block).build())
    }

    /**
     * Adds an inner shadow layer effect.
     */
    fun innerShadow(block: ShadowBuilder.() -> Unit) {
        innerShadows.add(ShadowBuilder().apply(block).build())
    }

    fun build(): LayerEffectsInfo {
        return LayerEffectsInfo(
            disabled = disabled,
            scale = scale,
            stroke = if (strokes.isEmpty()) null else strokes,
            dropShadow = if (dropShadows.isEmpty()) null else dropShadows,
            innerShadow = if (innerShadows.isEmpty()) null else innerShadows
        )
    }
}

/**
 * Builder class for configuring a [LayerEffectStroke] effect.
 */
@PsdDsl
class StrokeBuilder {
    var enabled: Boolean = true
    var present: Boolean = true
    var showInDialog: Boolean = true
    var size: UnitsValue = UnitsValue(Units.PIXELS, 1f)
    var position: StrokePosition = StrokePosition.OUTSIDE
    var fillType: StrokeFillType = StrokeFillType.COLOR
    var blendMode: BlendMode = BlendMode.NORMAL
    var opacity: Float = 1f
    var color: Color? = null
    var overprint: Boolean? = null

    /**
     * Helper to set RGB stroke color.
     */
    fun rgb(r: Int, g: Int, b: Int) {
        color = Rgb(r, g, b)
    }

    /**
     * Helper to set RGBA stroke color.
     */
    fun rgba(r: Int, g: Int, b: Int, a: Int) {
        color = Rgba(r, g, b, a)
    }

    fun build(): LayerEffectStroke {
        return LayerEffectStroke(
            enabled = enabled,
            present = present,
            showInDialog = showInDialog,
            size = size,
            position = position,
            fillType = fillType,
            blendMode = blendMode,
            opacity = opacity,
            color = color,
            overprint = overprint
        )
    }
}

/**
 * Builder class for configuring a [LayerEffectShadow] effect.
 */
@PsdDsl
class ShadowBuilder {
    var enabled: Boolean = true
    var present: Boolean = true
    var showInDialog: Boolean = true
    var size: UnitsValue = UnitsValue(Units.PIXELS, 0f)
    var angle: Float = 120f
    var distance: UnitsValue = UnitsValue(Units.PIXELS, 0f)
    var color: Color? = null
    var blendMode: BlendMode = BlendMode.MULTIPLY
    var opacity: Float = 0.75f
    var useGlobalLight: Boolean = true
    var antialiased: Boolean = false
    var choke: UnitsValue = UnitsValue(Units.PIXELS, 0f)
    var layerConceals: Boolean = true

    /**
     * Helper to set RGB shadow color.
     */
    fun rgb(r: Int, g: Int, b: Int) {
        color = Rgb(r, g, b)
    }

    /**
     * Helper to set RGBA shadow color.
     */
    fun rgba(r: Int, g: Int, b: Int, a: Int) {
        color = Rgba(r, g, b, a)
    }

    fun build(): LayerEffectShadow {
        return LayerEffectShadow(
            enabled = enabled,
            present = present,
            showInDialog = showInDialog,
            size = size,
            angle = angle,
            distance = distance,
            color = color,
            blendMode = blendMode,
            opacity = opacity,
            useGlobalLight = useGlobalLight,
            antialiased = antialiased,
            choke = choke,
            layerConceals = layerConceals
        )
    }
}

/**
 * Builder class for creating specialized text layers.
 */
@PsdDsl
class TextLayerBuilder(var name: String? = null, var text: String) {
    var top: Int = 0
    var left: Int = 0
    var bottom: Int = 0
    var right: Int = 0
    var blendMode: BlendMode = BlendMode.NORMAL
    var opacity: Float = 1f
    var clipping: Boolean = false
    var hidden: Boolean = false
    var id: Int? = null

    var transform: DoubleArray? = null
    var antiAlias: AntiAlias = AntiAlias.SHARP
    var gridding: TextGridding = TextGridding.NONE
    var orientation: Orientation = Orientation.HORIZONTAL
    var shapeType: TextShapeType = TextShapeType.POINT
    var boxBounds: FloatArray? = null
    
    var style: TextStyle? = null
    var paragraphStyle: ParagraphStyle? = null
    var styleRuns: List<TextStyleRun>? = null
    var paragraphStyleRuns: List<ParagraphStyleRun>? = null
    var effectsOpen: Boolean = false
    var effects: LayerEffectsInfo? = null

    var textLeft: Float? = null
    var textTop: Float? = null
    var textRight: Float? = null
    var textBottom: Float? = null

    /**
     * Sets a translation/rotation transform matrix on the text layer.
     */
    fun transform(vararg values: Double) {
        transform = values
    }

    /**
     * Configures the typography style block.
     */
    fun style(block: TextStyleBuilder.() -> Unit) {
        style = TextStyleBuilder().apply(block).build()
    }

    /**
     * Configures paragraph layout attributes.
     */
    fun paragraphStyle(block: ParagraphStyleBuilder.() -> Unit) {
        paragraphStyle = ParagraphStyleBuilder().apply(block).build()
    }

    /**
     * Configures layer style effects (stroke, shadow, etc.).
     */
    fun effects(block: EffectsBuilder.() -> Unit) {
        effects = EffectsBuilder().apply(block).build()
    }

    /**
     * Builds and returns the configured text [Layer].
     */
    fun build(): Layer {
        val finalStyle = style ?: TextStyle()
        val textData = LayerTextData(
            text = text,
            transform = transform ?: doubleArrayOf(1.0, 0.0, 0.0, 1.0, left.toDouble(), top.toDouble()),
            antiAlias = antiAlias,
            gridding = gridding,
            orientation = orientation,
            shapeType = shapeType,
            boxBounds = boxBounds,
            style = finalStyle,
            paragraphStyle = paragraphStyle,
            styleRuns = styleRuns,
            paragraphStyleRuns = paragraphStyleRuns,
            left = textLeft ?: if (shapeType == TextShapeType.BOX) 0f else null,
            top = textTop ?: if (shapeType == TextShapeType.BOX) 0f else null,
            right = textRight ?: if (shapeType == TextShapeType.BOX && boxBounds != null && boxBounds!!.size >= 3) boxBounds!![2] else null,
            bottom = textBottom ?: if (shapeType == TextShapeType.BOX && boxBounds != null && boxBounds!!.size >= 4) boxBounds!![3] else null
        )

        return Layer(
            name = name ?: text,
            top = top,
            left = left,
            bottom = bottom,
            right = right,
            blendMode = blendMode,
            opacity = opacity,
            clipping = clipping,
            hidden = hidden,
            id = id,
            text = textData,
            effects = effects,
            effectsOpen = effectsOpen
        )
    }
}

/**
 * Builder class for configuring KDoc-documented [TextStyle] properties.
 */
@PsdDsl
class TextStyleBuilder {
    var font: Font? = null
    var fontSize: Float? = null
    var fauxBold: Boolean? = null
    var fauxItalic: Boolean? = null
    var autoLeading: Boolean? = null
    var leading: Float? = null
    var horizontalScale: Float? = null
    var verticalScale: Float? = null
    var tracking: Float? = null
    var autoKerning: Boolean? = null
    var kerning: Float? = null
    var baselineShift: Float? = null
    var fontCaps: Int? = null
    var fontBaseline: Int? = null
    var underline: Boolean? = null
    var strikethrough: Boolean? = null
    var ligatures: Boolean? = null
    var dLigatures: Boolean? = null
    var baselineDirection: Int? = null
    var tsume: Float? = null
    var styleRunAlignment: Int? = null
    var language: Int? = null
    var noBreak: Boolean? = null
    var fillColor: Color? = null
    var strokeColor: Color? = null
    var fillFlag: Boolean? = null
    var strokeFlag: Boolean? = null
    var fillFirst: Boolean? = null
    var yUnderline: Int? = null
    var outlineWidth: Float? = null
    var characterDirection: Int? = null
    var hindiNumbers: Boolean? = null
    var kashida: Float? = null
    var diacriticPos: Int? = null

    /**
     * Sets Font parameters.
     */
    fun font(name: String, script: Int? = null, type: Int? = null, synthetic: Int? = null) {
        font = Font(name, script, type, synthetic)
    }

    /**
     * Sets RGB fill color.
     */
    fun fillColor(r: Int, g: Int, b: Int) {
        fillColor = Rgb(r, g, b)
    }

    /**
     * Sets RGBA fill color.
     */
    fun fillColor(r: Int, g: Int, b: Int, a: Int) {
        fillColor = Rgba(r, g, b, a)
    }

    /**
     * Sets RGB stroke color.
     */
    fun strokeColor(r: Int, g: Int, b: Int) {
        strokeColor = Rgb(r, g, b)
    }

    fun build(): TextStyle {
        return TextStyle(
            font = font,
            fontSize = fontSize,
            fauxBold = fauxBold,
            fauxItalic = fauxItalic,
            autoLeading = autoLeading,
            leading = leading,
            horizontalScale = horizontalScale,
            verticalScale = verticalScale,
            tracking = tracking,
            autoKerning = autoKerning,
            kerning = kerning,
            baselineShift = baselineShift,
            fontCaps = fontCaps,
            fontBaseline = fontBaseline,
            underline = underline,
            strikethrough = strikethrough,
            ligatures = ligatures,
            dLigatures = dLigatures,
            baselineDirection = baselineDirection,
            tsume = tsume,
            styleRunAlignment = styleRunAlignment,
            language = language,
            noBreak = noBreak,
            fillColor = fillColor,
            strokeColor = strokeColor,
            fillFlag = fillFlag,
            strokeFlag = strokeFlag,
            fillFirst = fillFirst,
            yUnderline = yUnderline,
            outlineWidth = outlineWidth,
            characterDirection = characterDirection,
            hindiNumbers = hindiNumbers,
            kashida = kashida,
            diacriticPos = diacriticPos
        )
    }
}

/**
 * Builder class for configuring [ParagraphStyle] layout parameters.
 */
@PsdDsl
class ParagraphStyleBuilder {
    var justification: Justification? = null
    var firstLineIndent: Float? = null
    var startIndent: Float? = null
    var endIndent: Float? = null
    var spaceBefore: Float? = null
    var spaceAfter: Float? = null
    var autoHyphenate: Boolean? = null
    var hyphenatedWordSize: Int? = null
    var preHyphen: Int? = null
    var postHyphen: Int? = null
    var consecutiveHyphens: Int? = null
    var zone: Float? = null
    var wordSpacing: FloatArray? = null
    var letterSpacing: FloatArray? = null
    var glyphSpacing: FloatArray? = null
    var autoLeading: Float? = null
    var leadingType: Int? = null
    var hanging: Boolean? = null
    var burasagari: Boolean? = null
    var kinsokuOrder: Int? = null
    var everyLineComposer: Boolean? = null

    fun build(): ParagraphStyle {
        return ParagraphStyle(
            justification = justification,
            firstLineIndent = firstLineIndent,
            startIndent = startIndent,
            endIndent = endIndent,
            spaceBefore = spaceBefore,
            spaceAfter = spaceAfter,
            autoHyphenate = autoHyphenate,
            hyphenatedWordSize = hyphenatedWordSize,
            preHyphen = preHyphen,
            postHyphen = postHyphen,
            consecutiveHyphens = consecutiveHyphens,
            zone = zone,
            wordSpacing = wordSpacing,
            letterSpacing = letterSpacing,
            glyphSpacing = glyphSpacing,
            autoLeading = autoLeading,
            leadingType = leadingType,
            hanging = hanging,
            burasagari = burasagari,
            kinsokuOrder = kinsokuOrder,
            everyLineComposer = everyLineComposer
        )
    }
}
