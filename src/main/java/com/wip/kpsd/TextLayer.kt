package com.wip.kpsd

object TextLayer {

    private val defaultFont = Font(name = "ArialMT", script = 0, type = 0, synthetic = 0)

    val defaultParagraphStyle = ParagraphStyle(
        justification = Justification.LEFT,
        firstLineIndent = 0f,
        startIndent = 0f,
        endIndent = 0f,
        spaceBefore = 0f,
        spaceAfter = 0f,
        autoHyphenate = true,
        hyphenatedWordSize = 6,
        preHyphen = 2,
        postHyphen = 2,
        consecutiveHyphens = 8,
        zone = 36f,
        wordSpacing = floatArrayOf(0.8f, 1.0f, 1.33f),
        letterSpacing = floatArrayOf(0f, 0f, 0f),
        glyphSpacing = floatArrayOf(1.0f, 1.0f, 1.0f),
        autoLeading = 1.2f,
        leadingType = 0,
        hanging = false,
        burasagari = false,
        kinsokuOrder = 0,
        everyLineComposer = false
    )

    val defaultStyle = TextStyle(
        font = defaultFont,
        fontSize = 12f,
        fauxBold = false,
        fauxItalic = false,
        autoLeading = true,
        leading = 0f,
        horizontalScale = 1f,
        verticalScale = 1f,
        tracking = 0f,
        autoKerning = true,
        kerning = 0f,
        baselineShift = 0f,
        fontCaps = 0,
        fontBaseline = 0,
        underline = false,
        strikethrough = false,
        ligatures = true,
        dLigatures = false,
        baselineDirection = 2,
        tsume = 0f,
        styleRunAlignment = 2,
        language = 0,
        noBreak = false,
        fillColor = Rgb(0, 0, 0),
        strokeColor = Rgb(0, 0, 0),
        fillFlag = true,
        strokeFlag = false,
        fillFirst = true,
        yUnderline = 1,
        outlineWidth = 1f,
        characterDirection = 0,
        hindiNumbers = false,
        kashida = 1f,
        diacriticPos = 2
    )

    val defaultGridInfo = TextGridInfo(
        isOn = false,
        show = false,
        size = 18f,
        leading = 22f,
        color = Rgb(255, 0, 0),
        leadingFillColor = Rgb(255, 255, 255),
        alignLineHeightToGridFlags = false
    )

    val antialias = listOf(AntiAlias.NONE, AntiAlias.SHARP, AntiAlias.CRISP, AntiAlias.STRONG, AntiAlias.SMOOTH)
    val justification = listOf(Justification.LEFT, Justification.RIGHT, Justification.CENTER)

    fun decodeColor(node: Map<String, Any?>): Color {
        val type = (node["Type"] as? Number)?.toInt() ?: 1
        val values = node["Values"] as? List<*> ?: emptyList<Any>()
        val c = values.map { (it as? Number)?.toDouble() ?: 0.0 }
        return when (type) {
            0 -> { // Grayscale
                val k = (c.getOrNull(1) ?: 0.0) * 255
                GrayscaleColor(k.toInt())
            }
            1 -> { // RGB or RGBA
                val alpha = c.getOrNull(0) ?: 1.0
                val r = (c.getOrNull(1) ?: 0.0) * 255
                val g = (c.getOrNull(2) ?: 0.0) * 255
                val b = (c.getOrNull(3) ?: 0.0) * 255
                if (alpha == 1.0) {
                    Rgb(r.toInt(), g.toInt(), b.toInt())
                } else {
                    Rgba(r.toInt(), g.toInt(), b.toInt(), (alpha * 255).toInt())
                }
            }
            2 -> { // CMYK
                val cyan = (c.getOrNull(1) ?: 0.0) * 255
                val magenta = (c.getOrNull(2) ?: 0.0) * 255
                val yellow = (c.getOrNull(3) ?: 0.0) * 255
                val black = (c.getOrNull(4) ?: 0.0) * 255
                Cmyk(cyan.toInt(), magenta.toInt(), yellow.toInt(), black.toInt())
            }
            else -> Rgb(0, 0, 0)
        }
    }

    fun encodeColor(color: Color?): Map<String, Any?> {
        if (color == null) {
            return mapOf("Type" to 1, "Values" to listOf(0.0, 0.0, 0.0, 0.0))
        }
        return when (color) {
            is GrayscaleColor -> {
                mapOf("Type" to 0, "Values" to listOf(1.0, color.k / 255.0))
            }
            is Rgba -> {
                mapOf("Type" to 1, "Values" to listOf(color.a / 255.0, color.r / 255.0, color.g / 255.0, color.b / 255.0))
            }
            is Rgb -> {
                mapOf("Type" to 1, "Values" to listOf(1.0, color.r / 255.0, color.g / 255.0, color.b / 255.0))
            }
            is Cmyk -> {
                mapOf("Type" to 2, "Values" to listOf(1.0, color.c / 255.0, color.m / 255.0, color.y / 255.0, color.k / 255.0))
            }
            else -> {
                mapOf("Type" to 1, "Values" to listOf(1.0, 0.0, 0.0, 0.0))
            }
        }
    }

    fun decodeStyle(node: Map<String, Any?>, fonts: List<Font>): TextStyle {
        val style = TextStyle()
        (node["Font"] as? Number)?.let { style.font = fonts.getOrNull(it.toInt()) }
        (node["FontSize"] as? Number)?.let { style.fontSize = it.toFloat() }
        (node["FauxBold"] as? Boolean)?.let { style.fauxBold = it }
        (node["FauxItalic"] as? Boolean)?.let { style.fauxItalic = it }
        (node["AutoLeading"] as? Boolean)?.let { style.autoLeading = it }
        (node["Leading"] as? Number)?.let { style.leading = it.toFloat() }
        (node["HorizontalScale"] as? Number)?.let { style.horizontalScale = it.toFloat() }
        (node["VerticalScale"] as? Number)?.let { style.verticalScale = it.toFloat() }
        (node["Tracking"] as? Number)?.let { style.tracking = it.toFloat() }
        (node["AutoKerning"] as? Boolean)?.let { style.autoKerning = it }
        (node["Kerning"] as? Number)?.let { style.kerning = it.toFloat() }
        (node["BaselineShift"] as? Number)?.let { style.baselineShift = it.toFloat() }
        (node["FontCaps"] as? Number)?.let { style.fontCaps = it.toInt() }
        (node["FontBaseline"] as? Number)?.let { style.fontBaseline = it.toInt() }
        (node["Underline"] as? Boolean)?.let { style.underline = it }
        (node["Strikethrough"] as? Boolean)?.let { style.strikethrough = it }
        (node["Ligatures"] as? Boolean)?.let { style.ligatures = it }
        (node["DLigatures"] as? Boolean)?.let { style.dLigatures = it }
        (node["BaselineDirection"] as? Number)?.let { style.baselineDirection = it.toInt() }
        (node["Tsume"] as? Number)?.let { style.tsume = it.toFloat() }
        (node["StyleRunAlignment"] as? Number)?.let { style.styleRunAlignment = it.toInt() }
        (node["Language"] as? Number)?.let { style.language = it.toInt() }
        (node["NoBreak"] as? Boolean)?.let { style.noBreak = it }
        (node["FillColor"] as? Map<*, *>)?.let {
            @Suppress("UNCHECKED_CAST")
            style.fillColor = decodeColor(it as Map<String, Any?>)
        }
        (node["StrokeColor"] as? Map<*, *>)?.let {
            @Suppress("UNCHECKED_CAST")
            style.strokeColor = decodeColor(it as Map<String, Any?>)
        }
        (node["FillFlag"] as? Boolean)?.let { style.fillFlag = it }
        (node["StrokeFlag"] as? Boolean)?.let { style.strokeFlag = it }
        (node["FillFirst"] as? Boolean)?.let { style.fillFirst = it }
        (node["YUnderline"] as? Number)?.let { style.yUnderline = it.toInt() }
        (node["OutlineWidth"] as? Number)?.let { style.outlineWidth = it.toFloat() }
        (node["CharacterDirection"] as? Number)?.let { style.characterDirection = it.toInt() }
        (node["HindiNumbers"] as? Boolean)?.let { style.hindiNumbers = it }
        (node["Kashida"] as? Number)?.let { style.kashida = it.toFloat() }
        (node["DiacriticPos"] as? Number)?.let { style.diacriticPos = it.toInt() }
        return style
    }

    fun encodeStyle(style: TextStyle, fonts: MutableList<Font>): Map<String, Any?> {
        val node = mutableMapOf<String, Any?>()
        style.font?.let { font ->
            var idx = fonts.indexOf(font)
            if (idx == -1) {
                idx = fonts.size
                fonts.add(font)
            }
            node["Font"] = idx
        }
        style.fontSize?.let { node["FontSize"] = it }
        style.fauxBold?.let { node["FauxBold"] = it }
        style.fauxItalic?.let { node["FauxItalic"] = it }
        style.autoLeading?.let { node["AutoLeading"] = it }
        style.leading?.let { node["Leading"] = it }
        style.horizontalScale?.let { node["HorizontalScale"] = it }
        style.verticalScale?.let { node["VerticalScale"] = it }
        style.tracking?.let { node["Tracking"] = it }
        style.autoKerning?.let { node["AutoKerning"] = it }
        style.kerning?.let { node["Kerning"] = it }
        style.baselineShift?.let { node["BaselineShift"] = it }
        style.fontCaps?.let { node["FontCaps"] = it }
        style.fontBaseline?.let { node["FontBaseline"] = it }
        style.underline?.let { node["Underline"] = it }
        style.strikethrough?.let { node["Strikethrough"] = it }
        style.ligatures?.let { node["Ligatures"] = it }
        style.dLigatures?.let { node["DLigatures"] = it }
        style.baselineDirection?.let { node["BaselineDirection"] = it }
        style.tsume?.let { node["Tsume"] = it }
        style.styleRunAlignment?.let { node["StyleRunAlignment"] = it }
        style.language?.let { node["Language"] = it }
        style.noBreak?.let { node["NoBreak"] = it }
        style.fillColor?.let { node["FillColor"] = encodeColor(it) }
        style.strokeColor?.let { node["StrokeColor"] = encodeColor(it) }
        style.fillFlag?.let { node["FillFlag"] = it }
        style.strokeFlag?.let { node["StrokeFlag"] = it }
        style.fillFirst?.let { node["FillFirst"] = it }
        style.yUnderline?.let { node["YUnderline"] = it }
        style.outlineWidth?.let { node["OutlineWidth"] = it }
        style.characterDirection?.let { node["CharacterDirection"] = it }
        style.hindiNumbers?.let { node["HindiNumbers"] = it }
        style.kashida?.let { node["Kashida"] = it }
        style.diacriticPos?.let { node["DiacriticPos"] = it }
        return node
    }

    fun decodeParagraphStyle(node: Map<String, Any?>, fonts: List<Font>): ParagraphStyle {
        val ps = ParagraphStyle()
        (node["Justification"] as? Number)?.let { ps.justification = justification.getOrNull(it.toInt()) }
        (node["FirstLineIndent"] as? Number)?.let { ps.firstLineIndent = it.toFloat() }
        (node["StartIndent"] as? Number)?.let { ps.startIndent = it.toFloat() }
        (node["EndIndent"] as? Number)?.let { ps.endIndent = it.toFloat() }
        (node["SpaceBefore"] as? Number)?.let { ps.spaceBefore = it.toFloat() }
        (node["SpaceAfter"] as? Number)?.let { ps.spaceAfter = it.toFloat() }
        (node["AutoHyphenate"] as? Boolean)?.let { ps.autoHyphenate = it }
        (node["HyphenatedWordSize"] as? Number)?.let { ps.hyphenatedWordSize = it.toInt() }
        (node["PreHyphen"] as? Number)?.let { ps.preHyphen = it.toInt() }
        (node["PostHyphen"] as? Number)?.let { ps.postHyphen = it.toInt() }
        (node["ConsecutiveHyphens"] as? Number)?.let { ps.consecutiveHyphens = it.toInt() }
        (node["Zone"] as? Number)?.let { ps.zone = it.toFloat() }
        (node["WordSpacing"] as? List<*>)?.let { list ->
            ps.wordSpacing = FloatArray(list.size) { (list[it] as Number).toFloat() }
        }
        (node["LetterSpacing"] as? List<*>)?.let { list ->
            ps.letterSpacing = FloatArray(list.size) { (list[it] as Number).toFloat() }
        }
        (node["GlyphSpacing"] as? List<*>)?.let { list ->
            ps.glyphSpacing = FloatArray(list.size) { (list[it] as Number).toFloat() }
        }
        (node["AutoLeading"] as? Number)?.let { ps.autoLeading = it.toFloat() }
        (node["LeadingType"] as? Number)?.let { ps.leadingType = it.toInt() }
        (node["Hanging"] as? Boolean)?.let { ps.hanging = it }
        (node["Burasagari"] as? Boolean)?.let { ps.burasagari = it }
        (node["KinsokuOrder"] as? Number)?.let { ps.kinsokuOrder = it.toInt() }
        (node["EveryLineComposer"] as? Boolean)?.let { ps.everyLineComposer = it }
        return ps
    }

    fun encodeParagraphStyle(ps: ParagraphStyle, fonts: List<Font>): Map<String, Any?> {
        val node = mutableMapOf<String, Any?>()
        ps.justification?.let { node["Justification"] = maxOf(0, justification.indexOf(it)) }
        ps.firstLineIndent?.let { node["FirstLineIndent"] = it }
        ps.startIndent?.let { node["StartIndent"] = it }
        ps.endIndent?.let { node["EndIndent"] = it }
        ps.spaceBefore?.let { node["SpaceBefore"] = it }
        ps.spaceAfter?.let { node["SpaceAfter"] = it }
        ps.autoHyphenate?.let { node["AutoHyphenate"] = it }
        ps.hyphenatedWordSize?.let { node["HyphenatedWordSize"] = it }
        ps.preHyphen?.let { node["PreHyphen"] = it }
        ps.postHyphen?.let { node["PostHyphen"] = it }
        ps.consecutiveHyphens?.let { node["ConsecutiveHyphens"] = it }
        ps.zone?.let { node["Zone"] = it }
        ps.wordSpacing?.let { node["WordSpacing"] = it.toList() }
        ps.letterSpacing?.let { node["LetterSpacing"] = it.toList() }
        ps.glyphSpacing?.let { node["GlyphSpacing"] = it.toList() }
        ps.autoLeading?.let { node["AutoLeading"] = it }
        ps.leadingType?.let { node["LeadingType"] = it }
        ps.hanging?.let { node["Hanging"] = it }
        ps.burasagari?.let { node["Burasagari"] = it }
        ps.kinsokuOrder?.let { node["KinsokuOrder"] = it }
        ps.everyLineComposer?.let { node["EveryLineComposer"] = it }
        return node
    }

    fun decodeEngineData(engineDict: Map<String, Any?>, resourceDict: Map<String, Any?>): LayerTextData {
        val editor = engineDict["Editor"] as? Map<*, *>
        val rawText = (editor?.get("Text") as? String) ?: ""
        var text = rawText.replace("\r", "\n")
        var removedCharacters = 0
        while (text.endsWith("\n")) {
            text = text.substring(0, text.length - 1)
            removedCharacters++
        }

        val fontSet = (resourceDict["FontSet"] as? List<*>) ?: emptyList<Any>()
        val fonts = fontSet.map {
            val f = it as Map<*, *>
            Font(
                name = (f["Name"] as? String) ?: "ArialMT",
                script = (f["Script"] as? Number)?.toInt(),
                type = (f["Type"] as? Number)?.toInt(),
                synthetic = (f["Synthetic"] as? Number)?.toInt()
            )
        }

        val styleRun = engineDict["StyleRun"] as? Map<*, *>
        val styleRunArray = (styleRun?.get("RunArray") as? List<*>) ?: emptyList<Any>()
        val styleRunLengthArray = (styleRun?.get("RunLengthArray") as? List<*>) ?: emptyList<Any>()

        val styleRuns = mutableListOf<TextStyleRun>()
        for (i in styleRunArray.indices) {
            val s = styleRunArray[i] as Map<*, *>
            val len = (styleRunLengthArray.getOrNull(i) as? Number)?.toInt() ?: 0
            val stylesheet = s["StyleSheet"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val styleData = stylesheet?.get("StyleSheetData") as? Map<String, Any?>
            if (styleData != null) {
                val decoded = decodeStyle(styleData, fonts)
                if (decoded.font == null && fonts.isNotEmpty()) {
                    decoded.font = fonts[0]
                }
                styleRuns.add(TextStyleRun(len, decoded))
            }
        }

        var tempRemoved = removedCharacters
        while (styleRuns.isNotEmpty() && tempRemoved > 0) {
            val lastRun = styleRuns.last()
            val newLen = lastRun.length - 1
            if (newLen <= 0) {
                styleRuns.removeAt(styleRuns.size - 1)
            } else {
                styleRuns[styleRuns.size - 1] = lastRun.copy(length = newLen)
            }
            tempRemoved--
        }

        val paragraphRun = engineDict["ParagraphRun"] as? Map<*, *>
        val paragraphRunArray = (paragraphRun?.get("RunArray") as? List<*>) ?: emptyList<Any>()
        val paragraphRunLengthArray = (paragraphRun?.get("RunLengthArray") as? List<*>) ?: emptyList<Any>()

        val paragraphStyleRuns = mutableListOf<ParagraphStyleRun>()
        for (i in paragraphRunArray.indices) {
            val p = paragraphRunArray[i] as Map<*, *>
            val len = (paragraphRunLengthArray.getOrNull(i) as? Number)?.toInt() ?: 0
            val stylesheet = p["ParagraphSheet"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val properties = stylesheet?.get("Properties") as? Map<String, Any?>
            if (properties != null) {
                paragraphStyleRuns.add(ParagraphStyleRun(len, decodeParagraphStyle(properties, fonts)))
            }
        }

        tempRemoved = removedCharacters
        while (paragraphStyleRuns.isNotEmpty() && tempRemoved > 0) {
            val lastRun = paragraphStyleRuns.last()
            val newLen = lastRun.length - 1
            if (newLen <= 0) {
                paragraphStyleRuns.removeAt(paragraphStyleRuns.size - 1)
            } else {
                paragraphStyleRuns[paragraphStyleRuns.size - 1] = lastRun.copy(length = newLen)
            }
            tempRemoved--
        }

        val baseStyle = TextStyle()
        val finalStyleRuns = if (styleRuns.isNotEmpty()) {
            deduplicateStyle(baseStyle, styleRuns)
        } else styleRuns

        val baseParagraphStyle = ParagraphStyle()
        val finalParagraphStyleRuns = if (paragraphStyleRuns.isNotEmpty()) {
            deduplicateParagraphStyle(baseParagraphStyle, paragraphStyleRuns)
        } else paragraphStyleRuns

        val gridInfoNode = engineDict["GridInfo"] as? Map<*, *>
        val gridInfo = TextGridInfo(
            isOn = gridInfoNode?.get("GridIsOn") as? Boolean,
            show = gridInfoNode?.get("ShowGrid") as? Boolean,
            size = (gridInfoNode?.get("GridSize") as? Number)?.toFloat(),
            leading = (gridInfoNode?.get("GridLeading") as? Number)?.toFloat(),
            color = (gridInfoNode?.get("GridColor") as? Map<*, *>)?.let { 
                @Suppress("UNCHECKED_CAST")
                decodeColor(it as Map<String, Any?>) 
            },
            leadingFillColor = (gridInfoNode?.get("GridLeadingFillColor") as? Map<*, *>)?.let {
                @Suppress("UNCHECKED_CAST")
                decodeColor(it as Map<String, Any?>)
            },
            alignLineHeightToGridFlags = gridInfoNode?.get("AlignLineHeightToGridFlags") as? Boolean
        )

        val superscriptSize = (resourceDict["SuperscriptSize"] as? Number)?.toFloat()
        val superscriptPosition = (resourceDict["SuperscriptPosition"] as? Number)?.toFloat()
        val subscriptSize = (resourceDict["SubscriptSize"] as? Number)?.toFloat()
        val subscriptPosition = (resourceDict["SubscriptPosition"] as? Number)?.toFloat()
        val smallCapSize = (resourceDict["SmallCapSize"] as? Number)?.toFloat()

        val rendered = engineDict["Rendered"] as? Map<*, *>
        val shapes = rendered?.get("Shapes") as? Map<*, *>
        val children = shapes?.get("Children") as? List<*>
        val firstChild = children?.getOrNull(0) as? Map<*, *>
        val cookie = firstChild?.get("Cookie") as? Map<*, *>
        val photoshop = cookie?.get("Photoshop") as? Map<*, *>
        
        val shapeType = if (photoshop != null) {
            val st = (photoshop["ShapeType"] as? Number)?.toInt()
            if (st == 1) TextShapeType.BOX else TextShapeType.POINT
        } else null
        
        val pointBase = (photoshop?.get("PointBase") as? List<*>)?.let { list ->
            FloatArray(list.size) { (list[it] as Number).toFloat() }
        }
        val boxBounds = (photoshop?.get("BoxBounds") as? List<*>)?.let { list ->
            FloatArray(list.size) { (list[it] as Number).toFloat() }
        }

        return LayerTextData(
            text = text,
            style = baseStyle,
            styleRuns = if (finalStyleRuns.isEmpty()) null else finalStyleRuns,
            paragraphStyle = baseParagraphStyle,
            paragraphStyleRuns = if (finalParagraphStyleRuns.isEmpty()) null else finalParagraphStyleRuns,
            gridInfo = gridInfo,
            antiAlias = antialias.getOrNull((engineDict["AntiAlias"] as? Number)?.toInt() ?: 1),
            useFractionalGlyphWidths = engineDict["UseFractionalGlyphWidths"] as? Boolean,
            superscriptSize = superscriptSize,
            superscriptPosition = superscriptPosition,
            subscriptSize = subscriptSize,
            subscriptPosition = subscriptPosition,
            smallCapSize = smallCapSize,
            shapeType = shapeType,
            pointBase = pointBase,
            boxBounds = boxBounds
        )
    }

    fun encodeEngineData(data: LayerTextData): Map<String, Any?> {
        val text = data.text.replace(Regex("\\r?\\n"), "\r") + "\r"
        val fonts = mutableListOf<Font>(
            Font(name = "AdobeInvisFont", script = 0, type = 0, synthetic = 0)
        )
        val defFont = data.style?.font ?: data.styleRuns?.find { it.style.font != null }?.style?.font ?: defaultFont

        val styleRunArray = mutableListOf<Map<String, Any?>>()
        val styleRunLengthArray = mutableListOf<Int>()
        val styleRuns = data.styleRuns
        if (styleRuns != null && styleRuns.isNotEmpty()) {
            var leftLength = text.length
            for (run in styleRuns) {
                var runLength = Math.min(run.length, leftLength)
                leftLength -= runLength
                if (runLength <= 0) continue
                if (leftLength == 1 && run == styleRuns.last()) {
                    runLength++
                    leftLength--
                }
                styleRunLengthArray.add(runLength)
                val runStyle = run.style
                val target = mergeStyle(TextStyle(font = defFont), data.style)
                val finalStyle = mergeStyle(target, runStyle)
                styleRunArray.add(
                    mapOf(
                        "StyleSheet" to mapOf("StyleSheetData" to encodeStyle(finalStyle, fonts))
                    )
                )
            }
            if (leftLength > 0) {
                styleRunLengthArray.add(leftLength)
                styleRunArray.add(
                    mapOf(
                        "StyleSheet" to mapOf("StyleSheetData" to encodeStyle(mergeStyle(TextStyle(font = defFont), data.style), fonts))
                    )
                )
            }
        } else {
            styleRunLengthArray.add(text.length)
            styleRunArray.add(
                mapOf(
                    "StyleSheet" to mapOf("StyleSheetData" to encodeStyle(mergeStyle(TextStyle(font = defFont), data.style), fonts))
                )
            )
        }

        val paragraphRunArray = mutableListOf<Map<String, Any?>>()
        val paragraphRunLengthArray = mutableListOf<Int>()
        val pRuns = data.paragraphStyleRuns
        if (pRuns != null && pRuns.isNotEmpty()) {
            var leftLength = text.length
            for (run in pRuns) {
                var runLength = Math.min(run.length, leftLength)
                leftLength -= runLength
                if (runLength <= 0) continue
                if (leftLength == 1 && run == pRuns.last()) {
                    runLength++
                    leftLength--
                }
                paragraphRunLengthArray.add(runLength)
                val runPs = run.style
                val target = mergeParagraphStyle(defaultParagraphStyle, data.paragraphStyle)
                val finalPs = mergeParagraphStyle(target, runPs)
                paragraphRunArray.add(
                    mapOf(
                        "ParagraphSheet" to mapOf("DefaultStyleSheet" to 0, "Properties" to encodeParagraphStyle(finalPs, fonts))
                    )
                )
            }
            if (leftLength > 0) {
                paragraphRunLengthArray.add(leftLength)
                paragraphRunArray.add(
                    mapOf(
                        "ParagraphSheet" to mapOf("DefaultStyleSheet" to 0, "Properties" to encodeParagraphStyle(mergeParagraphStyle(defaultParagraphStyle, data.paragraphStyle), fonts))
                    )
                )
            }
        } else {
            var last = 0
            for (i in 0 until text.length) {
                if (text[i].code == 13) { // \r
                    paragraphRunLengthArray.add(i - last + 1)
                    paragraphRunArray.add(
                        mapOf(
                            "ParagraphSheet" to mapOf("DefaultStyleSheet" to 0, "Properties" to encodeParagraphStyle(mergeParagraphStyle(defaultParagraphStyle, data.paragraphStyle), fonts))
                        )
                    )
                    last = i + 1
                }
            }
        }

        val gridInfo = data.gridInfo ?: defaultGridInfo
        val writingDirection = if (data.orientation == Orientation.VERTICAL) 2 else 0
        val procession = if (data.orientation == Orientation.VERTICAL) 1 else 0
        val shapeType = if (data.shapeType == TextShapeType.BOX) 1 else 0

        val photoshopNode = mutableMapOf<String, Any?>("ShapeType" to shapeType)
        if (shapeType == 0) {
            photoshopNode["PointBase"] = data.pointBase?.toList() ?: listOf(0.0, 0.0)
        } else {
            photoshopNode["BoxBounds"] = data.boxBounds?.toList() ?: listOf(0.0, 0.0, 0.0, 0.0)
        }
        photoshopNode["Base"] = mapOf(
            "ShapeType" to shapeType,
            "TransformPoint0" to listOf(1.0, 0.0),
            "TransformPoint1" to listOf(0.0, 1.0),
            "TransformPoint2" to listOf(0.0, 0.0)
        )

        val styleSheetData = encodeStyle(mergeStyle(TextStyle(font = defFont), data.style), fonts)
        val defaultResources = mapOf(
            "KinsokuSet" to listOf(
                mapOf(
                    "Name" to "PhotoshopKinsokuHard",
                    "NoStart" to "、。，．・：；？！ー―’”）〕］｝〉》」』】ヽヾゝゞ々ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ゛゜?!)]},.:;℃℉¢％‰",
                    "NoEnd" to "‘“（〔［｛〈《「『【([{￥＄£＠§〒＃",
                    "Keep" to "―‥",
                    "Hanging" to "、。.,"
                ),
                mapOf(
                    "Name" to "PhotoshopKinsokuSoft",
                    "NoStart" to "、。，．・：；？！’”）〕］｝〉》」』】ヽヾゝゞ々",
                    "NoEnd" to "‘“（〔［｛〈《「『【",
                    "Keep" to "―‥",
                    "Hanging" to "、。.,"
                )
            ),
            "MojiKumiSet" to listOf(
                mapOf("InternalName" to "Photoshop6MojiKumiSet1"),
                mapOf("InternalName" to "Photoshop6MojiKumiSet2"),
                mapOf("InternalName" to "Photoshop6MojiKumiSet3"),
                mapOf("InternalName" to "Photoshop6MojiKumiSet4")
            ),
            "TheNormalParagraphSheet" to 0,
            "TheNormalStyleSheet" to 0,
            "ParagraphSheetSet" to listOf(
                mapOf(
                    "Name" to "Normal RGB",
                    "DefaultStyleSheet" to 0,
                    "Properties" to encodeParagraphStyle(mergeParagraphStyle(defaultParagraphStyle, data.paragraphStyle), fonts)
                )
            ),
            "StyleSheetSet" to listOf(
                mapOf(
                    "Name" to "Normal RGB",
                    "StyleSheetData" to styleSheetData
                )
            ),
            "FontSet" to fonts.map {
                mapOf(
                    "Name" to it.name,
                    "Script" to (it.script ?: 0),
                    "FontType" to (it.type ?: 0),
                    "Synthetic" to (it.synthetic ?: 0)
                )
            },
            "SuperscriptSize" to (data.superscriptSize ?: 0.583f),
            "SuperscriptPosition" to (data.superscriptPosition ?: 0.333f),
            "SubscriptSize" to (data.subscriptSize ?: 0.583f),
            "SubscriptPosition" to (data.subscriptPosition ?: 0.333f),
            "SmallCapSize" to (data.smallCapSize ?: 0.7f)
        )

        val engineDict = mapOf(
            "Editor" to mapOf("Text" to text),
            "ParagraphRun" to mapOf(
                "DefaultRunData" to mapOf(
                    "ParagraphSheet" to mapOf("DefaultStyleSheet" to 0, "Properties" to mapOf<String, Any?>()),
                    "Adjustments" to mapOf("Axis" to listOf(1.0, 0.0, 1.0), "XY" to listOf(0.0, 0.0))
                ),
                "RunArray" to paragraphRunArray,
                "RunLengthArray" to paragraphRunLengthArray,
                "IsJoinable" to 1
            ),
            "StyleRun" to mapOf(
                "DefaultRunData" to mapOf("StyleSheet" to mapOf("StyleSheetData" to mapOf<String, Any?>())),
                "RunArray" to styleRunArray,
                "RunLengthArray" to styleRunLengthArray,
                "IsJoinable" to 2
            ),
            "GridInfo" to mapOf(
                "GridIsOn" to (gridInfo.isOn ?: false),
                "ShowGrid" to (gridInfo.show ?: false),
                "GridSize" to (gridInfo.size ?: 18f),
                "GridLeading" to (gridInfo.leading ?: 22f),
                "GridColor" to encodeColor(gridInfo.color),
                "GridLeadingFillColor" to encodeColor(gridInfo.leadingFillColor),
                "AlignLineHeightToGridFlags" to (gridInfo.alignLineHeightToGridFlags ?: false)
            ),
            "AntiAlias" to maxOf(0, antialias.indexOf(data.antiAlias ?: AntiAlias.SHARP)),
            "UseFractionalGlyphWidths" to (data.useFractionalGlyphWidths ?: true),
            "Rendered" to mapOf(
                "Version" to 1,
                "Shapes" to mapOf(
                    "WritingDirection" to writingDirection,
                    "Children" to listOf(
                        mapOf(
                            "ShapeType" to shapeType,
                            "Procession" to procession,
                            "Lines" to mapOf("WritingDirection" to writingDirection, "Children" to emptyList<Any>()),
                            "Cookie" to mapOf("Photoshop" to photoshopNode)
                        )
                    )
                )
            )
        )

        return mapOf(
            "EngineDict" to engineDict,
            "ResourceDict" to defaultResources,
            "DocumentResources" to defaultResources
        )
    }

    private fun mergeStyle(base: TextStyle, overlay: TextStyle?): TextStyle {
        if (overlay == null) return base
        val result = base.copy()
        overlay.font?.let { result.font = it }
        overlay.fontSize?.let { result.fontSize = it }
        overlay.fauxBold?.let { result.fauxBold = it }
        overlay.fauxItalic?.let { result.fauxItalic = it }
        overlay.autoLeading?.let { result.autoLeading = it }
        overlay.leading?.let { result.leading = it }
        overlay.horizontalScale?.let { result.horizontalScale = it }
        overlay.verticalScale?.let { result.verticalScale = it }
        overlay.tracking?.let { result.tracking = it }
        overlay.autoKerning?.let { result.autoKerning = it }
        overlay.kerning?.let { result.kerning = it }
        overlay.baselineShift?.let { result.baselineShift = it }
        overlay.fontCaps?.let { result.fontCaps = it }
        overlay.fontBaseline?.let { result.fontBaseline = it }
        overlay.underline?.let { result.underline = it }
        overlay.strikethrough?.let { result.strikethrough = it }
        overlay.ligatures?.let { result.ligatures = it }
        overlay.dLigatures?.let { result.dLigatures = it }
        overlay.baselineDirection?.let { result.baselineDirection = it }
        overlay.tsume?.let { result.tsume = it }
        overlay.styleRunAlignment?.let { result.styleRunAlignment = it }
        overlay.language?.let { result.language = it }
        overlay.noBreak?.let { result.noBreak = it }
        overlay.fillColor?.let { result.fillColor = it }
        overlay.strokeColor?.let { result.strokeColor = it }
        overlay.fillFlag?.let { result.fillFlag = it }
        overlay.strokeFlag?.let { result.strokeFlag = it }
        overlay.fillFirst?.let { result.fillFirst = it }
        overlay.yUnderline?.let { result.yUnderline = it }
        overlay.outlineWidth?.let { result.outlineWidth = it }
        overlay.characterDirection?.let { result.characterDirection = it }
        overlay.hindiNumbers?.let { result.hindiNumbers = it }
        overlay.kashida?.let { result.kashida = it }
        overlay.diacriticPos?.let { result.diacriticPos = it }
        return result
    }

    private fun mergeParagraphStyle(base: ParagraphStyle, overlay: ParagraphStyle?): ParagraphStyle {
        if (overlay == null) return base
        val result = base.copy()
        overlay.justification?.let { result.justification = it }
        overlay.firstLineIndent?.let { result.firstLineIndent = it }
        overlay.startIndent?.let { result.startIndent = it }
        overlay.endIndent?.let { result.endIndent = it }
        overlay.spaceBefore?.let { result.spaceBefore = it }
        overlay.spaceAfter?.let { result.spaceAfter = it }
        overlay.autoHyphenate?.let { result.autoHyphenate = it }
        overlay.hyphenatedWordSize?.let { result.hyphenatedWordSize = it }
        overlay.preHyphen?.let { result.preHyphen = it }
        overlay.postHyphen?.let { result.postHyphen = it }
        overlay.consecutiveHyphens?.let { result.consecutiveHyphens = it }
        overlay.zone?.let { result.zone = it }
        overlay.wordSpacing?.let { result.wordSpacing = it }
        overlay.letterSpacing?.let { result.letterSpacing = it }
        overlay.glyphSpacing?.let { result.glyphSpacing = it }
        overlay.autoLeading?.let { result.autoLeading = it }
        overlay.leadingType?.let { result.leadingType = it }
        overlay.hanging?.let { result.hanging = it }
        overlay.burasagari?.let { result.burasagari = it }
        overlay.kinsokuOrder?.let { result.kinsokuOrder = it }
        overlay.everyLineComposer?.let { result.everyLineComposer = it }
        return result
    }

    fun deduplicateStyle(base: TextStyle, runs: List<TextStyleRun>): List<TextStyleRun> {
        fun <V> isIdentical(getter: (TextStyle) -> V): Boolean {
            val first = getter(runs[0].style)
            return runs.all { getter(it.style) == first }
        }

        // Font
        if (isIdentical { it.font }) {
            base.font = runs[0].style.font
            runs.forEach { it.style.font = null }
        }
        // Size
        if (isIdentical { it.fontSize }) {
            base.fontSize = runs[0].style.fontSize
            runs.forEach { it.style.fontSize = null }
        }
        
        return runs
    }

    fun deduplicateParagraphStyle(base: ParagraphStyle, runs: List<ParagraphStyleRun>): List<ParagraphStyleRun> {
        fun <V> isIdentical(getter: (ParagraphStyle) -> V): Boolean {
            val first = getter(runs[0].style)
            return runs.all { getter(it.style) == first }
        }

        // Justification
        if (isIdentical { it.justification }) {
            base.justification = runs[0].style.justification
            runs.forEach { it.style.justification = null }
        }

        return runs
    }
}
