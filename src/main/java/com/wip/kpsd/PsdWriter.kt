package com.wip.kpsd

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PsdWriter(initialCapacity: Int = 4096) {
    var bytes = ByteArray(initialCapacity)
    var offset = 0
    var large = false
    private var layerIdCounter = 0

    private fun ensureSize(size: Int) {
        if (size > bytes.size) {
            var newLength = bytes.size
            do {
                newLength *= 2
            } while (size > newLength)
            val newBytes = ByteArray(newLength)
            System.arraycopy(bytes, 0, newBytes, 0, offset)
            bytes = newBytes
        }
    }

    fun getWriterBuffer(): ByteArray {
        return bytes.copyOf(offset)
    }

    fun writeUint8(value: Int) {
        ensureSize(offset + 1)
        bytes[offset] = value.toByte()
        offset += 1
    }

    fun writeInt16(value: Int) {
        ensureSize(offset + 2)
        bytes[offset] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 1] = (value and 0xff).toByte()
        offset += 2
    }

    fun writeUint16(value: Int) {
        writeInt16(value)
    }

    fun writeUint16LE(value: Int) {
        ensureSize(offset + 2)
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
        offset += 2
    }

    fun writeInt32(value: Int) {
        ensureSize(offset + 4)
        bytes[offset] = ((value shr 24) and 0xff).toByte()
        bytes[offset + 1] = ((value shr 16) and 0xff).toByte()
        bytes[offset + 2] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 3] = (value and 0xff).toByte()
        offset += 4
    }

    fun writeInt32LE(value: Int) {
        ensureSize(offset + 4)
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xff).toByte()
        offset += 4
    }

    fun writeUint32(value: Long) {
        ensureSize(offset + 4)
        bytes[offset] = ((value shr 24) and 0xff).toByte()
        bytes[offset + 1] = ((value shr 16) and 0xff).toByte()
        bytes[offset + 2] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 3] = (value and 0xff).toByte()
        offset += 4
    }

    fun writeFloat32(value: Float) {
        val intBits = java.lang.Float.floatToIntBits(value)
        writeInt32(intBits)
    }

    fun writeFloat64(value: Double) {
        val longBits = java.lang.Double.doubleToLongBits(value)
        writeInt32((longBits shr 32).toInt())
        writeInt32((longBits and 0xffffffffL).toInt())
    }

    fun writeFixedPoint32(value: Float) {
        writeInt32((value * (1 shl 16)).toInt())
    }

    fun writeFixedPointPath32(value: Float) {
        writeInt32((value * (1 shl 24)).toInt())
    }

    fun writeBytes(buffer: ByteArray?) {
        if (buffer != null) {
            ensureSize(offset + buffer.size)
            System.arraycopy(buffer, 0, bytes, offset, buffer.size)
            offset += buffer.size
        }
    }

    fun writeZeros(count: Int) {
        for (i in 0 until count) {
            writeUint8(0)
        }
    }

    fun writeSignature(signature: String) {
        require(signature.length == 4) { "Invalid signature: '$signature'" }
        for (i in 0 until 4) {
            writeUint8(signature[i].code)
        }
    }

    fun writeAsciiString(text: String) {
        for (i in 0 until text.length) {
            writeUint8(text[i].code)
        }
    }

    fun writePascalString(text: String, padTo: Int) {
        var length = text.length
        require(length <= 255) { "String too long" }
        writeUint8(length)
        for (i in 0 until length) {
            val code = text[i].code
            writeUint8(if (code < 128) code else '?'.code)
        }
        length++ // including size byte
        while (length % padTo != 0) {
            writeUint8(0)
            length++
        }
    }

    fun writeUnicodeStringWithoutLength(text: String) {
        for (i in 0 until text.length) {
            writeUint16(text[i].code)
        }
    }

    fun writeUnicodeStringWithoutLengthLE(text: String) {
        for (i in 0 until text.length) {
            writeUint16LE(text[i].code)
        }
    }

    fun writeUnicodeString(text: String) {
        writeUint32(text.length.toLong())
        writeUnicodeStringWithoutLength(text)
    }

    fun writeUnicodeStringWithPadding(text: String) {
        writeUint32(text.length + 1L)
        writeUnicodeStringWithoutLength(text)
        writeUint16(0)
    }

    fun writeSection(
        round: Int,
        writeTotalLength: Boolean = false,
        largeSection: Boolean = false,
        func: () -> Unit
    ) {
        if (largeSection) writeUint32(0L)
        val startOffset = offset
        writeUint32(0L)

        func()

        var length = offset - startOffset - 4
        var len = length

        while (len % round != 0) {
            writeUint8(0)
            len++
        }

        val finalLength = if (writeTotalLength) len else length
        bytes[startOffset] = ((finalLength shr 24) and 0xff).toByte()
        bytes[startOffset + 1] = ((finalLength shr 16) and 0xff).toByte()
        bytes[startOffset + 2] = ((finalLength shr 8) and 0xff).toByte()
        bytes[startOffset + 3] = (finalLength and 0xff).toByte()
    }

    fun writePsd(psd: Psd, compress: Boolean = false) {
        require(psd.width > 0 && psd.height > 0) { "Invalid document size" }

        val bitsPerChannel = psd.bitsPerChannel
        require(bitsPerChannel == 8) { "bitsPerChannel other than 8 are not supported for writing" }

        val globalAlpha = psd.imageData != null && PsdHelpers.hasAlpha(psd.imageData!!)
        
        layerIdCounter = maxLayerId(psd.children)

        val layers = mutableListOf<Layer>()
        addChildren(layers, psd.children)
        if (layers.isEmpty()) {
            layers.add(Layer())
        }

        // Header
        writeSignature("8BPS")
        writeUint16(if (large) 2 else 1) // version
        writeZeros(6)
        writeUint16(if (globalAlpha) 4 else 3) // channels
        writeInt32(psd.height)
        writeInt32(psd.width)
        writeUint16(bitsPerChannel)
        writeUint16(ColorMode.RGB.value)

        // Color Mode Data
        writeSection(1) {
            val palette = psd.palette
            if (palette != null) {
                for (i in 0 until 256) writeUint8(if (i < palette.size) palette[i].r else 0)
                for (i in 0 until 256) writeUint8(if (i < palette.size) palette[i].g else 0)
                for (i in 0 until 256) writeUint8(if (i < palette.size) palette[i].b else 0)
            }
        }

        // Image Resources
        val resources = psd.imageResources ?: ImageResources()
        resources.layersGroup = layers.map { it.linkGroup ?: 0 }.toIntArray()
        resources.layerGroupsEnabledId = layers.map { if (it.linkGroupEnabled == false) 0 else 1 }.toIntArray()
        if (resources.resolutionInfo == null) resources.resolutionInfo = ResolutionInfo()

        writeSection(1, writeTotalLength = true) {
            // Write layer groups resource
            resources.layersGroup?.let { groups ->
                writeSignature("8BIM")
                writeUint16(1026)
                writePascalString("", 2)
                writeSection(2) {
                    for (g in groups) writeUint16(g)
                }
            }
            // Write layer groups enabled status
            resources.layerGroupsEnabledId?.let { enabled ->
                writeSignature("8BIM")
                writeUint16(1072)
                writePascalString("", 2)
                writeSection(2) {
                    for (e in enabled) writeUint8(e)
                }
            }
            // Write resolution info
            resources.resolutionInfo?.let { info ->
                writeSignature("8BIM")
                writeUint16(1005)
                writePascalString("", 2)
                writeSection(2) {
                    writeFixedPoint32(info.horizontalResolution)
                    writeUint16(if (info.horizontalResolutionUnit == ResolutionUnit.PPI) 1 else 2)
                    writeUint16(if (info.widthUnit == MeasurementUnit.INCHES) 1 else 2) // simplification
                    writeFixedPoint32(info.verticalResolution)
                    writeUint16(if (info.verticalResolutionUnit == ResolutionUnit.PPI) 1 else 2)
                    writeUint16(if (info.heightUnit == MeasurementUnit.INCHES) 1 else 2) // simplification
                }
            }
        }

        // Layer and Mask Info
        writeSection(2, writeTotalLength = false, largeSection = large) {
            writeLayerInfo(layers, globalAlpha, compress)
            writeZeros(4) // Empty global layer mask info
        }

        // Composite Image Data
        val compositeData = psd.imageData ?: PixelData(psd.width, psd.height, ByteArray(psd.width * psd.height * 4) {
            if (it % 4 == 3) 255.toByte() else 255.toByte()
        })

        // Photoshop doesn't support ZIP compression for composite image data
        writeUint16(Compression.RleCompressed.value)

        val channelsToSave = if (globalAlpha) intArrayOf(0, 1, 2, 3) else intArrayOf(0, 1, 2)
        writeBytes(PsdHelpers.writeDataRLE(compositeData, channelsToSave, large))
    }

    private fun maxLayerId(children: List<Layer>?): Int {
        if (children == null) return 0
        var max = 0
        for (c in children) {
            c.id?.let { if (it > max) max = it }
            val childMax = maxLayerId(c.children)
            if (childMax > max) max = childMax
        }
        return max
    }

    private fun addChildren(layers: MutableList<Layer>, children: List<Layer>?) {
        if (children == null) return
        for (c in children) {
            if (c.children != null) {
                // Folder end divider
                layers.add(
                    Layer(
                        name = "</Layer group>",
                        sectionDivider = SectionDivider(SectionDividerType.BoundingSectionDivider)
                    )
                )
                addChildren(layers, c.children)
                // Folder start divider
                layers.add(
                    c.copy(
                        id = c.id ?: ++layerIdCounter,
                        imageData = null, // Folders shouldn't have pixel data
                        sectionDivider = SectionDivider(
                            if (c.opened) SectionDividerType.OpenFolder else SectionDividerType.ClosedFolder,
                            "pass"
                        )
                    )
                )
            } else {
                layers.add(c.copy(id = c.id ?: ++layerIdCounter))
            }
        }
    }

    private fun writeLayerInfo(layers: List<Layer>, globalAlpha: Boolean, compress: Boolean) {
        writeSection(4, writeTotalLength = true, largeSection = large) {
            writeInt16(if (globalAlpha) -layers.size else layers.size)

            val layersChannelsData = layers.mapIndexed { i, l -> getLayerChannelsData(l, compress, i == 0) }

            // Layer Records
            for (ld in layersChannelsData) {
                writeInt32(ld.top)
                writeInt32(ld.left)
                writeInt32(ld.bottom)
                writeInt32(ld.right)
                writeUint16(ld.channels.size)

                for (c in ld.channels) {
                    writeInt16(c.id.value)
                    if (large) writeUint32(0L)
                    writeInt32(c.length)
                }

                writeSignature("8BIM")
                writeSignature(PsdHelpers.fromBlendMode[ld.layer.blendMode] ?: "norm")
                writeUint8((ld.layer.opacity * 255).toInt())
                writeUint8(if (ld.layer.clipping) 1 else 0)

                var flags = 0x08
                if (ld.layer.transparencyProtected) flags = flags or 0x01
                if (ld.layer.hidden) flags = flags or 0x02
                if (ld.layer.text != null || ld.layer.sectionDivider != null) flags = flags or 0x10
                if (ld.layer.effectsOpen) flags = flags or 0x20
                writeUint8(flags)
                writeUint8(0) // padding

                writeSection(1) {
                    writeLayerMaskData(ld.layer)
                    writeLayerBlendingRanges(ld.layer)
                    writePascalString(ld.layer.name ?: "", 4)
                    writeAdditionalLayerInfo(ld.layer)
                }
            }

            // Layer Channel Image Data
            for (ld in layersChannelsData) {
                for (c in ld.channels) {
                    writeUint16(c.compression.value)
                    writeBytes(c.buffer)
                }
            }
        }
    }

    private fun writeLayerMaskData(layer: Layer) {
        writeSection(1) {
            val mask = layer.mask
            val realMask = layer.realMask
            if (mask != null || realMask != null) {
                var flags = 0
                if (mask?.disabled == true) flags = flags or 2
                if (mask?.positionRelativeToLayer == true) flags = flags or 1
                if (mask?.fromVectorData == true) flags = flags or 8

                writeInt32(mask?.top ?: 0)
                writeInt32(mask?.left ?: 0)
                writeInt32(mask?.bottom ?: 0)
                writeInt32(mask?.right ?: 0)
                writeUint8(mask?.defaultColor ?: 0)
                writeUint8(flags)

                if (realMask != null) {
                    var realFlags = 0
                    if (realMask.disabled == true) realFlags = realFlags or 2
                    if (realMask.positionRelativeToLayer == true) realFlags = realFlags or 1
                    if (realMask.fromVectorData == true) realFlags = realFlags or 8
                    writeUint8(realFlags)
                    writeUint8(realMask.defaultColor ?: 0)
                    writeInt32(realMask.top ?: 0)
                    writeInt32(realMask.left ?: 0)
                    writeInt32(realMask.bottom ?: 0)
                    writeInt32(realMask.right ?: 0)
                }
                writeZeros(2)
            }
        }
    }

    private fun writeLayerBlendingRanges(layer: Layer) {
        writeSection(1) {
            val ranges = layer.blendingRanges
            if (ranges != null) {
                writeBytes(ranges.compositeGrayBlendSource)
                writeBytes(ranges.compositeGraphBlendDestinationRange)
                for (r in ranges.ranges) {
                    writeBytes(r.sourceRange)
                    writeBytes(r.destRange)
                }
            }
        }
    }

    private fun writeAdditionalLayerInfo(target: Any) {
        val largeKeys = listOf("LMsk", "Lr16", "Lr32", "Layr", "Mt16", "Mt32", "Mtrn", "Alph", "FMsk", "lnk2", "FEid", "FXid", "PxSD", "cinf")
        
        if (target is Layer) {
            val text = target.text
            if (text != null) {
                val key = "TySh"
                writeSignature("8BIM")
                writeSignature(key)
                writeSection(2, writeTotalLength = true, largeSection = false) {
                    writeInt16(1) // version
                    val transform = text.transform ?: doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
                    for (t in transform) writeFloat64(t)

                    writeInt16(50) // text version
                    val textDesc = buildTextDescriptor(text)
                    PsdDescriptor.writeVersionAndDescriptor(this, "", "TxLr", textDesc)

                    writeInt16(1) // warp version
                    val warpDesc = buildWarpDescriptor(text.warp)
                    PsdDescriptor.writeVersionAndDescriptor(this, "", "warp", warpDesc)

                    writeFloat32(text.left ?: 0f)
                    writeFloat32(text.top ?: 0f)
                    writeFloat32(text.right ?: 0f)
                    writeFloat32(text.bottom ?: 0f)
                }
            }

            target.id?.let { id ->
                val key = "lyid"
                writeSignature("8BIM")
                writeSignature(key)
                writeSection(2, writeTotalLength = true, largeSection = false) {
                    writeUint32(id.toLong())
                }
            }

            target.name?.let { name ->
                val key = "luni"
                writeSignature("8BIM")
                writeSignature(key)
                writeSection(4, writeTotalLength = true, largeSection = false) {
                    writeInt32(name.length)
                    writeUnicodeStringWithoutLength(name)
                }
            }

            target.sectionDivider?.let { sd ->
                val key = "lsct"
                writeSignature("8BIM")
                writeSignature(key)
                writeSection(2, writeTotalLength = true, largeSection = false) {
                    writeInt32(sd.type.value)
                    sd.key?.let { k ->
                        writeSignature("8BIM")
                        writeSignature(k)
                        sd.subType?.let { st ->
                            writeInt32(st)
                        }
                    }
                }
            }

            target.effects?.let { effects ->
                val key = "lfx2"
                writeSignature("8BIM")
                writeSignature(key)
                writeSection(2, writeTotalLength = true, largeSection = false) {
                    writeInt32(0) // version
                    val desc = buildEffectsDescriptor(effects, multi = false)
                    PsdDescriptor.writeVersionAndDescriptor(this, "", "null", desc)
                }

                // Multi effects (lmfx) - only write if more than 1 effect total
                val effectCount = (effects.stroke?.size ?: 0) + (effects.dropShadow?.size ?: 0)
                if (effectCount > 1) {
                    val mkey = "lmfx"
                    writeSignature("8BIM")
                    writeSignature(mkey)
                    writeSection(4, writeTotalLength = true, largeSection = false) {
                        val mdesc = buildEffectsDescriptor(effects, multi = true)
                        PsdDescriptor.writeVersionAndDescriptor(this, "", "lmfx", mdesc)
                    }
                }
            }
        }
    }

    private fun buildEffectsDescriptor(e: LayerEffectsInfo, multi: Boolean): DescriptorStructure {
        val props = mutableMapOf<String, DescriptorValue>()
        props["masterFXSwitch"] = BooleanValue(!e.disabled)
        props["Scl "] = UnitDoubleValue("#Prc", e.scale * 100.0)

        if (multi) {
            var numEnabled = 0
            e.stroke?.let { list ->
                val multiList = list.map { s ->
                    if (s.enabled) numEnabled++
                    buildStrokeDescriptor(s)
                }
                props["frameFXMulti"] = ListValue(multiList)
            }
            e.dropShadow?.let { list ->
                val multiList = list.map { s ->
                    if (s.enabled) numEnabled++
                    buildShadowDescriptor(s, "DrSh")
                }
                props["dropShadowMulti"] = ListValue(multiList)
            }
            props["numModifyingFX"] = LongValue(numEnabled)
        } else {
            e.stroke?.firstOrNull()?.let { s ->
                props["FrFX"] = buildStrokeDescriptor(s)
            }
            e.dropShadow?.firstOrNull()?.let { s ->
                props["DrSh"] = buildShadowDescriptor(s, "DrSh")
            }
        }

        return DescriptorStructure("", if (multi) "lmfx" else "null", props)
    }

    private fun buildStrokeDescriptor(s: LayerEffectStroke): DescriptorStructure {
        val props = mutableMapOf<String, DescriptorValue>()
        props["enab"] = BooleanValue(s.enabled)
        props["present"] = BooleanValue(s.present)
        props["showInDialog"] = BooleanValue(s.showInDialog)
        props["Styl"] = EnumValue("FStl", when (s.position) {
            StrokePosition.INSIDE -> "InsF"
            StrokePosition.CENTER -> "CtrF"
            else -> "OutF"
        })
        props["PntT"] = EnumValue("FrFl", when (s.fillType) {
            StrokeFillType.GRADIENT -> "GrFl"
            StrokeFillType.PATTERN -> "Ptrn"
            else -> "SClr"
        })
        props["Md  "] = EnumValue("BlnM", PsdHelpers.fromBlendModeDescriptor[s.blendMode] ?: "Nrml")
        props["Opct"] = UnitDoubleValue("#Prc", s.opacity * 100.0)
        props["Sz  "] = UnitDoubleValue(if (s.size.units == Units.PIXELS) "#Pxl" else "#Pnt", s.size.value.toDouble())
        s.color?.let { props["Clr "] = buildColorDescriptor(it) }
        s.overprint?.let { props["overprint"] = BooleanValue(it) }

        return DescriptorStructure("", "FrFX", props)
    }

    private fun buildShadowDescriptor(s: LayerEffectShadow, classID: String): DescriptorStructure {
        val props = mutableMapOf<String, DescriptorValue>()
        props["enab"] = BooleanValue(s.enabled)
        props["present"] = BooleanValue(s.present)
        props["showInDialog"] = BooleanValue(s.showInDialog)
        props["Md  "] = EnumValue("BlnM", PsdHelpers.fromBlendModeDescriptor[s.blendMode] ?: "Nrml")
        s.color?.let { props["Clr "] = buildColorDescriptor(it) }
        props["Opct"] = UnitDoubleValue("#Prc", s.opacity * 100.0)
        props["uglg"] = BooleanValue(s.useGlobalLight)
        props["lagl"] = UnitDoubleValue("#Ang", s.angle.toDouble())
        props["Dstn"] = UnitDoubleValue(if (s.distance.units == Units.PIXELS) "#Pxl" else "#Rlt", s.distance.value.toDouble())
        props["blur"] = UnitDoubleValue(if (s.size.units == Units.PIXELS) "#Pxl" else "#Rlt", s.size.value.toDouble())
        props["Ckmt"] = UnitDoubleValue("#Prc", s.choke.value.toDouble())
        props["AntA"] = BooleanValue(s.antialiased)
        // contour missing for now
        if (classID == "DrSh") {
            props["layerConceals"] = BooleanValue(s.layerConceals)
        }
        return DescriptorStructure("", classID, props)
    }

    private fun buildColorDescriptor(color: Color): DescriptorStructure {
        val props = mutableMapOf<String, DescriptorValue>()
        return when (color) {
            is Rgb -> {
                props["Rd  "] = DoubleValue(color.r.toDouble())
                props["Grn "] = DoubleValue(color.g.toDouble())
                props["Bl  "] = DoubleValue(color.b.toDouble())
                DescriptorStructure("", "RGBC", props)
            }
            is Rgba -> {
                props["Rd  "] = DoubleValue(color.r.toDouble())
                props["Grn "] = DoubleValue(color.g.toDouble())
                props["Bl  "] = DoubleValue(color.b.toDouble())
                // alpha usually ignored in descriptor color
                DescriptorStructure("", "RGBC", props)
            }
            else -> DescriptorStructure("", "RGBC", props) // fallback
        }
    }

    private fun buildTextDescriptor(text: LayerTextData): DescriptorStructure {
        val props = mutableMapOf<String, DescriptorValue>()
        props["Txt "] = TextValue((text.text).replace(Regex("\\r?\\n"), "\r"))
        props["textGridding"] = EnumValue("textGridding", if (text.gridding == TextGridding.ROUND) "Rnd " else "None")
        props["Ornt"] = EnumValue("Ornt", if (text.orientation == Orientation.VERTICAL) "Vrtc" else "Hrzn")
        props["AntA"] = EnumValue("Annt", when(text.antiAlias) {
            AntiAlias.NONE -> "Anno"
            AntiAlias.CRISP -> "AnCr"
            AntiAlias.STRONG -> "AnSt"
            AntiAlias.SMOOTH -> "AnSm"
            AntiAlias.SHARP -> "antiAliasSharp"
            else -> "antiAliasSharp"
        })
        props["TextIndex"] = LongValue(text.index ?: 0)

        // EngineData
        val serializedData = EngineData.serializeEngineData(TextLayer.encodeEngineData(text))
        props["EngineData"] = RawDataValue(serializedData)

        return DescriptorStructure(
            name = "",
            classID = "TxLr",
            properties = props
        )
    }

    private fun buildWarpDescriptor(warp: Warp?): DescriptorStructure {
        val props = mutableMapOf<String, DescriptorValue>()
        
        val rawStyle = warp?.style ?: WarpStyle.NONE
        val styleCapitalized = rawStyle.value.replaceFirstChar { it.uppercaseChar() }
        val warpStyleVal = if (styleCapitalized.startsWith("Warp")) {
            styleCapitalized.replaceFirstChar { it.lowercaseChar() }
        } else {
            "warp$styleCapitalized"
        }
        props["warpStyle"] = EnumValue("warpStyle", warpStyleVal)
        props["warpValue"] = DoubleValue(warp?.value?.toDouble() ?: 0.0)
        props["warpPerspective"] = DoubleValue(warp?.perspective?.toDouble() ?: 0.0)
        props["warpPerspectiveOther"] = DoubleValue(warp?.perspectiveOther?.toDouble() ?: 0.0)
        
        val rawRotate = warp?.rotate ?: Orientation.HORIZONTAL
        props["warpRotate"] = EnumValue("orientation", if (rawRotate == Orientation.VERTICAL) "Vrtc" else "Hrzn")
        props["uOrder"] = LongValue(warp?.uOrder ?: 0)
        props["vOrder"] = LongValue(warp?.vOrder ?: 0)

        // Bounds
        val bounds = warp?.bounds
        props["bounds"] = DescriptorStructure(
            name = "",
            classID = "Rctn",
            properties = mapOf(
                "Top " to UnitDoubleValue(bounds?.top?.units?.value ?: "Pixels", bounds?.top?.value?.toDouble() ?: 0.0),
                "Left" to UnitDoubleValue(bounds?.left?.units?.value ?: "Pixels", bounds?.left?.value?.toDouble() ?: 0.0),
                "Btom" to UnitDoubleValue(bounds?.bottom?.units?.value ?: "Pixels", bounds?.bottom?.value?.toDouble() ?: 0.0),
                "Rght" to UnitDoubleValue(bounds?.right?.units?.value ?: "Pixels", bounds?.right?.value?.toDouble() ?: 0.0)
            )
        )

        return DescriptorStructure(
            name = "",
            classID = "warp",
            properties = props
        )
    }

    data class ChannelData(val id: ChannelID, val compression: Compression, val buffer: ByteArray, val length: Int)
    data class LayerChannelData(val layer: Layer, val top: Int, val left: Int, val bottom: Int, val right: Int, val channels: List<ChannelData>)

    private fun getLayerChannelsData(layer: Layer, compress: Boolean, isBackground: Boolean): LayerChannelData {
        var top = layer.top
        var left = layer.left
        var bottom = layer.bottom
        var right = layer.right

        val channels = mutableListOf<ChannelData>()
        val imageData = layer.imageData

        if (imageData == null || (right - left) <= 0 || (bottom - top) <= 0) {
            // For text layers or empty layers, we must set bounds to 0x0
            // so Photoshop doesn't expect pixel data.
            right = left
            bottom = top
            
            // Photoshop still expects the basic channels (0, 1, 2) and Alpha (-1)
            val emptyChannels = mutableListOf(
                ChannelData(ChannelID.Color0, Compression.RawData, ByteArray(0), 2),
                ChannelData(ChannelID.Color1, Compression.RawData, ByteArray(0), 2),
                ChannelData(ChannelID.Color2, Compression.RawData, ByteArray(0), 2)
            )
            if (!isBackground) {
                emptyChannels.add(0, ChannelData(ChannelID.Transparency, Compression.RawData, ByteArray(0), 2))
            }
            return LayerChannelData(layer, top, left, bottom, right, emptyChannels)
        }

        val width = right - left
        val height = bottom - top

        // Channels to save: Transparency, Red, Green, Blue
        val channelIDs = mutableListOf(ChannelID.Color0, ChannelID.Color1, ChannelID.Color2)
        if (!isBackground || PsdHelpers.hasAlpha(imageData)) {
            channelIDs.add(0, ChannelID.Transparency)
        }

        for (cid in channelIDs) {
            val offsetInPixelData = PsdHelpers.offsetForChannel(cid, false)
            val channelBytes: ByteArray
            val compression: Compression

            if (compress) {
                channelBytes = PsdHelpers.writeDataZipWithoutPrediction(imageData, intArrayOf(offsetInPixelData))
                compression = Compression.ZipWithoutPrediction
            } else {
                channelBytes = PsdHelpers.writeDataRLE(imageData, intArrayOf(offsetInPixelData), large)
                compression = Compression.RleCompressed
            }

            channels.add(ChannelData(cid, compression, channelBytes, 2 + channelBytes.size))
        }

        return LayerChannelData(layer, top, left, bottom, right, channels)
    }
}
