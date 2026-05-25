package com.wip.kpsd

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking

class KPsdTest {

    @Test
    fun testPsdRoundtrip() {
        val width = 200
        val height = 150

        // 1. Create a solid background pixel data (filled with red: 255, 0, 0, 255)
        val bgData = ByteArray(width * height * 4)
        for (i in bgData.indices step 4) {
            bgData[i] = 255.toByte()     // R
            bgData[i + 1] = 0.toByte()   // G
            bgData[i + 2] = 0.toByte()   // B
            bgData[i + 3] = 255.toByte() // A
        }
        val bgPixelData = PixelData(width, height, bgData)

        // 2. Create the PSD structure:
        // - Background layer
        // - Folder: "Texts"
        //   - Text layer: "Hello World"
        val bgLayer = Layer(
            name = "Background",
            top = 0,
            left = 0,
            bottom = height,
            right = width,
            imageData = bgPixelData
        )

        val textLayer = Layer(
            name = "Hello Text",
            top = 10,
            left = 20,
            bottom = 50,
            right = 180,
            text = LayerTextData(
                text = "Hello World",
                transform = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 20.0, 10.0),
                style = TextStyle(
                    font = Font(name = "ArialMT"),
                    fontSize = 24.0f,
                    fillColor = Rgb(0, 0, 0)
                )
            )
        )

        val folderLayer = Layer(
            name = "Texts",
            children = mutableListOf(textLayer)
        )

        val originalPsd = Psd(
            width = width,
            height = height,
            children = mutableListOf(bgLayer, folderLayer)
        )

        // 3. Serialize PSD to Byte Array
        val psdBytes = KPsd.write(originalPsd, compress = false)
        assertNotNull(psdBytes)
        assertTrue(psdBytes.isNotEmpty())

        // 4. Parse PSD back from Byte Array
        val parsedPsd = KPsd.read(psdBytes)
        assertNotNull(parsedPsd)

        // 5. Assert document properties
        assertEquals(width, parsedPsd.width)
        assertEquals(height, parsedPsd.height)
        assertEquals(2, parsedPsd.children.size)

        // 6. Assert Background Layer
        val parsedBg = parsedPsd.children[0]
        assertEquals("Background", parsedBg.name)
        assertEquals(0, parsedBg.left)
        assertEquals(0, parsedBg.top)
        assertEquals(width, parsedBg.right)
        assertEquals(height, parsedBg.bottom)
        assertNotNull(parsedBg.imageData)
        assertEquals(bgPixelData.width, parsedBg.imageData!!.width)
        assertEquals(bgPixelData.height, parsedBg.imageData!!.height)
        // Verify R channel of pixel (0,0) is 255
        assertEquals(255.toByte(), parsedBg.imageData!!.data[0])

        // 7. Assert Folder Layer & Child Text Layer
        val parsedFolder = parsedPsd.children[1]
        assertEquals("Texts", parsedFolder.name)
        assertNotNull(parsedFolder.children)
        assertEquals(1, parsedFolder.children!!.size)

        val parsedTextLayer = parsedFolder.children!![0]
        assertEquals("Hello Text", parsedTextLayer.name)
        assertNotNull(parsedTextLayer.text)
        assertEquals("Hello World", parsedTextLayer.text!!.text)

        val textStyle = parsedTextLayer.text!!.style
        assertNotNull(textStyle)
        assertEquals("ArialMT", textStyle.font?.name)
        assertEquals(24.0f, textStyle.fontSize)
    }

    @Test
    fun testLayerEffectsRoundtrip() {
        val width = 100
        val height = 100
        val layer = Layer(
            name = "Stroked Layer",
            top = 10, left = 10, bottom = 90, right = 90,
            imageData = PixelData(80, 80, ByteArray(80 * 80 * 4) { 128.toByte() }),
            effects = LayerEffectsInfo(
                stroke = listOf(
                    LayerEffectStroke(
                        size = UnitsValue(Units.PIXELS, 5f),
                        color = Rgb(255, 0, 0)
                    )
                )
            ),
            effectsOpen = true
        )
        val psd = Psd(width = width, height = height, children = mutableListOf(layer))
        
        val bytes = KPsd.write(psd)
        // Note: Reader implementation for effects is not yet fully added, 
        // but writing and then checking file size difference in comparison test is already done.
        // I will implement a minimal reader for verification if needed.
        // For now, let's at least verify that it doesn't crash.
        val parsed = KPsd.read(bytes)
        assertNotNull(parsed)
    }

    @Test
    fun testPsdRoundtripCompressed() {
        val width = 100
        val height = 80

        val bgData = ByteArray(width * height * 4) { 128.toByte() }
        val bgPixelData = PixelData(width, height, bgData)

        val bgLayer = Layer(
            name = "Background",
            top = 0,
            left = 0,
            bottom = height,
            right = width,
            imageData = bgPixelData
        )

        val textLayer = Layer(
            name = "Hello Native",
            top = 5,
            left = 5,
            bottom = 25,
            right = 95,
            text = LayerTextData(
                text = "Native Text",
                transform = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 5.0, 5.0),
                style = TextStyle(
                    font = Font(name = "CourierNewPSMT"),
                    fontSize = 12.0f,
                    fillColor = Rgb(255, 255, 255)
                )
            )
        )

        val originalPsd = Psd(
            width = width,
            height = height,
            children = mutableListOf(bgLayer, textLayer)
        )

        // Serialize PSD with compress = true (ZipWithoutPrediction)
        val psdBytes = KPsd.write(originalPsd, compress = true)
        assertNotNull(psdBytes)
        assertTrue(psdBytes.isNotEmpty())

        // Parse PSD back
        val parsedPsd = KPsd.read(psdBytes)
        assertNotNull(parsedPsd)

        assertEquals(width, parsedPsd.width)
        assertEquals(height, parsedPsd.height)
        assertEquals(2, parsedPsd.children.size)

        val parsedBg = parsedPsd.children[0]
        assertEquals("Background", parsedBg.name)
        assertNotNull(parsedBg.imageData)
        assertEquals(128.toByte(), parsedBg.imageData!!.data[0])

        val parsedTextLayer = parsedPsd.children[1]
        assertEquals("Hello Native", parsedTextLayer.name)
        assertNotNull(parsedTextLayer.text)
        assertEquals("Native Text", parsedTextLayer.text!!.text)
        assertEquals("CourierNewPSMT", parsedTextLayer.text!!.style?.font?.name)
    }

    @Test
    fun testPackbitsRleDirect() {
        val width = 16
        val height = 2
        val step = 4
        // Create repeating pattern
        val data = ByteArray(width * height * step)
        for (i in data.indices step step) {
            data[i] = (if ((i / step) % 4 == 0) 10 else 20).toByte() // R channel
        }
        val pixelData = PixelData(width, height, data)

        // Compress
        val compressed = PsdHelpers.writeDataRLE(pixelData, intArrayOf(0), large = false)
        assertNotNull(compressed)

        // Parse lengths
        val lengths = IntArray(height) {
            val idx = it * 2
            ((compressed[idx].toInt() and 0xff) shl 8) or (compressed[idx + 1].toInt() and 0xff)
        }

        // Decompress
        val decompressedPixelData = PixelData(width, height, ByteArray(width * height * step))
        PsdHelpers.readDataRLE(lengths, compressed, height * 2, decompressedPixelData, width, height, step, intArrayOf(0))

        // Assert
        for (i in 0 until (width * height)) {
            val p = i * step
            assertEquals(data[p], decompressedPixelData.data[p], "Mismatch at index $i")
        }
    }

    @Test
    fun testDescriptorRoundtrip() {
        val originalDesc = DescriptorStructure(
            name = "TestDescriptor",
            classID = "testClass",
            properties = mapOf(
                "longProp" to LongValue(12345),
                "doubProp" to DoubleValue(123.456),
                "boolProp" to BooleanValue(true),
                "textProp" to TextValue("Hello Descriptor"),
                "enumProp" to EnumValue("enumType", "enumVal")
            )
        )

        val writer = PsdWriter()
        PsdDescriptor.writeVersionAndDescriptor(writer, originalDesc.name, originalDesc.classID, originalDesc)
        val bytes = writer.getWriterBuffer()

        val reader = PsdReader(bytes)
        val parsedDesc = PsdDescriptor.readVersionAndDescriptor(reader)

        assertNotNull(parsedDesc)
        assertEquals(originalDesc.name, parsedDesc.name)
        assertEquals(originalDesc.classID, parsedDesc.classID)

        val parsedProps = parsedDesc.properties
        assertEquals(12345, (parsedProps["longProp"] as LongValue).value)
        assertEquals(123.456, (parsedProps["doubProp"] as DoubleValue).value)
        assertEquals(true, (parsedProps["boolProp"] as BooleanValue).value)
        assertEquals("Hello Descriptor", (parsedProps["textProp"] as TextValue).value)
        assertEquals("enumType", (parsedProps["enumProp"] as EnumValue).type)
        assertEquals("enumVal", (parsedProps["enumProp"] as EnumValue).value)
    }

    @Test
    fun testEngineDataParsingDirect() {
        // Sample Lisp-like EngineData format used by Photoshop
        val engineDataBytes = """
            <<
              /EngineDict <<
                /Editor <<
                  /Text (EngineData Test String)
                >>
                /StyleRun <<
                  /RunArray [
                    <<
                      /StyleSheet <<
                        /StyleSheetData <<
                          /FontSize 14.5
                        >>
                      >>
                    >>
                  ]
                  /RunLengthArray [
                    22
                  ]
                >>
              >>
              /ResourceDict <<
                /FontSet [
                  <<
                    /Name (Helvetica)
                  >>
                ]
              >>
            >>
        """.trimIndent().toByteArray(Charsets.US_ASCII)

        val parsed = EngineData.parseEngineData(engineDataBytes)
        assertNotNull(parsed)

        val engineDict = parsed["EngineDict"] as? Map<String, Any?>
        assertNotNull(engineDict)
        val editor = engineDict["Editor"] as? Map<String, Any?>
        assertNotNull(editor)
        assertEquals("EngineData Test String", editor["Text"])

        val resourceDict = parsed["ResourceDict"] as? Map<String, Any?>
        assertNotNull(resourceDict)
        val fontSet = resourceDict["FontSet"] as? List<Map<String, Any?>>
        assertNotNull(fontSet)
        assertEquals("Helvetica", fontSet[0]["Name"])

        // Decode through TextLayer
        val textLayout = TextLayer.decodeEngineData(engineDict, resourceDict)
        assertEquals("EngineData Test String", textLayout.text)
        assertEquals("Helvetica", textLayout.style?.font?.name)
        assertEquals(14.5f, textLayout.style?.fontSize)
    }

    @Test
    fun testNestedFoldersRoundtrip() {
        val width = 100
        val height = 100

        val dummyData = PixelData(10, 10, ByteArray(400) { 100.toByte() })

        val l1 = Layer(name = "Layer 1", top = 0, left = 0, bottom = 10, right = 10, imageData = dummyData)
        val l2 = Layer(name = "Layer 2", top = 10, left = 10, bottom = 20, right = 20, imageData = dummyData)
        val l3 = Layer(
            name = "Text Layer",
            top = 20, left = 20, bottom = 40, right = 80,
            text = LayerTextData(
                text = "Hello Inner",
                style = TextStyle(font = Font(name = "ArialMT"), fontSize = 12f)
            )
        )
        val l4 = Layer(name = "Layer 4", top = 40, left = 40, bottom = 50, right = 50, imageData = dummyData)
        val l5 = Layer(name = "Layer 5", top = 50, left = 50, bottom = 60, right = 60, imageData = dummyData)

        val innerFolder = Layer(
            name = "Inner Folder",
            children = mutableListOf(l3)
        )

        val outerFolder = Layer(
            name = "Outer Folder",
            children = mutableListOf(l2, innerFolder, l4)
        )

        val originalPsd = Psd(
            width = width,
            height = height,
            children = mutableListOf(l1, outerFolder, l5)
        )

        val bytes = KPsd.write(originalPsd, compress = false)
        val parsed = KPsd.read(bytes)

        assertEquals(width, parsed.width)
        assertEquals(height, parsed.height)
        assertEquals(3, parsed.children.size)

        assertEquals("Layer 1", parsed.children[0].name)
        assertEquals("Outer Folder", parsed.children[1].name)
        assertEquals("Layer 5", parsed.children[2].name)

        val parsedOuter = parsed.children[1]
        assertNotNull(parsedOuter.children)
        assertEquals(3, parsedOuter.children!!.size)

        assertEquals("Layer 2", parsedOuter.children!![0].name)
        assertEquals("Inner Folder", parsedOuter.children!![1].name)
        assertEquals("Layer 4", parsedOuter.children!![2].name)

        val parsedInner = parsedOuter.children!![1]
        assertNotNull(parsedInner.children)
        assertEquals(1, parsedInner.children!!.size)

        assertEquals("Text Layer", parsedInner.children!![0].name)
        assertEquals("Hello Inner", parsedInner.children!![0].text!!.text)
    }

    @Test
    fun testComplexTextStyleRuns() {
        val width = 100
        val height = 50

        val textData = LayerTextData(
            text = "Red Green Blue",
            style = TextStyle(font = Font("ArialMT"), fontSize = 14f),
            styleRuns = listOf(
                TextStyleRun(4, TextStyle(fillColor = Rgb(255, 0, 0))),
                TextStyleRun(6, TextStyle(fillColor = Rgb(0, 255, 0))),
                TextStyleRun(4, TextStyle(fillColor = Rgb(0, 0, 255)))
            ),
            paragraphStyleRuns = listOf(
                ParagraphStyleRun(14, ParagraphStyle(justification = Justification.CENTER))
            )
        )

        val textLayer = Layer(
            name = "StyledText",
            top = 10, left = 10, bottom = 40, right = 90,
            text = textData
        )

        val originalPsd = Psd(
            width = width,
            height = height,
            children = mutableListOf(textLayer)
        )

        val bytes = KPsd.write(originalPsd, compress = false)
        val parsed = KPsd.read(bytes)

        assertEquals(1, parsed.children.size)
        val parsedLayer = parsed.children[0]
        assertNotNull(parsedLayer.text)
        assertTrue(parsedLayer.text!!.text.startsWith("Red Green Blue"))

        val runs = parsedLayer.text!!.styleRuns
        assertNotNull(runs)
        assertTrue(runs.size >= 3)
        val colors = runs.mapNotNull { it.style.fillColor as? Rgb }
        assertTrue(colors.any { it.r == 255 && it.g == 0 && it.b == 0 })
        assertTrue(colors.any { it.r == 0 && it.g == 255 && it.b == 0 })
        assertTrue(colors.any { it.r == 0 && it.g == 0 && it.b == 255 })
    }

    @Test
    fun testPsdRoundtripLarge() {
        val width = 120
        val height = 100

        val bgData = ByteArray(width * height * 4) { 64.toByte() }
        val bgPixelData = PixelData(width, height, bgData)

        val bgLayer = Layer(
            name = "Background Large",
            top = 0, left = 0, bottom = height, right = width,
            imageData = bgPixelData
        )

        val originalPsd = Psd(
            width = width,
            height = height,
            children = mutableListOf(bgLayer)
        )

        val bytes = KPsd.write(originalPsd, compress = true, large = true)
        assertNotNull(bytes)
        assertTrue(bytes.size > 4)
        assertEquals('8'.code.toByte(), bytes[0])
        assertEquals('B'.code.toByte(), bytes[1])
        assertEquals('P'.code.toByte(), bytes[2])
        assertEquals('S'.code.toByte(), bytes[3])
        assertEquals(0, bytes[4].toInt())
        assertEquals(2, bytes[5].toInt())

        val parsedPsd = KPsd.read(bytes)
        assertNotNull(parsedPsd)
        assertEquals(width, parsedPsd.width)
        assertEquals(height, parsedPsd.height)
        assertEquals(1, parsedPsd.children.size)
        assertEquals("Background Large", parsedPsd.children[0].name)
    }



    @Test
    fun testLayerMasksAndBlendingRanges() {
        val width = 50
        val height = 50

        val mask = LayerMaskData(
            top = 10, left = 10, bottom = 40, right = 40,
            defaultColor = 0,
            disabled = false,
            positionRelativeToLayer = true
        )

        val ranges = BlendingRanges(
            compositeGrayBlendSource = byteArrayOf(0, 0, 255.toByte(), 255.toByte()),
            compositeGraphBlendDestinationRange = byteArrayOf(0, 0, 255.toByte(), 255.toByte()),
            ranges = listOf(
                BlendingRange(byteArrayOf(10, 20, 100, 120), byteArrayOf(5, 15, 90, 110))
            )
        )

        val layer = Layer(
            name = "Masked Layer",
            top = 0, left = 0, bottom = height, right = width,
            imageData = PixelData(width, height, ByteArray(width * height * 4) { 255.toByte() }),
            mask = mask,
            blendingRanges = ranges
        )

        val originalPsd = Psd(width = width, height = height, children = mutableListOf(layer))
        val bytes = KPsd.write(originalPsd, compress = false)
        val parsed = KPsd.read(bytes)

        assertEquals(1, parsed.children.size)
        val parsedLayer = parsed.children[0]

        assertNotNull(parsedLayer.mask)
        assertEquals(10, parsedLayer.mask!!.top)
        assertEquals(10, parsedLayer.mask!!.left)
        assertEquals(40, parsedLayer.mask!!.bottom)
        assertEquals(40, parsedLayer.mask!!.right)
        assertEquals(false, parsedLayer.mask!!.disabled)

        assertNotNull(parsedLayer.blendingRanges)
        assertEquals(1, parsedLayer.blendingRanges!!.ranges.size)
        assertEquals(10, parsedLayer.blendingRanges!!.ranges[0].sourceRange[0].toInt())
        assertEquals(5, parsedLayer.blendingRanges!!.ranges[0].destRange[0].toInt())
    }

    @Test
    fun testUnicodeLayerNames() {
        val width = 50
        val height = 50

        val originalPsd = Psd(
            width = width,
            height = height,
            children = mutableListOf(
                Layer(
                    name = "こんにちは layer",
                    top = 0, left = 0, bottom = height, right = width,
                    imageData = PixelData(width, height, ByteArray(width * height * 4) { 128.toByte() })
                )
            )
        )

        val bytes = KPsd.write(originalPsd, compress = false)
        val parsed = KPsd.read(bytes)

        assertEquals(1, parsed.children.size)
        assertEquals("こんにちは layer", parsed.children[0].name)
    }

    @Test
    fun testSectionPadding() {
        val width = 50
        val height = 50

        // Create a simple PSD with one layer
        val layer = Layer(
            name = "Test",
            top = 0, left = 0, bottom = height, right = width,
            imageData = PixelData(width, height, ByteArray(width * height * 4) { 128.toByte() })
        )
        val originalPsd = Psd(width = width, height = height, children = mutableListOf(layer))

        val bytes = KPsd.write(originalPsd, compress = false)

        // Find the start of image resources section
        // 8BPS (4) + Version (2) + Zeros (6) + Channels (2) + Height (4) + Width (4) + BPC (2) + Mode (2) = 26
        // Color mode data section: Length (4) = 30
        val colorModeDataLength = ((bytes[26].toInt() and 0xff) shl 24) or
                ((bytes[27].toInt() and 0xff) shl 16) or
                ((bytes[28].toInt() and 0xff) shl 8) or
                (bytes[29].toInt() and 0xff)
        val imageResourcesStart = 30 + colorModeDataLength

        // Image resources section: Length (4)
        val imageResourcesLength = ((bytes[imageResourcesStart].toInt() and 0xff) shl 24) or
                ((bytes[imageResourcesStart + 1].toInt() and 0xff) shl 16) or
                ((bytes[imageResourcesStart + 2].toInt() and 0xff) shl 8) or
                (bytes[imageResourcesStart + 3].toInt() and 0xff)

        // Section length itself should be a multiple of 4 (due to writeSection(4, writeTotalLength = true))
        assertEquals(0, imageResourcesLength % 4, "Image Resources section length should be multiple of 4")

        // Next section: Layer and Mask Info
        val layerInfoStart = imageResourcesStart + 4 + imageResourcesLength
        val layerInfoLength = ((bytes[layerInfoStart].toInt() and 0xff) shl 24) or
                ((bytes[layerInfoStart + 1].toInt() and 0xff) shl 16) or
                ((bytes[layerInfoStart + 2].toInt() and 0xff) shl 8) or
                (bytes[layerInfoStart + 3].toInt() and 0xff)

        assertEquals(0, layerInfoLength % 4, "Layer and Mask Info section length should be multiple of 4")

        // Image Data should follow
        val imageDataStart = layerInfoStart + 4 + layerInfoLength
        assertEquals('8'.code.toByte(), bytes[0]) // check we haven't corrupted the whole thing
        // PSD file should end after image data.
        // For 50x50, channels 0,1,2 (RGB), Compression (2) + RLE lengths (3*50*2=300) + data
        // Just checking that we can read it back is usually enough, but here we verified the padding.
        val parsed = KPsd.read(bytes)
        assertNotNull(parsed)
    }

    @Test
    fun testFolderWithImageAndText() = runBlocking {
        val tempDir = File("build/tmp/kpsd_test").apply { mkdirs() }

        val imagePath = File(tempDir, "base_folder.png").absolutePath
        val baseImage = BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
        val g = baseImage.createGraphics()
        g.color = Color.BLUE
        g.fillRect(0, 0, 200, 200)
        g.dispose()
        ImageIO.write(baseImage, "png", File(imagePath))

        // Create a custom image layer: 50x50 pixels, filled with green
        val imgBytes = ByteArray(50 * 50 * 4) { i ->
            when (i % 4) {
                0 -> 0.toByte()      // R
                1 -> 255.toByte()    // G
                2 -> 0.toByte()      // B
                else -> 255.toByte()  // A
            }
        }
        val imgLayer = Layer(
            name = "Image Layer",
            top = 10,
            left = 10,
            bottom = 60,
            right = 60,
            imageData = PixelData(50, 50, imgBytes)
        )

        // Create a text layer
        val textLayer = Layer(
            name = "My Text Layer",
            top = 80,
            left = 10,
            bottom = 120,
            right = 190,
            text = LayerTextData(
                text = "Hello Inside Folder",
                shapeType = TextShapeType.BOX,
                boxBounds = floatArrayOf(0f, 0f, 180f, 40f),
                transform = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 10.0, 80.0),
                left = 0f,
                top = 0f,
                right = 180f,
                bottom = 40f,
                style = TextStyle(
                    font = Font(name = "AnimeAce2.0BB"),
                    fontSize = 20f,
                    fillColor = Rgb(255, 255, 255)
                )
            )
        )

        // Create an open folder group containing both layers
        val folderLayer = Layer(
            name = "My Group Folder",
            opened = true,
            children = mutableListOf(imgLayer, textLayer)
        )

        // Create base background layer
        val bgBytes = ByteArray(200 * 200 * 4)
        val bgPixelData = PixelData(200, 200, bgBytes)
        val bgLayer = Layer(
            name = "Background",
            top = 0,
            left = 0,
            bottom = 200,
            right = 200,
            imageData = bgPixelData
        )

        // Assemble PSD
        val psd = Psd(
            width = 200,
            height = 200,
            children = mutableListOf(bgLayer, folderLayer),
            imageData = bgPixelData
        )

        // Write
        val psdBytes = KPsd.write(psd, compress = false)
        val psdFile = File(tempDir, "folder_img_text.psd")
        psdFile.writeBytes(psdBytes)

        // Read back and assert hierarchy
        val parsed = KPsd.read(psdBytes)
        assertEquals(2, parsed.children.size, "PSD should have 2 root layers (Background and Folder)")
        
        val parsedBg = parsed.children[0]
        assertEquals("Background", parsedBg.name)

        val parsedFolder = parsed.children[1]
        assertEquals("My Group Folder", parsedFolder.name)
        assertNotNull(parsedFolder.children)
        assertEquals(2, parsedFolder.children!!.size, "Folder should contain 2 child layers")

        val parsedImg = parsedFolder.children!![0]
        assertEquals("Image Layer", parsedImg.name)
        assertNotNull(parsedImg.imageData)
        assertEquals(50, parsedImg.imageData!!.width)
        assertEquals(50, parsedImg.imageData!!.height)

        val parsedText = parsedFolder.children!![1]
        assertEquals("My Text Layer", parsedText.name)
        assertNotNull(parsedText.text)
        assertEquals("Hello Inside Folder", parsedText.text!!.text)
    }

    @Test
    fun testFolderWithEffects() = runBlocking {
        val tempDir = File("build/tmp/kpsd_test").apply { mkdirs() }

        // Create a text layer with shadow and stroke effects
        val textLayer = Layer(
            name = "Effect Text Layer",
            top = 20,
            left = 20,
            bottom = 80,
            right = 180,
            text = LayerTextData(
                text = "Shadow & Stroke",
                shapeType = TextShapeType.BOX,
                boxBounds = floatArrayOf(0f, 0f, 160f, 60f),
                transform = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 20.0, 20.0),
                left = 0f,
                top = 0f,
                right = 160f,
                bottom = 60f,
                style = TextStyle(
                    font = Font(name = "AnimeAce2.0BB"),
                    fontSize = 24f,
                    fillColor = Rgb(255, 255, 255)
                )
            ),
            effects = LayerEffectsInfo(
                scale = 1f,
                stroke = listOf(
                    LayerEffectStroke(
                        size = UnitsValue(Units.PIXELS, 4f),
                        color = Rgb(0, 0, 0)
                    )
                ),
                dropShadow = listOf(
                    LayerEffectShadow(
                        size = UnitsValue(Units.PIXELS, 8f),
                        distance = UnitsValue(Units.PIXELS, 6f),
                        color = Rgb(0, 0, 0),
                        opacity = 0.6f
                    )
                )
            ),
            effectsOpen = true
        )

        // Create folder containing the text layer
        val folderLayer = Layer(
            name = "Folder with Effects",
            opened = true,
            children = mutableListOf(textLayer)
        )

        // Create base background layer
        val bgBytes = ByteArray(200 * 200 * 4)
        val bgPixelData = PixelData(200, 200, bgBytes)
        val bgLayer = Layer(
            name = "Background",
            top = 0,
            left = 0,
            bottom = 200,
            right = 200,
            imageData = bgPixelData
        )

        // Assemble PSD
        val psd = Psd(
            width = 200,
            height = 200,
            children = mutableListOf(bgLayer, folderLayer),
            imageData = bgPixelData
        )

        // Write
        val psdBytes = KPsd.write(psd, compress = false)
        val psdFile = File(tempDir, "folder_effects.psd")
        psdFile.writeBytes(psdBytes)

        // Read back and assert
        val parsed = KPsd.read(psdBytes)
        assertEquals(2, parsed.children.size)

        val parsedFolder = parsed.children[1]
        assertEquals("Folder with Effects", parsedFolder.name)
        assertNotNull(parsedFolder.children)
        assertEquals(1, parsedFolder.children!!.size)

        val parsedTextLayer = parsedFolder.children!![0]
        assertEquals("Effect Text Layer", parsedTextLayer.name)
        
        val parsedEffects = parsedTextLayer.effects
        assertNotNull(parsedEffects)
        assertNotNull(parsedEffects.stroke)
        assertEquals(1, parsedEffects.stroke!!.size)
        assertEquals(4f, parsedEffects.stroke!![0].size.value)

        assertNotNull(parsedEffects.dropShadow)
        assertEquals(1, parsedEffects.dropShadow!!.size)
        assertEquals(8f, parsedEffects.dropShadow!![0].size.value)
        assertEquals(6f, parsedEffects.dropShadow!![0].distance.value)
    }

    @Test
    fun testEffectsOnFolder() = runBlocking {
        val tempDir = File("build/tmp/kpsd_test").apply { mkdirs() }

        // Create a simple text layer
        val textLayer = Layer(
            name = "Child Layer",
            top = 20,
            left = 20,
            bottom = 80,
            right = 180,
            text = LayerTextData(
                text = "Inside Folder",
                shapeType = TextShapeType.BOX,
                boxBounds = floatArrayOf(0f, 0f, 160f, 60f),
                transform = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 20.0, 20.0),
                left = 0f,
                top = 0f,
                right = 160f,
                bottom = 60f,
                style = TextStyle(
                    font = Font(name = "AnimeAce2.0BB"),
                    fontSize = 24f,
                    fillColor = Rgb(255, 255, 255)
                )
            )
        )

        // Create folder containing the text layer, and apply effects to the FOLDER
        val folderLayer = Layer(
            name = "Folder with Effects Applied",
            opened = true,
            children = mutableListOf(textLayer),
            effects = LayerEffectsInfo(
                scale = 1f,
                stroke = listOf(
                    LayerEffectStroke(
                        size = UnitsValue(Units.PIXELS, 5f),
                        color = Rgb(0, 0, 0)
                    )
                ),
                dropShadow = listOf(
                    LayerEffectShadow(
                        size = UnitsValue(Units.PIXELS, 12f),
                        distance = UnitsValue(Units.PIXELS, 4f),
                        color = Rgb(0, 0, 0),
                        opacity = 0.5f
                    )
                )
            ),
            effectsOpen = true
        )

        // Create base background layer
        val bgBytes = ByteArray(200 * 200 * 4)
        val bgPixelData = PixelData(200, 200, bgBytes)
        val bgLayer = Layer(
            name = "Background",
            top = 0,
            left = 0,
            bottom = 200,
            right = 200,
            imageData = bgPixelData
        )

        // Assemble PSD
        val psd = Psd(
            width = 200,
            height = 200,
            children = mutableListOf(bgLayer, folderLayer),
            imageData = bgPixelData
        )

        // Write
        val psdBytes = KPsd.write(psd, compress = false)
        val psdFile = File(tempDir, "folder_with_effects_applied.psd")
        psdFile.writeBytes(psdBytes)

        // Read back and assert
        val parsed = KPsd.read(psdBytes)
        assertEquals(2, parsed.children.size)

        val parsedFolder = parsed.children[1]
        assertEquals("Folder with Effects Applied", parsedFolder.name)
        assertNotNull(parsedFolder.children)
        assertEquals(1, parsedFolder.children!!.size)

        // Verify folder layer itself has the effects
        val parsedEffects = parsedFolder.effects
        assertNotNull(parsedEffects, "Folder layer should have effects parsed back")
        assertNotNull(parsedEffects.stroke)
        assertEquals(1, parsedEffects.stroke!!.size)
        assertEquals(5f, parsedEffects.stroke!![0].size.value)

        assertNotNull(parsedEffects.dropShadow)
        assertEquals(1, parsedEffects.dropShadow!!.size)
        assertEquals(12f, parsedEffects.dropShadow!![0].size.value)
        assertEquals(4f, parsedEffects.dropShadow!![0].distance.value)

        // Verify child layer does not have effects
        val parsedChild = parsedFolder.children!![0]
        assertEquals("Child Layer", parsedChild.name)
        assertTrue(parsedChild.effects == null || (parsedChild.effects!!.stroke == null && parsedChild.effects!!.dropShadow == null))
    }

    @Test
    fun testPsdBuilderDSL() {
        val width = 200
        val height = 150
        val bgData = ByteArray(width * height * 4) { 128.toByte() }
        val bgPixelData = PixelData(width, height, bgData)

        val doc = psd(width = width, height = height) {
            layer("Background") {
                top = 0
                left = 0
                bottom = height
                right = width
                imageData = bgPixelData
            }

            group("Texts", opened = true) {
                
                textLayer("Hello Text", textValue = "Hello World") {
                    top = 10
                    left = 20
                    bottom = 50
                    right = 180
                    
                    transform(1.0, 0.0, 0.0, 1.0, 20.0, 10.0)
                    antiAlias = AntiAlias.SMOOTH
                    
                    style {
                        font(name = "ArialMT")
                        fontSize = 24.0f
                        fillColor(0, 0, 0)
                    }
                    
                    paragraphStyle {
                        justification = Justification.CENTER
                    }
                }
            }
        }

        // Verify DSL-created document parameters
        assertEquals(width, doc.width)
        assertEquals(height, doc.height)
        assertEquals(2, doc.children.size)

        val parsedBg = doc.children[0]
        assertEquals("Background", parsedBg.name)
        assertEquals(0, parsedBg.left)
        assertEquals(width, parsedBg.right)

        val parsedFolder = doc.children[1]
        assertEquals("Texts", parsedFolder.name)
        assertEquals(1, parsedFolder.children!!.size)

        val parsedTextLayer = parsedFolder.children!![0]
        assertEquals("Hello Text", parsedTextLayer.name)
        assertEquals("Hello World", parsedTextLayer.text!!.text)
        assertEquals(AntiAlias.SMOOTH, parsedTextLayer.text!!.antiAlias)
        assertEquals(Justification.CENTER, parsedTextLayer.text!!.paragraphStyle?.justification)
        assertEquals("ArialMT", parsedTextLayer.text!!.style?.font?.name)

        // Write and read back to make sure everything serializes/deserializes properly
        val bytes = KPsd.write(doc, compress = false)
        val readBack = KPsd.read(bytes)

        assertNotNull(readBack)
        assertEquals(2, readBack.children.size)
        assertEquals("Texts", readBack.children[1].name)
        assertEquals("Hello Text", readBack.children[1].children!![0].name)
    }
}

