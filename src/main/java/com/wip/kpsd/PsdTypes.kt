package com.wip.kpsd

import java.awt.image.BufferedImage

/**
 * Color mode of the PSD document.
 */
enum class ColorMode(val value: Int) {
    /** Monochrome image (1 bit per pixel). */
    Bitmap(0),
    /** 8-bit grayscale image. */
    Grayscale(1),
    /** Indexed color image with a palette. */
    Indexed(2),
    /** Standard red, green, blue color mode. */
    RGB(3),
    /** Cyan, magenta, yellow, black color mode. */
    CMYK(4),
    /** Image containing multiple custom color channels. */
    Multichannel(7),
    /** Grayscale image printed with custom inks. */
    Duotone(8),
    /** CIELAB color mode. */
    Lab(9);

    companion object {
        /** Returns the [ColorMode] corresponding to the integer value, falling back to [RGB] if invalid. */
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: RGB
    }
}

/**
 * Section divider type used to structure layer groups.
 */
enum class SectionDividerType(val value: Int) {
    /** Regular layer. */
    Other(0),
    /** Start of an open folder group. */
    OpenFolder(1),
    /** Start of a closed folder group. */
    ClosedFolder(2),
    /** Bounding divider marking the end of a folder group. */
    BoundingSectionDivider(3);

    companion object {
        /** Returns the [SectionDividerType] corresponding to the integer value, falling back to [Other] if invalid. */
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: Other
    }
}

/**
 * Compression mode used for layer channel data or composite image data.
 */
enum class Compression(val value: Int) {
    /** Raw, uncompressed channel data. */
    RawData(0),
    /** PackBits RLE compressed channel data. */
    RleCompressed(1),
    /** ZIP compressed channel data without prediction. */
    ZipWithoutPrediction(2),
    /** ZIP compressed channel data with horizontal differencing prediction. */
    ZipWithPrediction(3);

    companion object {
        /** Returns the [Compression] corresponding to the integer value, falling back to [RawData] if invalid. */
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: RawData
    }
}

/**
 * Photoshop layer channel identifier.
 */
enum class ChannelID(val value: Int) {
    /** Alpha/transparency channel. */
    Transparency(-1),
    /** Red channel (or first color channel). */
    Color0(0),
    /** Green channel (or second color channel). */
    Color1(1),
    /** Blue channel (or third color channel). */
    Color2(2),
    /** Fourth color channel (e.g., Black in CMYK). */
    Color3(3),
    /** Fifth color channel. */
    Color4(4),
    /** User layer mask. */
    UserMask(-2),
    /** Real user layer mask (e.g. from vector data). */
    RealUserMask(-3);

    companion object {
        /** Returns the [ChannelID] corresponding to the integer value, falling back to [Color0] if invalid. */
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: Color0
    }
}

/**
 * Photoshop layer and effect blend modes.
 */
enum class BlendMode(val value: String) {
    /** Folder blend mode representing 'pass through'. */
    PASS_THROUGH("pass through"),
    /** Standard normal blend mode. */
    NORMAL("normal"),
    /** Dissolve blend mode. */
    DISSOLVE("dissolve"),
    /** Darken blend mode. */
    DARKEN("darken"),
    /** Multiply blend mode. */
    MULTIPLY("multiply"),
    /** Color Burn blend mode. */
    COLOR_BURN("color burn"),
    /** Linear Burn blend mode. */
    LINEAR_BURN("linear burn"),
    /** Darker Color blend mode. */
    DARKER_COLOR("darker color"),
    /** Lighten blend mode. */
    LIGHTEN("lighten"),
    /** Screen blend mode. */
    SCREEN("screen"),
    /** Color Dodge blend mode. */
    COLOR_DODGE("color dodge"),
    /** Linear Dodge blend mode. */
    LINEAR_DODGE("linear dodge"),
    /** Lighter Color blend mode. */
    LIGHTER_COLOR("lighter color"),
    /** Overlay blend mode. */
    OVERLAY("overlay"),
    /** Soft Light blend mode. */
    SOFT_LIGHT("soft light"),
    /** Hard Light blend mode. */
    HARD_LIGHT("hard light"),
    /** Vivid Light blend mode. */
    VIVID_LIGHT("vivid light"),
    /** Linear Light blend mode. */
    LINEAR_LIGHT("linear light"),
    /** Pin Light blend mode. */
    PIN_LIGHT("pin light"),
    /** Hard Mix blend mode. */
    HARD_MIX("hard mix"),
    /** Difference blend mode. */
    DIFFERENCE("difference"),
    /** Exclusion blend mode. */
    EXCLUSION("exclusion"),
    /** Subtract blend mode. */
    SUBTRACT("subtract"),
    /** Divide blend mode. */
    DIVIDE("divide"),
    /** Hue blend mode. */
    HUE("hue"),
    /** Saturation blend mode. */
    SATURATION("saturation"),
    /** Color blend mode. */
    COLOR("color"),
    /** Luminosity blend mode. */
    LUMINOSITY("luminosity");

    companion object {
        /** Helper to find [BlendMode] from String value. */
        fun fromString(str: String?): BlendMode = entries.firstOrNull { it.value == str } ?: NORMAL
    }
}

/**
 * Position for layer stroke effects.
 */
enum class StrokePosition(val value: String) {
    /** Stroke drawn inside the boundary. */
    INSIDE("inside"),
    /** Stroke centered on the boundary. */
    CENTER("center"),
    /** Stroke drawn outside the boundary. */
    OUTSIDE("outside");

    companion object {
        /** Helper to find [StrokePosition] from String value. */
        fun fromString(str: String?): StrokePosition = entries.firstOrNull { it.value == str } ?: OUTSIDE
    }
}

/**
 * Stroke fill type.
 */
enum class StrokeFillType(val value: String) {
    /** Solid color fill. */
    COLOR("color"),
    /** Gradient fill. */
    GRADIENT("gradient"),
    /** Pattern fill. */
    PATTERN("pattern");

    companion object {
        /** Helper to find [StrokeFillType] from String value. */
        fun fromString(str: String?): StrokeFillType = entries.firstOrNull { it.value == str } ?: COLOR
    }
}

/**
 * Text anti-alias settings.
 */
enum class AntiAlias(val value: String) {
    /** No anti-aliasing. */
    NONE("none"),
    /** Sharp anti-aliasing. */
    SHARP("sharp"),
    /** Crisp anti-aliasing. */
    CRISP("crisp"),
    /** Strong anti-aliasing. */
    STRONG("strong"),
    /** Smooth anti-aliasing. */
    SMOOTH("smooth");

    companion object {
        /** Helper to find [AntiAlias] from String value. */
        fun fromString(str: String?): AntiAlias = entries.firstOrNull { it.value == str } ?: SHARP
    }
}

/**
 * Text alignment/justification options.
 */
enum class Justification(val value: String) {
    /** Left-aligned text. */
    LEFT("left"),
    /** Right-aligned text. */
    RIGHT("right"),
    /** Centered text. */
    CENTER("center");

    companion object {
        /** Helper to find [Justification] from String value. */
        fun fromString(str: String?): Justification = entries.firstOrNull { it.value == str } ?: LEFT
    }
}

/**
 * Text gridding mode.
 */
enum class TextGridding(val value: String) {
    /** No text gridding. */
    NONE("none"),
    /** Align to pixel boundaries. */
    ROUND("round");

    companion object {
        /** Helper to find [TextGridding] from String value. */
        fun fromString(str: String?): TextGridding = entries.firstOrNull { it.value == str } ?: NONE
    }
}

/**
 * Orientation of elements (such as text layer or warp).
 */
enum class Orientation(val value: String) {
    /** Horizontal layout. */
    HORIZONTAL("horizontal"),
    /** Vertical layout. */
    VERTICAL("vertical");

    companion object {
        /** Helper to find [Orientation] from String value. */
        fun fromString(str: String?): Orientation = entries.firstOrNull { it.value == str } ?: HORIZONTAL
    }
}

/**
 * Text frame layout types.
 */
enum class TextShapeType(val value: String) {
    /** Point text (single line/unbounded). */
    POINT("point"),
    /** Paragraph/Box text (bound to dimensions). */
    BOX("box");

    companion object {
        /** Helper to find [TextShapeType] from String value. */
        fun fromString(str: String?): TextShapeType = entries.firstOrNull { it.value == str } ?: POINT
    }
}

/**
 * Warp transform styles.
 */
enum class WarpStyle(val value: String) {
    /** No warp transformation. */
    NONE("none"),
    /** Arc warp style. */
    ARC("arc"),
    /** Arc Lower warp style. */
    ARC_LOWER("arcLower"),
    /** Arc Upper warp style. */
    ARC_UPPER("arcUpper"),
    /** Arch warp style. */
    ARCH("arch"),
    /** Bulge warp style. */
    BULGE("bulge"),
    /** Shell Lower warp style. */
    SHELL_LOWER("shellLower"),
    /** Shell Upper warp style. */
    SHELL_UPPER("shellUpper"),
    /** Flag warp style. */
    FLAG("flag"),
    /** Wave warp style. */
    WAVE("wave"),
    /** Fish warp style. */
    FISH("fish"),
    /** Rise warp style. */
    RISE("rise"),
    /** Fisheye warp style. */
    FISHEYE("fisheye"),
    /** Inflate warp style. */
    INFLATE("inflate"),
    /** Squeeze warp style. */
    SQUEEZE("squeeze"),
    /** Twist warp style. */
    TWIST("twist");

    companion object {
        /** Helper to find [WarpStyle] from String value. */
        fun fromString(str: String?): WarpStyle = entries.firstOrNull { it.value == str } ?: NONE
    }
}

/**
 * Units of measure for PSD parameters.
 */
enum class Units(val value: String) {
    /** Unit representing pixels. */
    PIXELS("Pixels"),
    /** Unit representing points. */
    POINTS("Points"),
    /** Unit representing percentages. */
    PERCENT("Percent"),
    /** Unit representing angles. */
    ANGLE("Angle"),
    /** Unit representing density. */
    DENSITY("Density"),
    /** Unit representing distance. */
    DISTANCE("Distance"),
    /** Unit representing millimeters. */
    MILLIMETERS("Millimeters"),
    /** Unit representing picas. */
    PICAS("Picas"),
    /** Unit representing inches. */
    INCHES("Inches"),
    /** Unit representing centimeters. */
    CENTIMETERS("Centimeters");

    companion object {
        /** Helper to find [Units] from String value. */
        fun fromString(str: String?): Units = entries.firstOrNull { it.value == str } ?: PIXELS
    }
}

/**
 * Resolution unit settings.
 */
enum class ResolutionUnit(val value: String) {
    /** Pixels Per Inch. */
    PPI("PPI"),
    /** Pixels Per Centimeter. */
    PPCM("PPCM");

    companion object {
        /** Helper to find [ResolutionUnit] from String value. */
        fun fromString(str: String?): ResolutionUnit = entries.firstOrNull { it.value == str } ?: PPI
    }
}

/**
 * Physical measurement unit choices.
 */
enum class MeasurementUnit(val value: String) {
    /** Inches. */
    INCHES("Inches"),
    /** Centimeters. */
    CENTIMETERS("Centimeters"),
    /** Points. */
    POINTS("Points"),
    /** Picas. */
    PICAS("Picas"),
    /** Column units. */
    COLUMNS("Columns");

    companion object {
        /** Helper to find [MeasurementUnit] from String value. */
        fun fromString(str: String?): MeasurementUnit = entries.firstOrNull { it.value == str } ?: INCHES
    }
}

/**
 * Base sealed interface representing color formats in PSD documents.
 */
sealed interface Color

/**
 * Red, Green, Blue, Alpha (8-bit per channel) color representation.
 */
data class Rgba(val r: Int, val g: Int, val b: Int, val a: Int) : Color

/**
 * Red, Green, Blue (8-bit per channel) color representation.
 */
data class Rgb(val r: Int, val g: Int, val b: Int) : Color

/**
 * Floating point Red, Green, Blue color representation (range 0.0 to 1.0).
 */
data class Frgb(val fr: Float, val fg: Float, val fb: Float) : Color

/**
 * Hue, Saturation, Brightness color representation.
 */
data class Hsb(val h: Float, val s: Float, val b: Float) : Color

/**
 * Cyan, Magenta, Yellow, Key (black) color representation (range 0 to 100).
 */
data class Cmyk(val c: Int, val m: Int, val y: Int, val k: Int) : Color

/**
 * CIELAB color representation.
 */
data class Lab(val l: Float, val a: Float, val b: Float) : Color

/**
 * Grayscale key color representation (range 0 to 255).
 */
data class GrayscaleColor(val k: Int) : Color

/**
 * Container representing raw, channel-interleaved pixel buffers in the PSD document.
 *
 * @property width Width of the image in pixels.
 * @property height Height of the image in pixels.
 * @property data Row-major byte array representing pixel channels.
 */
data class PixelData(
    val width: Int,
    val height: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelData) return false
        if (width != other.width) return false
        if (height != other.height) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Represents the layer mask associated with a layer.
 */
data class LayerMaskData(
    /** Top coordinate of the mask boundaries. */
    var top: Int? = null,
    /** Left coordinate of the mask boundaries. */
    var left: Int? = null,
    /** Bottom coordinate of the mask boundaries. */
    var bottom: Int? = null,
    /** Right coordinate of the mask boundaries. */
    var right: Int? = null,
    /** Default color when mask is not active. */
    var defaultColor: Int? = null,
    /** Whether this mask is disabled. */
    var disabled: Boolean? = null,
    /** Position relative to the parent layer. */
    var positionRelativeToLayer: Boolean? = null,
    /** True if mask was generated from vector mask path data. */
    var fromVectorData: Boolean? = null,
    /** Mask density opacity (0.0 to 1.0). */
    var userMaskDensity: Float? = null,
    /** Mask feathering in points. */
    var userMaskFeather: Double? = null,
    /** Vector mask density (0.0 to 1.0). */
    var vectorMaskDensity: Float? = null,
    /** Vector mask feathering in points. */
    var vectorMaskFeather: Double? = null,
    /** BufferedImage view of the mask. */
    var canvas: BufferedImage? = null,
    /** Raw [PixelData] of the mask. */
    var imageData: PixelData? = null
)

/**
 * Metadata indicating a folder divider block structure in the PSD hierarchy.
 */
data class SectionDivider(
    /** The divider section type. */
    var type: SectionDividerType,
    /** Signature key for blend modes. */
    var key: String? = null,
    /** Subtype information. */
    var subType: Int? = null
)

/**
 * Text Font resource.
 */
data class Font(
    /** PostScript name of the font. */
    val name: String,
    /** Script code identifier. */
    val script: Int? = null,
    /** Font type code. */
    val type: Int? = null,
    /** Synthetic style properties. */
    val synthetic: Int? = null
)

/**
 * Warp transform properties associated with text layers.
 */
data class Warp(
    /** Style of warp effect. */
    var style: WarpStyle = WarpStyle.NONE,
    /** Warp factor value (bend amount). */
    var value: Float? = null,
    /** Warp style array factors. */
    var values: FloatArray? = null,
    /** Warp perspective horizontal adjustment. */
    var perspective: Float? = null,
    /** Warp perspective vertical adjustment. */
    var perspectiveOther: Float? = null,
    /** Warp orientation direction. */
    var rotate: Orientation = Orientation.HORIZONTAL,
    /** Bounding box of warp limits. */
    var bounds: UnitsBounds? = null,
    /** Deform envelope grid parameters. */
    var uOrder: Int? = null,
    /** Deform envelope grid parameters. */
    var vOrder: Int? = null,
    /** Deform envelope mesh points layout columns. */
    var deformNumRows: Int? = null,
    /** Deform envelope mesh points layout rows. */
    var deformNumCols: Int? = null,
    /** Custom envelope mesh parameters. */
    var customEnvelopeWarp: CustomEnvelopeWarp? = null
)

/**
 * Bounding box representing boundaries using units value coordinates.
 */
data class UnitsBounds(
    val top: UnitsValue,
    val left: UnitsValue,
    val right: UnitsValue,
    val bottom: UnitsValue
)

/**
 * Double value representation tagged with a [Units] type.
 */
data class UnitsValue(
    /** Unit type designation. */
    val units: Units,
    /** Floating point magnitude value. */
    val value: Float
)

/**
 * Custom envelope warp grid mapping parameters.
 */
data class CustomEnvelopeWarp(
    val quiltSliceX: FloatArray? = null,
    val quiltSliceY: FloatArray? = null,
    val meshPoints: List<Point>
)

/**
 * Graphic Contour definition.
 */
data class EffectContour(
    val name: String,
    val curve: List<Point>
)

/**
 * Stroke layer effect configuration.
 */
data class LayerEffectStroke(
    /** Whether the stroke is enabled. */
    var enabled: Boolean = true,
    /** Whether the stroke is present. */
    var present: Boolean = true,
    /** Show stroke settings inside dialogs. */
    var showInDialog: Boolean = true,
    /** Thickness of stroke. */
    var size: UnitsValue = UnitsValue(Units.PIXELS, 1f),
    /** Placement of stroke. */
    var position: StrokePosition = StrokePosition.OUTSIDE,
    /** Type of paint inside stroke. */
    var fillType: StrokeFillType = StrokeFillType.COLOR,
    /** Opacity blending mode. */
    var blendMode: BlendMode = BlendMode.NORMAL,
    /** Stroke transparency opacity (0.0 to 1.0). */
    var opacity: Float = 1f,
    /** Color of the stroke. */
    var color: Color? = null,
    /** Whether overprint is enabled. */
    var overprint: Boolean? = null
)

/**
 * Shadow/Glow layer effect configuration (supports drop shadow, inner shadow).
 */
data class LayerEffectShadow(
    /** Whether shadow is active. */
    var enabled: Boolean = true,
    /** Presence check. */
    var present: Boolean = true,
    /** Show shadow settings in dialog. */
    var showInDialog: Boolean = true,
    /** Size (blur size) of the shadow. */
    var size: UnitsValue = UnitsValue(Units.PIXELS, 0f),
    /** Light source projection angle (degrees). */
    var angle: Float = 120f,
    /** Displacement offset distance. */
    var distance: UnitsValue = UnitsValue(Units.PIXELS, 0f),
    /** Color of shadow. */
    var color: Color? = null,
    /** Opacity blending mode. */
    var blendMode: BlendMode = BlendMode.MULTIPLY,
    /** Shadow opacity (0.0 to 1.0). */
    var opacity: Float = 0.75f,
    /** Sync light direction with document global light angle. */
    var useGlobalLight: Boolean = true,
    /** Smooth border edges. */
    var antialiased: Boolean = false,
    /** Spread/Choke percent of shadow. */
    var choke: UnitsValue = UnitsValue(Units.PIXELS, 0f),
    /** Allow layer visibility to mask/conceal the shadow. */
    var layerConceals: Boolean = true
)

/**
 * Aggregate properties representing all active layer styling effects.
 */
data class LayerEffectsInfo(
    var disabled: Boolean = false,
    var scale: Float = 1f,
    var stroke: List<LayerEffectStroke>? = null,
    var dropShadow: List<LayerEffectShadow>? = null,
    var innerShadow: List<LayerEffectShadow>? = null
)

/**
 * Point representation.
 */
data class Point(val x: Float, val y: Float)

/**
 * Typography styling formatting records.
 */
data class TextStyle(
    var font: Font? = null,
    var fontSize: Float? = null,
    var fauxBold: Boolean? = null,
    var fauxItalic: Boolean? = null,
    var autoLeading: Boolean? = null,
    var leading: Float? = null,
    var horizontalScale: Float? = null,
    var verticalScale: Float? = null,
    var tracking: Float? = null,
    var autoKerning: Boolean? = null,
    var kerning: Float? = null,
    var baselineShift: Float? = null,
    /** FontCaps formatting flags. */
    var fontCaps: Int? = null,
    /** FontBaseline alignment settings. */
    var fontBaseline: Int? = null,
    var underline: Boolean? = null,
    var strikethrough: Boolean? = null,
    var ligatures: Boolean? = null,
    var dLigatures: Boolean? = null,
    /** Text direction index. */
    var baselineDirection: Int? = null,
    var tsume: Float? = null,
    /** Style run alignment index. */
    var styleRunAlignment: Int? = null,
    /** Locale language index. */
    var language: Int? = null,
    var noBreak: Boolean? = null,
    var fillColor: Color? = null,
    var strokeColor: Color? = null,
    var fillFlag: Boolean? = null,
    var strokeFlag: Boolean? = null,
    var fillFirst: Boolean? = null,
    var yUnderline: Int? = null,
    var outlineWidth: Float? = null,
    /** Unicode layout properties. */
    var characterDirection: Int? = null,
    var hindiNumbers: Boolean? = null,
    var kashida: Float? = null,
    /** Accent marks diacritic positions. */
    var diacriticPos: Int? = null,
    /** Auto-fit text scale constraints. */
    var autoFit: AutoFit? = null
)

/**
 * Run-length span format tracking styles on sub-strings.
 */
data class TextStyleRun(
    /** Length of the substring span. */
    val length: Int,
    /** Text formatting styling parameters. */
    val style: TextStyle
)

/**
 * Text paragraph formatting parameters.
 */
data class ParagraphStyle(
    /** Alignment justification mode. */
    var justification: Justification? = null,
    var firstLineIndent: Float? = null,
    var startIndent: Float? = null,
    var endIndent: Float? = null,
    var spaceBefore: Float? = null,
    var spaceAfter: Float? = null,
    var autoHyphenate: Boolean? = null,
    var hyphenatedWordSize: Int? = null,
    var preHyphen: Int? = null,
    var postHyphen: Int? = null,
    var consecutiveHyphens: Int? = null,
    var zone: Float? = null,
    var wordSpacing: FloatArray? = null,
    var letterSpacing: FloatArray? = null,
    var glyphSpacing: FloatArray? = null,
    var autoLeading: Float? = null,
    /** Leading type indexing. */
    var leadingType: Int? = null,
    var hanging: Boolean? = null,
    var burasagari: Boolean? = null,
    /** Line breaking rules index. */
    var kinsokuOrder: Int? = null,
    var everyLineComposer: Boolean? = null
)

/**
 * Run-length span tracking paragraph style formatting on sub-paragraphs.
 */
data class ParagraphStyleRun(
    val length: Int,
    val style: ParagraphStyle
)

/**
 * Text layout alignment grids.
 */
data class TextGridInfo(
    var isOn: Boolean? = null,
    var show: Boolean? = null,
    var size: Float? = null,
    var leading: Float? = null,
    var color: Color? = null,
    var leadingFillColor: Color? = null,
    var alignLineHeightToGridFlags: Boolean? = null
)

/**
 * Detailed Photoshop text block properties.
 */
data class LayerTextData(
    /** Plain text string. */
    var text: String,
    /** Transform matrix components. */
    var transform: DoubleArray? = null,
    /** Anti-aliasing quality. */
    var antiAlias: AntiAlias? = null,
    /** Grid alignment options. */
    var gridding: TextGridding? = null,
    /** Writing text orientation direction. */
    var orientation: Orientation? = null,
    var index: Int? = null,
    var warp: Warp? = null,
    /** Text bounds limits inside the layout. */
    var top: Float? = null,
    var left: Float? = null,
    var bottom: Float? = null,
    var right: Float? = null,
    var gridInfo: TextGridInfo? = null,
    var useFractionalGlyphWidths: Boolean? = null,
    var style: TextStyle? = null,
    var styleRuns: List<TextStyleRun>? = null,
    var paragraphStyle: ParagraphStyle? = null,
    var paragraphStyleRuns: List<ParagraphStyleRun>? = null,
    var superscriptSize: Float? = null,
    var superscriptPosition: Float? = null,
    var subscriptSize: Float? = null,
    var subscriptPosition: Float? = null,
    var smallCapSize: Float? = null,
    /** Box or single-line layout shape. */
    var shapeType: TextShapeType? = null,
    var pointBase: FloatArray? = null,
    var boxBounds: FloatArray? = null,
    var bounds: UnitsBounds? = null,
    var boundingBox: UnitsBounds? = null
)

/**
 * Global mask information parameters.
 */
data class GlobalLayerMaskInfo(
    val overlayColorSpace: Int,
    val colorSpace1: Int,
    val colorSpace2: Int,
    val colorSpace3: Int,
    val colorSpace4: Int,
    val opacity: Float,
    val kind: Int
)

/**
 * Photoshop image resource blocks.
 */
data class ImageResources(
    var layersGroup: IntArray? = null,
    var layerGroupsEnabledId: IntArray? = null,
    var resolutionInfo: ResolutionInfo? = null
)

/**
 * PSD document resolution configuration.
 */
data class ResolutionInfo(
    var horizontalResolution: Float = 72f,
    var horizontalResolutionUnit: ResolutionUnit = ResolutionUnit.PPI,
    var widthUnit: MeasurementUnit = MeasurementUnit.INCHES,
    var verticalResolution: Float = 72f,
    var verticalResolutionUnit: ResolutionUnit = ResolutionUnit.PPI,
    var heightUnit: MeasurementUnit = MeasurementUnit.INCHES
)

/**
 * Document channel color blending limits ranges.
 */
data class BlendingRanges(
    val compositeGrayBlendSource: ByteArray,
    val compositeGraphBlendDestinationRange: ByteArray,
    val ranges: List<BlendingRange>
)

/**
 * Channel blend range threshold bytes.
 */
data class BlendingRange(
    val sourceRange: ByteArray,
    val destRange: ByteArray
)

/**
 * Base element representing a layer (or folder group) in a PSD document.
 */
data class Layer(
    /** Name of the layer. */
    var name: String? = null,
    /** Top bounds coordinate. */
    var top: Int = 0,
    /** Left bounds coordinate. */
    var left: Int = 0,
    /** Bottom bounds coordinate. */
    var bottom: Int = 0,
    /** Right bounds coordinate. */
    var right: Int = 0,
    /** Layer blending mode. */
    var blendMode: BlendMode = BlendMode.NORMAL,
    /** Opacity percentage (0.0 to 1.0). */
    var opacity: Float = 1f,
    /** Whether this layer clips to the layer below it. */
    var clipping: Boolean = false,
    /** Whether this layer is hidden. */
    var hidden: Boolean = false,
    /** Protect alpha/transparency channel from edits. */
    var transparencyProtected: Boolean = false,
    /** True if effect settings list is expanded in UI view. */
    var effectsOpen: Boolean = false,
    /** Group divider structure info if this is a boundary divider. */
    var sectionDivider: SectionDivider? = null,
    /** Folder open state. Only relevant if this layer acts as a group. */
    var opened: Boolean = true,
    /** Child layers list inside this folder group. Null for regular layers. */
    var children: MutableList<Layer>? = null,
    /** Image data pixel buffer. Null for folders or vector shapes. */
    var imageData: PixelData? = null,
    /** Canvas representation. */
    var canvas: BufferedImage? = null,
    /** Associated user mask. */
    var mask: LayerMaskData? = null,
    /** Real mask vector reference. */
    var realMask: LayerMaskData? = null,
    /** Typography text parameters if this is a text layer. */
    var text: LayerTextData? = null,
    /** ID of the layer. */
    var id: Int? = null,
    /** Source origin string. */
    var nameSource: String? = null,
    /** Multi-layer link index. */
    var linkGroup: Int? = null,
    /** Link active state. */
    var linkGroupEnabled: Boolean? = null,
    /** Custom blending range limits. */
    var blendingRanges: BlendingRanges? = null,
    /** Layer styles configuration. */
    var effects: LayerEffectsInfo? = null
)

/**
 * Root container model representing a complete PSD document.
 */
data class Psd(
    /** Width in pixels. */
    var width: Int = 0,
    /** Height in pixels. */
    var height: Int = 0,
    /** Channels count. Default is RGB + A (4). */
    var channels: Int = 4,
    /** Bit depth per channel. Default is 8. */
    var bitsPerChannel: Int = 8,
    /** Document has transparency alpha. */
    var hasAlpha: Boolean = false,
    /** Color mode configuration. */
    var colorMode: ColorMode = ColorMode.RGB,
    /** Indexed color palette definition. */
    var palette: MutableList<Rgb>? = null,
    /** Flat or nested list of layers in the document. */
    var children: MutableList<Layer> = mutableListOf(),
    /** Composite merged document image data view. */
    var imageData: PixelData? = null,
    /** Canvas viewport reference. */
    var canvas: BufferedImage? = null,
    /** Document global mask configuration. */
    var globalLayerMaskInfo: GlobalLayerMaskInfo? = null,
    /** Metadata resource blocks. */
    var imageResources: ImageResources? = null
)
