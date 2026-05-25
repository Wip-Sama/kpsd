package com.wip.kpsd

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class PsdReader(val bytes: ByteArray) {
    var offset: Int = 0
    var large: Boolean = false

    private val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

    fun readUint8(): Int {
        return bytes[offset++].toInt() and 0xff
    }

    fun peekUint8(): Int {
        return bytes[offset].toInt() and 0xff
    }

    fun readInt16(): Int {
        val v = byteBuffer.getShort(offset).toInt()
        offset += 2
        return v
    }

    fun readUint16(): Int {
        return readInt16() and 0xffff
    }

    fun readUint16LE(): Int {
        val b1 = readUint8()
        val b2 = readUint8()
        return (b2 shl 8) or b1
    }

    fun readInt32(): Int {
        val v = byteBuffer.getInt(offset)
        offset += 4
        return v
    }

    fun readInt32LE(): Int {
        val b1 = readUint8()
        val b2 = readUint8()
        val b3 = readUint8()
        val b4 = readUint8()
        return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
    }

    fun readUint32(): Long {
        return readInt32().toLong() and 0xffffffffL
    }

    fun readFloat32(): Float {
        val v = byteBuffer.getFloat(offset)
        offset += 4
        return v
    }

    fun readFloat64(): Double {
        val v = byteBuffer.getDouble(offset)
        offset += 8
        return v
    }

    fun readBytes(length: Int): ByteArray {
        val result = bytes.copyOfRange(offset, offset + length)
        offset += length
        return result
    }

    fun readSignature(): String {
        return String(readBytes(4), StandardCharsets.US_ASCII)
    }

    fun readPascalString(round: Int): String {
        val length = readUint8()
        val result = String(readBytes(length), StandardCharsets.US_ASCII)
        var total = length + 1
        while (total % round != 0) {
            readUint8()
            total++
        }
        return result
    }

    fun readUnicodeString(): String {
        val length = readUint32().toInt()
        val chars = CharArray(length)
        for (i in 0 until length) {
            chars[i] = readUint16().toChar()
        }
        return String(chars).trimEnd('\u0000')
    }

    fun readAsciiString(length: Int): String {
        val bytes = readBytes(length)
        return String(bytes, StandardCharsets.US_ASCII)
    }

    fun skipBytes(length: Int) {
        offset += length
    }

    fun checkSignature(expected: String, message: String? = null) {
        val sig = readSignature()
        if (sig != expected) {
            throw IllegalStateException(message ?: "Expected signature $expected but got $sig")
        }
    }

    fun <T> readSection(round: Int, eightBytes: Boolean = false, func: (left: () -> Int) -> T): T? {
        val length = if (eightBytes) readUint64() else readUint32()
        if (length == 0L) return null
        val start = offset
        val end = offset + length.toInt()
        val result = func { end - offset }
        if (offset != end) {
            offset = end
        }
        var totalLen = length.toInt()
        while (totalLen % round != 0) {
            totalLen++
        }
        offset = start + totalLen
        return result
    }

    data class ChannelInfo(val id: ChannelID, val length: Int)

    fun readPsd(): Psd {
        checkSignature("8BPS")
        val version = readInt16()
        large = version == 2
        skipBytes(6) // reserved
        val channels = readInt16()
        val height = readInt32()
        val width = readInt32()
        val depth = readInt16()
        val colorModeValue = readInt16()
        val colorMode = ColorMode.fromInt(colorModeValue)

        val psd = Psd(width = width, height = height, channels = channels, bitsPerChannel = depth, colorMode = colorMode)

        // color mode data
        readSection(1) {
            // skip
        }

        // image resources
        readSection(1) { left ->
            while (left() >= 12) {
                val sig = readSignature()
                if (sig != "8BIM") break
                val id = readInt16()
                readPascalString(2) // name
                readSection(2) {
                    // Resource data - handle resolution etc
                }
            }
        }

        // layer and mask info
        readSection(2, eightBytes = large) {
            readLayerInfo(psd)
            readGlobalLayerMaskInfo()
            // readAdditionalLayerInfo(psd, psd)
        }

        // composite image data
        readCompositeImageData(psd)

        return psd
    }

    private fun readLayerInfo(psd: Psd) {
        readSection(4, eightBytes = large) { left ->
            val count = readInt16()
            val absCount = Math.abs(count)
            psd.hasAlpha = count < 0

            val layers = mutableListOf<Layer>()
            val channelsInfo = mutableListOf<List<ChannelInfo>>()

            for (i in 0 until absCount) {
                val (layer, info) = readLayerRecord(psd)
                layers.add(layer)
                channelsInfo.add(info)
            }

            for (i in 0 until absCount) {
                readLayerChannelImageData(psd, layers[i], channelsInfo[i])
            }

            // Reconstruct hierarchy
            val stack = mutableListOf<Layer>()
            val root = Layer() // dummy root layer to hold children
            root.children = mutableListOf()
            stack.add(root)

            for (i in layers.indices.reversed()) {
                val l = layers[i]
                val type = l.sectionDivider?.type ?: SectionDividerType.Other

                if (type == SectionDividerType.OpenFolder || type == SectionDividerType.ClosedFolder) {
                    l.opened = type == SectionDividerType.OpenFolder
                    l.children = mutableListOf()

                    l.sectionDivider?.key?.let { key ->
                        PsdHelpers.toBlendMode[key]?.let { l.blendMode = it }
                    }

                    stack.last().children!!.add(0, l)
                    stack.add(l)
                } else if (type == SectionDividerType.BoundingSectionDivider) {
                    if (stack.size > 1) {
                        stack.removeAt(stack.size - 1)
                    }
                } else {
                    stack.last().children!!.add(0, l)
                }
            }

            psd.children = root.children ?: mutableListOf()
        }
    }

    private fun readLayerRecord(psd: Psd): Pair<Layer, List<ChannelInfo>> {
        val top = readInt32()
        val left = readInt32()
        val bottom = readInt32()
        val right = readInt32()
        val channelsCount = readInt16()
        val info = mutableListOf<ChannelInfo>()
        for (i in 0 until channelsCount) {
            val id = readInt16()
            val length = if (large) readUint64().toInt() else readInt32()
            info.add(ChannelInfo(ChannelID.fromInt(id), length))
        }

        checkSignature("8BIM")
        val blendMode = readSignature()
        val opacity = readUint8() / 255f
        val clipping = readUint8()
        val flags = readUint8()
        readUint8() // filler

        val layer = Layer(
            top = top,
            left = left,
            bottom = bottom,
            right = right,
            blendMode = PsdHelpers.toBlendMode[blendMode] ?: BlendMode.NORMAL,
            opacity = opacity,
            hidden = (flags and 0x02) != 0,
            transparencyProtected = (flags and 0x01) != 0,
            effectsOpen = (flags and 0x20) != 0
        )

        readSection(1) { left ->
            readLayerMaskData(layer)
            readLayerBlendingRanges(layer)
            layer.name = readPascalString(4)

            while (left() >= 12) {
                readAdditionalLayerInfo(psd, layer)
            }
        }

        return Pair(layer, info)
    }

    private fun readLayerMaskData(layer: Layer) {
        readSection(1) { left ->
            if (left() > 0) {
                val mask = LayerMaskData()
                mask.top = readInt32()
                mask.left = readInt32()
                mask.bottom = readInt32()
                mask.right = readInt32()
                mask.defaultColor = readUint8()
                val flags = readUint8()
                mask.positionRelativeToLayer = (flags and 1) != 0
                mask.disabled = (flags and 2) != 0
                mask.fromVectorData = (flags and 8) != 0

                layer.mask = mask

                if (left() >= 18) {
                    val realMask = LayerMaskData()
                    val realFlags = readUint8()
                    realMask.positionRelativeToLayer = (realFlags and 1) != 0
                    realMask.disabled = (realFlags and 2) != 0
                    realMask.fromVectorData = (realFlags and 8) != 0
                    realMask.defaultColor = readUint8()
                    realMask.top = readInt32()
                    realMask.left = readInt32()
                    realMask.bottom = readInt32()
                    realMask.right = readInt32()
                    layer.realMask = realMask
                }
                
                // Read parameters if present (flags bit 4: MaskHasParametersAppliedToIt = 16)
                if ((flags and 16) != 0 && left() > 0) {
                    val params = readUint8()
                    if ((params and 1) != 0) mask.userMaskDensity = readUint8() / 255f
                    if ((params and 2) != 0) mask.userMaskFeather = readFloat64()
                    if ((params and 4) != 0) mask.vectorMaskDensity = readUint8() / 255f
                    if ((params and 8) != 0) mask.vectorMaskFeather = readFloat64()
                }
            }
        }
    }

    private fun readLayerBlendingRanges(layer: Layer) {
        readSection(1) { left ->
            if (left() >= 8) {
                val compositeGrayBlendSource = readBytes(4)
                val compositeGraphBlendDestinationRange = readBytes(4)
                val rangesList = mutableListOf<BlendingRange>()
                while (left() >= 8) {
                    val sourceRange = readBytes(4)
                    val destRange = readBytes(4)
                    rangesList.add(BlendingRange(sourceRange, destRange))
                }
                layer.blendingRanges = BlendingRanges(
                    compositeGrayBlendSource = compositeGrayBlendSource,
                    compositeGraphBlendDestinationRange = compositeGraphBlendDestinationRange,
                    ranges = rangesList
                )
            }
        }
    }

    fun readAdditionalLayerInfo(psd: Psd, target: Any) {
        val sig = readSignature()
        if (sig != "8BIM" && sig != "8B64") {
            // println("Invalid signature at $offset: $sig")
            throw IllegalStateException("Invalid signature: '$sig' in additional layer info at $offset")
        }
        val key = readSignature()
        val u64 = sig == "8B64" || (large && key in listOf("LMsk", "Lr16", "Lr32", "Layr", "Mt16", "Mt32", "Mtrn", "Alph", "FMsk", "lnk2", "FEid", "FXid", "PxSD", "cinf"))

        val sectionLen = if (u64) readUint64().toInt() else readInt32()
        val sectionEnd = offset + sectionLen

        if (key == "TySh" && target is Layer) {
            val version = readInt16()
            if (version != 1) throw IllegalStateException("Invalid TySh version: $version at $offset")

            val transform = DoubleArray(6) { readFloat64() }
            val textVersion = readInt16()
            if (textVersion != 50) throw IllegalStateException("Invalid TySh text version $textVersion at $offset")

            val textDesc = PsdDescriptor.readVersionAndDescriptor(this)
            
            val warpVersion = readInt16()
            val warpDesc = PsdDescriptor.readVersionAndDescriptor(this)
            
            val leftVal = readFloat32()
            val topVal = readFloat32()
            val rightVal = readFloat32()
            val bottomVal = readFloat32()

            val textVal = (textDesc.properties["Txt "] as? TextValue)?.value ?: ""
            val rawEngineData = textDesc.properties["EngineData"] as? RawDataValue

            val textLayout = if (rawEngineData != null) {
                val parsed = EngineData.parseEngineData(rawEngineData.data)
                if (parsed != null) {
                    @Suppress("UNCHECKED_CAST")
                    val engineDict = parsed["EngineDict"] as? Map<String, Any?> ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val resourceDict = parsed["ResourceDict"] as? Map<String, Any?> ?: emptyMap()
                    TextLayer.decodeEngineData(engineDict, resourceDict)
                } else {
                    LayerTextData(text = textVal)
                }
            } else {
                LayerTextData(text = textVal)
            }

            textLayout.transform = transform
            textLayout.left = leftVal
            textLayout.top = topVal
            textLayout.right = rightVal
            textLayout.bottom = bottomVal
            textLayout.text = textVal
            textLayout.warp = parseWarpDescriptor(warpDesc)
            
            target.text = textLayout
        } else if ((key == "lfx2" || key == "lmfx") && target is Layer) {
            // Effects
            if (key == "lfx2") skipBytes(4) // version
            val desc = PsdDescriptor.readVersionAndDescriptor(this)
            target.effects = parseEffectsDescriptor(desc)
        } else if (key == "luni" && target is Layer) {
            target.name = readUnicodeString()
        } else if (key == "lyid" && target is Layer) {
            target.id = readInt32()
        } else if (key == "lsct" && target is Layer) {
            val type = readInt32()
            target.sectionDivider = SectionDivider(SectionDividerType.fromInt(type))
            if (sectionEnd - offset >= 8) {
                if (readSignature() == "8BIM") {
                    target.sectionDivider?.key = readSignature()
                    if (sectionEnd - offset >= 4) {
                        target.sectionDivider?.subType = readInt32()
                    }
                }
            }
        }
        
        offset = sectionEnd
        if (sectionLen % 2 != 0) offset++ // round to 2
    }

    private fun parseEffectsDescriptor(desc: DescriptorStructure): LayerEffectsInfo {
        val e = LayerEffectsInfo()
        e.disabled = !(desc.properties["masterFXSwitch"] as? BooleanValue)?.value!!
        e.scale = (desc.properties["Scl "] as? UnitDoubleValue)?.value?.div(100.0f)?.toFloat() ?: 1.0f

        (desc.properties["frameFXMulti"] as? ListValue)?.values?.let { list ->
            e.stroke = list.map { parseStrokeDescriptor(it as DescriptorStructure) }
        } ?: (desc.properties["FrFX"] as? DescriptorStructure)?.let {
            e.stroke = listOf(parseStrokeDescriptor(it))
        }

        (desc.properties["dropShadowMulti"] as? ListValue)?.values?.let { list ->
            e.dropShadow = list.map { parseShadowDescriptor(it as DescriptorStructure) }
        } ?: (desc.properties["DrSh"] as? DescriptorStructure)?.let {
            e.dropShadow = listOf(parseShadowDescriptor(it))
        }

        return e
    }

    private fun parseStrokeDescriptor(s: DescriptorStructure): LayerEffectStroke {
        val enabled = (s.properties["enab"] as? BooleanValue)?.value ?: true
        val position = when ((s.properties["Styl"] as? EnumValue)?.value) {
            "InsF" -> StrokePosition.INSIDE
            "CtrF" -> StrokePosition.CENTER
            else -> StrokePosition.OUTSIDE
        }
        val fillType = when ((s.properties["PntT"] as? EnumValue)?.value) {
            "GrFl" -> StrokeFillType.GRADIENT
            "Ptrn" -> StrokeFillType.PATTERN
            else -> StrokeFillType.COLOR
        }
        val blendModeVal = (s.properties["Md  "] as? EnumValue)?.value ?: "Nrml"
        val blendMode = PsdHelpers.fromBlendModeDescriptor.entries.firstOrNull { it.value == blendModeVal }?.key ?: BlendMode.NORMAL
        val opacity = (s.properties["Opct"] as? UnitDoubleValue)?.value?.div(100.0f)?.toFloat() ?: 1.0f
        val sizeVal = s.properties["Sz  "] as? UnitDoubleValue
        val size = UnitsValue(Units.fromString(sizeVal?.units ?: "Pixels"), sizeVal?.value?.toFloat() ?: 1.0f)
        val color = s.properties["Clr "]?.let { parseColorDescriptor(it as DescriptorStructure) }

        return LayerEffectStroke(
            enabled = enabled,
            position = position,
            fillType = fillType,
            blendMode = blendMode,
            opacity = opacity,
            size = size,
            color = color
        )
    }

    private fun parseShadowDescriptor(s: DescriptorStructure): LayerEffectShadow {
        val enabled = (s.properties["enab"] as? BooleanValue)?.value ?: true
        val blendModeVal = (s.properties["Md  "] as? EnumValue)?.value ?: "Nrml"
        val blendMode = PsdHelpers.fromBlendModeDescriptor.entries.firstOrNull { it.value == blendModeVal }?.key ?: BlendMode.MULTIPLY
        val color = s.properties["Clr "]?.let { parseColorDescriptor(it as DescriptorStructure) }
        val opacity = (s.properties["Opct"] as? UnitDoubleValue)?.value?.div(100.0f)?.toFloat() ?: 1.0f
        val useGlobalLight = (s.properties["uglg"] as? BooleanValue)?.value ?: true
        val angle = (s.properties["lagl"] as? UnitDoubleValue)?.value?.toFloat() ?: 120f
        val distanceVal = s.properties["Dstn"] as? UnitDoubleValue
        val distance = UnitsValue(Units.fromString(distanceVal?.units ?: "Pixels"), distanceVal?.value?.toFloat() ?: 0f)
        val sizeVal = s.properties["blur"] as? UnitDoubleValue
        val size = UnitsValue(Units.fromString(sizeVal?.units ?: "Pixels"), sizeVal?.value?.toFloat() ?: 0f)
        val chokeVal = s.properties["Ckmt"] as? UnitDoubleValue
        val choke = UnitsValue(Units.fromString(chokeVal?.units ?: "Percent"), chokeVal?.value?.toFloat() ?: 0f)

        return LayerEffectShadow(
            enabled = enabled,
            blendMode = blendMode,
            color = color,
            opacity = opacity,
            useGlobalLight = useGlobalLight,
            angle = angle,
            distance = distance,
            size = size,
            choke = choke
        )
    }

    private fun parseColorDescriptor(c: DescriptorStructure): Color {
        val r = (c.properties["Rd  "] as? DoubleValue)?.value?.toInt() ?: 0
        val g = (c.properties["Grn "] as? DoubleValue)?.value?.toInt() ?: 0
        val b = (c.properties["Bl  "] as? DoubleValue)?.value?.toInt() ?: 0
        return Rgb(r, g, b)
    }

    private fun parseWarpDescriptor(desc: DescriptorStructure): Warp {
        val styleStr = (desc.properties["warpStyle"] as? EnumValue)?.value ?: "warpNone"
        val style = WarpStyle.fromString(styleStr.removePrefix("warp"))
        val value = (desc.properties["warpValue"] as? DoubleValue)?.value?.toFloat() ?: 0f
        val perspective = (desc.properties["warpPerspective"] as? DoubleValue)?.value?.toFloat() ?: 0f
        val perspectiveOther = (desc.properties["warpPerspectiveOther"] as? DoubleValue)?.value?.toFloat() ?: 0f
        val rotate = (desc.properties["warpRotate"] as? EnumValue)?.value ?: "Hrzn"

        return Warp(
            style = style,
            value = value,
            perspective = perspective,
            perspectiveOther = perspectiveOther,
            rotate = if (rotate == "Vrtc") Orientation.VERTICAL else Orientation.HORIZONTAL
        )
    }

    private fun readLayerChannelImageData(psd: Psd, layer: Layer, info: List<ChannelInfo>) {
        val width = layer.right - layer.left
        val height = layer.bottom - layer.top

        if (width <= 0 || height <= 0) {
            // skip channel data for empty layers
            for (c in info) {
                skipBytes(c.length)
            }
            return
        }

        val channelData = mutableMapOf<ChannelID, ByteArray>()
        for (c in info) {
            if (c.length <= 0) {
                channelData[c.id] = ByteArray(width * height)
                continue
            }
            val compressionValue = readInt16()
            val compression = Compression.fromInt(compressionValue)
            if (compression == Compression.RleCompressed) {
                val rowCounts = IntArray(height)
                for (y in 0 until height) {
                    rowCounts[y] = if (large) readInt32() else readInt16()
                }
                
                val pixelData = PixelData(width, height, ByteArray(width * height * 4))
                PsdHelpers.readDataRLE(rowCounts, bytes, offset, pixelData, width, height, 1, intArrayOf(0))
                
                channelData[c.id] = pixelData.data.sliceArray(0 until width * height)
                
                for (len in rowCounts) skipBytes(len)
            } else if (compression == Compression.ZipWithoutPrediction || compression == Compression.ZipWithPrediction) {
                val compressedBytes = readBytes(c.length - 2)
                val pixelData = PixelData(width, height, ByteArray(width * height))
                PsdHelpers.readDataZip(
                    compressed = compressedBytes,
                    pixelData = pixelData,
                    width = width,
                    height = height,
                    bitDepth = 8,
                    step = 1,
                    offset = 0,
                    prediction = compression == Compression.ZipWithPrediction
                )
                channelData[c.id] = pixelData.data
            } else {
                channelData[c.id] = readBytes(c.length - 2)
            }
        }

        val r = channelData[ChannelID.Color0] ?: ByteArray(width * height)
        val g = channelData[ChannelID.Color1] ?: ByteArray(width * height)
        val b = channelData[ChannelID.Color2] ?: ByteArray(width * height)
        val a = channelData[ChannelID.Transparency] ?: ByteArray(width * height) { 255.toByte() }

        val pixels = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val base = i * 4
            pixels[base] = r[i]
            pixels[base + 1] = g[i]
            pixels[base + 2] = b[i]
            pixels[base + 3] = a[i]
        }
        layer.imageData = PixelData(width, height, pixels)
    }

    private fun readGlobalLayerMaskInfo(): GlobalLayerMaskInfo? {
        return readSection(2) {
            // skip for now
            null
        }
    }

    private fun readCompositeImageData(psd: Psd) {
        val compressionValue = readInt16()
        val compression = Compression.fromInt(compressionValue)

        val width = psd.width
        val height = psd.height
        val channels = psd.channels

        if (compression == Compression.RleCompressed) {
            val rowCounts = IntArray(height * channels)
            for (i in 0 until height * channels) {
                rowCounts[i] = if (large) readInt32() else readInt16()
            }
            
            val pixelData = PixelData(width, height, ByteArray(width * height * 4))
            val channelOffsets = IntArray(channels) { it }
            PsdHelpers.readDataRLE(rowCounts, bytes, offset, pixelData, width, height, 4, channelOffsets)
            psd.imageData = pixelData
            
            for (len in rowCounts) skipBytes(len)
        } else {
            val pixelData = PixelData(width, height, ByteArray(width * height * 4))
            for (c in 0 until channels) {
                val channel = readBytes(width * height)
                PsdHelpers.copyChannelToPixelData(pixelData, channel, c, 4)
            }
            psd.imageData = pixelData
        }
    }

    fun readUint64(): Long {
        val v = byteBuffer.getLong(offset)
        offset += 8
        return v
    }
}
