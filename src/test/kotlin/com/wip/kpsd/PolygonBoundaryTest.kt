package com.wip.kpsd

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font as AwtFont
import java.awt.Graphics2D
import java.awt.Polygon as AwtPolygon
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PolygonBoundaryTest {

    @Test
    fun testHorizontalSpanProperties() {
        // Asymmetric span: left offset is -120px, right offset is +60px
        val span = HorizontalSpan(leftOffset = -120f, rightOffset = 60f)
        assertEquals(180f, span.width, 0.001f)
        assertEquals(120f, span.symmetricWidth, 0.001f)

        // Symmetric span: -50px to +50px
        val symSpan = HorizontalSpan(leftOffset = -50f, rightOffset = 50f)
        assertEquals(100f, symSpan.width, 0.001f)
        assertEquals(100f, symSpan.symmetricWidth, 0.001f)

        // Degenerate/empty span: left is to the right of right
        val emptySpan = HorizontalSpan(leftOffset = 20f, rightOffset = -10f)
        assertEquals(0f, emptySpan.width, 0.001f)
        assertEquals(0f, emptySpan.symmetricWidth, 0.001f)
    }

    @Test
    fun testPolygonBoundaryWidth() {
        // Square polygon from (100, 100) to (300, 300)
        val square = listOf(
            Point2D.Double(100.0, 100.0),
            Point2D.Double(300.0, 100.0),
            Point2D.Double(300.0, 300.0),
            Point2D.Double(100.0, 300.0)
        )

        val visualCenter = Point2D.Double(200.0, 200.0)
        val boundary = PolygonBoundary(
            polygon = square,
            padding = 10f,
            visualCenter = visualCenter
        )

        val bounds = PsdBounds(left = 100f, top = 100f, right = 300f, bottom = 300f)

        // At center (y = 0 relative to visualCenter.y 200), width is 200 - (2 * 10) = 180
        val centerWidth = boundary.getAvailableWidth(0f, bounds)
        assertEquals(180f, centerWidth, 0.5f)

        // Horizontal span should be [-90, +90]
        val centerSpan = boundary.getAvailableSpan(0f, bounds)
        assertEquals(-90f, centerSpan.leftOffset, 0.5f)
        assertEquals(90f, centerSpan.rightOffset, 0.5f)
        assertEquals(180f, centerSpan.width, 0.5f)
        assertEquals(180f, centerSpan.symmetricWidth, 0.5f)

        // At y = 50 (scanline at y = 250), width is still 180 for a square
        val offCenterWidth = boundary.getAvailableWidth(50f, bounds)
        assertEquals(180f, offCenterWidth, 0.5f)

        // Outside polygon (y = 150 -> scanline at 350), width is 0
        val outsideWidth = boundary.getAvailableWidth(150f, bounds)
        assertEquals(0f, outsideWidth)
    }

    @Test
    fun testDiamondPolygonNarrowing() {
        // Diamond from (200, 100) top, (300, 200) right, (200, 300) bottom, (100, 200) left
        val diamond = listOf(
            Point2D.Double(200.0, 100.0),
            Point2D.Double(300.0, 200.0),
            Point2D.Double(200.0, 300.0),
            Point2D.Double(100.0, 200.0)
        )

        val visualCenter = Point2D.Double(200.0, 200.0)
        val boundary = PolygonBoundary(
            polygon = diamond,
            padding = 0f,
            visualCenter = visualCenter
        )

        val bounds = PsdBounds(left = 100f, top = 100f, right = 300f, bottom = 300f)

        // Center width at y = 0 -> 200px
        val centerWidth = boundary.getAvailableWidth(0f, bounds)
        assertEquals(200f, centerWidth, 1.0f)

        // Halfway up at y = -50 (y = 150) -> width should narrow to 100px
        val halfUpWidth = boundary.getAvailableWidth(-50f, bounds)
        assertEquals(100f, halfUpWidth, 1.0f)

        // Near top at y = -90 (y = 110) -> width should be 20px
        val topWidth = boundary.getAvailableWidth(-90f, bounds)
        assertEquals(20f, topWidth, 1.0f)
    }

    @Test
    fun testAsymmetricPolygonWidthConstraint() {
        // Asymmetric polygon from x=100 to x=500, but visualCenter is at x=200
        val asymmetric = listOf(
            Point2D.Double(100.0, 100.0),
            Point2D.Double(500.0, 100.0),
            Point2D.Double(500.0, 300.0),
            Point2D.Double(100.0, 300.0)
        )

        val visualCenter = Point2D.Double(200.0, 200.0)
        val boundary = PolygonBoundary(
            polygon = asymmetric,
            padding = 0f,
            visualCenter = visualCenter
        )

        val bounds = PsdBounds(left = 100f, top = 100f, right = 500f, bottom = 300f)

        // At center (y=0, scanline at y=200), total span from 100 to 500 is 400.
        // Span relative to x=200 is [-100, +300].
        // However, bounds clamps right to bounds.width/2 = 200, so span is [-100, +200].
        val span = boundary.getAvailableSpan(0f, bounds)
        assertEquals(-100f, span.leftOffset, 0.5f)
        assertEquals(200f, span.rightOffset, 0.5f)

        // Symmetric available width centered on x=200 is 2 * min(100, 200) = 200.
        val availableWidth = boundary.getAvailableWidth(0f, bounds)
        assertEquals(200f, availableWidth, 0.5f)
    }

    @Test
    fun testAutoCentroidComputation() {
        // Triangle from (0, 0), (90, 0), (0, 90)
        val triangle = listOf(
            Point2D.Double(0.0, 0.0),
            Point2D.Double(90.0, 0.0),
            Point2D.Double(0.0, 90.0)
        )

        val boundary = PolygonBoundary(
            polygon = triangle,
            padding = 0f
            // visualCenter is null, should be auto-computed to centroid (30, 30)
        )

        val vc = boundary.visualCenter
        assertNotNull(vc)
        assertEquals(30.0, vc.x, 1.0)
        assertEquals(30.0, vc.y, 1.0)
    }

    @Test
    fun testPolylabelComputation() {
        // Irregular polygon with speech bubble body and a tail pointing to the bottom-left:
        // Body: roughly a 100x100 box from (50, 50) to (150, 150)
        // Tail extends down to (10, 250)
        val balloonWithTail = listOf(
            Point2D.Double(50.0, 50.0),
            Point2D.Double(150.0, 50.0),
            Point2D.Double(150.0, 150.0),
            Point2D.Double(80.0, 150.0),
            Point2D.Double(10.0, 250.0), // Tail tip
            Point2D.Double(50.0, 140.0)
        )

        val centroid = PolygonBoundary.computeCentroid(balloonWithTail)
        assertNotNull(centroid)

        val polylabel = PolygonBoundary.computePoleOfInaccessibility(balloonWithTail)
        assertNotNull(polylabel)

        // The polylabel should land in the main balloon body (x in [70, 130], y in [70, 130])
        // and be significantly higher (lower y) than the centroid which gets pulled toward the tail.
        assertTrue(polylabel.y < centroid.y, "Polylabel ($polylabel) should be higher than centroid ($centroid)")
        assertTrue(polylabel.x in 60.0..140.0)
        assertTrue(polylabel.y in 60.0..140.0)

        // Verify boundary configured with usePolylabel = true uses polylabel
        val boundary = PolygonBoundary(
            polygon = balloonWithTail,
            usePolylabel = true
        )
        assertNotNull(boundary.visualCenter)
        assertEquals(polylabel.x, boundary.visualCenter!!.x, 0.01)
        assertEquals(polylabel.y, boundary.visualCenter!!.y, 0.01)
    }

    @Test
    fun testFromPointsFactory() {
        val boundary = PolygonBoundary.fromPoints(
            points = listOf(
                Pair(0, 0),
                Pair(100, 0),
                Pair(100, 100),
                Pair(0, 100)
            ),
            padding = 5f
        )
        assertEquals(4, boundary.polygon.size)
        assertEquals(5f, boundary.padding)
        assertNotNull(boundary.visualCenter)
        assertEquals(50.0, boundary.visualCenter!!.x, 0.5)
        assertEquals(50.0, boundary.visualCenter!!.y, 0.5)
    }

    @Test
    fun testTaperingPolygonExtremeYFixInFormatter() {
        // A trapezoid / funnel shape:
        // Wide at top (y = -40.. -20, width = 200px)
        // Narrow at middle/bottom (y = -10 .. 0, width = 40px)
        // If evaluated with extremeY, a line at y in [-20, -5] would evaluate at y = -20 (width 200),
        // but with minOf(topY, bottomY), it evaluates the narrower width at -5 (narrow width).
        val funnel = listOf(
            Point2D.Double(0.0, 0.0),     // top-left: wide
            Point2D.Double(200.0, 0.0),   // top-right: wide
            Point2D.Double(120.0, 100.0), // bottom-right: narrow
            Point2D.Double(80.0, 100.0)   // bottom-left: narrow
        )

        val boundary = PolygonBoundary(
            polygon = funnel,
            padding = 0f,
            visualCenter = Point2D.Double(100.0, 50.0)
        )

        val bounds = PsdBounds(0f, 0f, 200f, 100f)

        // Near top at y = -40 (canvas y = 10): width is 184
        val topW = boundary.getAvailableWidth(-40f, bounds)
        assertEquals(184f, topW, 2.0f)

        // Mid-point at y = 0 (canvas y = 50): width is 120
        val midW = boundary.getAvailableWidth(0f, bounds)
        assertEquals(120f, midW, 1.0f)

        // Near bottom at y = 40 (canvas y = 90): width is 56
        val bottomW = boundary.getAvailableWidth(40f, bounds)
        assertEquals(56f, bottomW, 2.0f)

        // Format text with short words inside this funnel boundary
        val text = "Top wide middle narrows down to the smaller base of this funnel"
        val style = TextStyle(
            font = Font(name = "ArialMT"),
            fontSize = 10f
        )

        val formatted = TextFormatter.formatTextInternal(
            text = text,
            style = style,
            bounds = bounds,
            shape = boundary,
            wordBreak = WordBreak.NONE,
            alignment = VerticalAlignment.TOP
        )

        // The text should format without horizontal overflow
        assertTrue(formatted.fitsHorizontally, "Text should fit horizontally when sampled at minOf(topY, bottomY)")

        // Verify that text wraps into multiple lines due to tapering
        val lines = formatted.text.split("\r")
        assertTrue(lines.size >= 2, "Expected text to wrap into multiple lines due to tapering")

        // Also verify AutoFit correctly scales down a longer string to fit inside the bottleneck
        val longText = "This longer text must scale down to fit into the bottleneck"
        val resolvedSize = TextFormatter.resolveFontSize(
            text = longText,
            baseStyle = TextStyle(font = Font(name = "ArialMT")),
            bounds = bounds,
            shape = boundary,
            autoFit = AutoFit(minSize = 6f, maxSize = 20f),
            wordBreak = WordBreak.NONE,
            alignment = VerticalAlignment.TOP
        )
        assertTrue(resolvedSize < 20f && resolvedSize >= 6f, "AutoFit should reduce font size to fit tapering bottleneck (got $resolvedSize)")
    }

    @Test
    fun testFitToBoundaryTextLayerBuilder() {
        val square = listOf(
            Point2D.Double(100.0, 200.0),
            Point2D.Double(300.0, 200.0),
            Point2D.Double(300.0, 400.0),
            Point2D.Double(100.0, 400.0)
        )

        val visualCenter = Point2D.Double(200.0, 300.0)
        val boundary = PolygonBoundary(
            polygon = square,
            padding = 0f,
            visualCenter = visualCenter
        )

        // Test without rotation
        val builderNoRot = TextLayerBuilder(name = "Test Text", text = "Hello Boundary")
        val layoutNoRot = builderNoRot.fitToBoundary(
            boundary = boundary,
            rotation = 0.0,
            paddingPercentage = 0.05f
        )

        assertEquals(200, layoutNoRot.boxWidth)
        assertEquals(200, layoutNoRot.boxHeight)
        assertEquals(100, layoutNoRot.left)
        assertEquals(200, layoutNoRot.top)
        assertEquals(300, layoutNoRot.right)
        assertEquals(400, layoutNoRot.bottom)
        assertEquals(100.0, layoutNoRot.tx, 0.001)
        assertEquals(200.0, layoutNoRot.ty, 0.001)

        val layerNoRot = builderNoRot.build()
        assertEquals(TextShapeType.BOX, layerNoRot.text?.shapeType)
        assertNotNull(layerNoRot.text?.boxBounds)
        assertEquals(0f, layerNoRot.text!!.boxBounds!![0])
        assertEquals(0f, layerNoRot.text!!.boxBounds!![1])
        assertEquals(200f, layerNoRot.text!!.boxBounds!![2])
        assertEquals(200f, layerNoRot.text!!.boxBounds!![3])

        // Verify padding percentage was applied: min(200, 200) * 0.05 = 10f
        val appliedShape = layerNoRot.text?.style?.let { builderNoRot.boundaryShape } as? PolygonBoundary
        assertNotNull(appliedShape)
        assertEquals(10f, appliedShape.padding, 0.01f)

        // Test with 45 degree rotation
        val builderRot = TextLayerBuilder(name = "Rotated Text", text = "Rotated Hello")
        val layoutRot = builderRot.fitToBoundary(
            boundary = boundary,
            rotation = 45.0
        )

        val theta = Math.toRadians(45.0)
        val cos = cos(theta)
        val sin = sin(theta)
        val expectedTx = 200.0 - (cos * 100.0 - sin * 100.0)
        val expectedTy = 300.0 - (sin * 100.0 + cos * 100.0)

        assertEquals(expectedTx, layoutRot.tx, 0.001)
        assertEquals(expectedTy, layoutRot.ty, 0.001)

        val layerRot = builderRot.build()
        assertNotNull(layerRot.text?.transform)
        assertEquals(cos, layerRot.text!!.transform!![0], 0.0001)
        assertEquals(sin, layerRot.text!!.transform!![1], 0.0001)
        assertEquals(-sin, layerRot.text!!.transform!![2], 0.0001)
        assertEquals(cos, layerRot.text!!.transform!![3], 0.0001)
        assertEquals(expectedTx, layerRot.text!!.transform!![4], 0.001)
        assertEquals(expectedTy, layerRot.text!!.transform!![5], 0.001)
    }

    @Test
    fun testPsdWithPolygonBoundaryRoundtrip() {
        val square = listOf(
            Point2D.Double(50.0, 50.0),
            Point2D.Double(250.0, 50.0),
            Point2D.Double(250.0, 250.0),
            Point2D.Double(50.0, 250.0)
        )
        val boundary = PolygonBoundary(polygon = square)

        val doc = psd(width = 300, height = 300) {
            textLayer(name = "Speech Balloon", textValue = "Speech balloon content in native KPsd!") {
                fitToBoundary(
                    boundary = boundary,
                    rotation = 0.0,
                    paddingPercentage = 0.05f
                )
                style {
                    font("ArialMT")
                    fontSize = 18f
                    fillColor(0, 0, 0)
                }
                paragraphStyle {
                    justification = Justification.CENTER
                }
            }
        }

        assertEquals(1, doc.children.size)
        val textLayer = doc.children[0]
        assertEquals("Speech Balloon", textLayer.name)
        assertNotNull(textLayer.text)
        assertEquals(TextShapeType.BOX, textLayer.text!!.shapeType)
        assertEquals(50, textLayer.left)
        assertEquals(50, textLayer.top)
        assertEquals(250, textLayer.right)
        assertEquals(250, textLayer.bottom)

        // Verify serializing to bytes and parsing back
        val bytes = KPsd.write(doc)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        val parsed = KPsd.read(bytes)
        assertNotNull(parsed)
        assertEquals(1, parsed.children.size)
        val parsedLayer = parsed.children[0]
        assertEquals("Speech Balloon", parsedLayer.name)
        assertNotNull(parsedLayer.text)
        assertEquals(TextShapeType.BOX, parsedLayer.text!!.shapeType)
    }

    @Test
    fun testGenerateVisualValidationPsd() {
        val outDir = File("build/test_psds")
        outDir.mkdirs()

        val canvasWidth = 1800
        val canvasHeight = 1200

        // Create high-res diagnostic background image
        val bgImage = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = bgImage.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Fill background
        g2d.color = Color(248, 250, 253)
        g2d.fillRect(0, 0, canvasWidth, canvasHeight)

        // Header
        g2d.color = Color(30, 41, 59)
        g2d.font = AwtFont("SansSerif", AwtFont.BOLD, 26)
        g2d.drawString("KPsd v1.3.0 - PolygonBoundary & Layout Verification Test Suite", 40, 48)

        g2d.color = Color(100, 116, 139)
        g2d.font = AwtFont("SansSerif", AwtFont.PLAIN, 15)
        g2d.drawString("Visualizing Polylabel centering, tapering sampling, asymmetric spans, and rotated transformation matrices", 40, 75)

        // Quadrant divider grid
        g2d.color = Color(226, 232, 240)
        g2d.stroke = BasicStroke(2f)
        g2d.drawLine(canvasWidth / 2, 85, canvasWidth / 2, canvasHeight - 20)
        g2d.drawLine(20, 600, canvasWidth - 20, 600)

        // Helper to draw polygon and diagnostic markers
        fun drawDiagnosticShape(
            title: String,
            subtitle: String,
            poly: List<Point2D>,
            layout: EffectiveTextLayout,
            titleX: Int,
            titleY: Int
        ) {
            // Draw title and subtitle
            g2d.color = Color(15, 23, 42)
            g2d.font = AwtFont("SansSerif", AwtFont.BOLD, 18)
            g2d.drawString(title, titleX, titleY)

            g2d.color = Color(100, 116, 139)
            g2d.font = AwtFont("SansSerif", AwtFont.PLAIN, 13)
            g2d.drawString(subtitle, titleX, titleY + 22)

            // Draw filled polygon
            val xPoints = poly.map { it.x.toInt() }.toIntArray()
            val yPoints = poly.map { it.y.toInt() }.toIntArray()
            val awtPoly = AwtPolygon(xPoints, yPoints, poly.size)

            g2d.color = Color(236, 242, 255, 140)
            g2d.fillPolygon(awtPoly)

            // Draw polygon contour outline
            g2d.color = Color(219, 39, 119) // Vibrant Pink/Magenta
            g2d.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2d.drawPolygon(awtPoly)

            // Draw effective text bounding box (dashed cyan)
            g2d.color = Color(14, 165, 233, 160)
            g2d.stroke = BasicStroke(
                1.5f,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10f,
                floatArrayOf(6f, 6f),
                0f
            )
            g2d.drawRect(layout.left, layout.top, layout.boxWidth, layout.boxHeight)

            // Draw visual center crosshair (red)
            val vc = layout.boundaryShape.visualCenter ?: layout.boundaryShape.anchorCenter
            if (vc != null) {
                val cx = vc.x.toInt()
                val cy = vc.y.toInt()
                g2d.color = Color(239, 68, 68)
                g2d.stroke = BasicStroke(2f)
                g2d.drawLine(cx - 15, cy, cx + 15, cy)
                g2d.drawLine(cx, cy - 15, cx, cy + 15)
                g2d.drawOval(cx - 5, cy - 5, 10, 10)

                g2d.color = Color(185, 28, 28)
                g2d.font = AwtFont("Monospaced", AwtFont.BOLD, 12)
                g2d.drawString("VC (${cx}, ${cy})", cx + 18, cy + 5)
            }
        }

        // ==========================================
        // 1. Top-Left: Speech Balloon with Tail (Polylabel)
        // ==========================================
        val balloon1Points = mutableListOf<Point2D>()
        val b1Cx = 450.0
        val b1Cy = 310.0
        val b1Rx = 260.0
        val b1Ry = 160.0

        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            if (deg in 210..240) {
                if (deg == 210) {
                    // Start of speech tail
                    balloon1Points.add(Point2D.Double(b1Cx + b1Rx * cos(rad), b1Cy + b1Ry * sin(rad)))
                    // Tail tip
                    balloon1Points.add(Point2D.Double(140.0, 560.0))
                }
            } else {
                balloon1Points.add(Point2D.Double(b1Cx + b1Rx * cos(rad), b1Cy + b1Ry * sin(rad)))
            }
        }
        val boundary1 = PolygonBoundary(polygon = balloon1Points, usePolylabel = true)
        val layout1 = EffectiveTextLayout.compute(boundary1, paddingPercentage = 0.05f)
        drawDiagnosticShape(
            title = "1. Speech Balloon with Tail (Polylabel)",
            subtitle = "Pole of inaccessibility places text inside bubble body, ignoring tail pull",
            poly = balloon1Points,
            layout = layout1,
            titleX = 50,
            titleY = 120
        )

        // ==========================================
        // 2. Top-Right: Tapering Funnel (extremeY fix)
        // ==========================================
        val funnelPoints = listOf(
            Point2D.Double(1050.0, 180.0),
            Point2D.Double(1650.0, 180.0),
            Point2D.Double(1440.0, 530.0),
            Point2D.Double(1260.0, 530.0)
        )
        val boundary2 = PolygonBoundary(polygon = funnelPoints)
        val layout2 = EffectiveTextLayout.compute(boundary2, paddingPercentage = 0.05f)
        drawDiagnosticShape(
            title = "2. Tapering Funnel (extremeY Sampling Fix)",
            subtitle = "Samples available width at minOf(topY, bottomY) to eliminate diagonal edge clipping",
            poly = funnelPoints,
            layout = layout2,
            titleX = 950,
            titleY = 120
        )

        // ==========================================
        // 3. Bottom-Left: Asymmetric Bubble (HorizontalSpan)
        // ==========================================
        val asymPoints = listOf(
            Point2D.Double(120.0, 720.0),
            Point2D.Double(460.0, 690.0),
            Point2D.Double(660.0, 740.0),
            Point2D.Double(660.0, 1020.0),
            Point2D.Double(480.0, 1110.0),
            Point2D.Double(180.0, 1110.0),
            Point2D.Double(100.0, 930.0)
        )
        val boundary3 = PolygonBoundary(
            polygon = asymPoints,
            visualCenter = Point2D.Double(460.0, 900.0)
        )
        val layout3 = EffectiveTextLayout.compute(boundary3, paddingPercentage = 0.05f)
        drawDiagnosticShape(
            title = "3. Asymmetric Bubble (HorizontalSpan)",
            subtitle = "Left extends 340px, right extends 200px. Text is centered without right-side clipping",
            poly = asymPoints,
            layout = layout3,
            titleX = 50,
            titleY = 635
        )

        // ==========================================
        // 4. Bottom-Right: Rotated 25° Balloon
        // ==========================================
        val rotAngle = 25.0
        val rotRad = Math.toRadians(rotAngle)
        val b4Cx = 1350.0
        val b4Cy = 900.0
        val b4Rx = 270.0
        val b4Ry = 140.0
        val rotPoints = mutableListOf<Point2D>()

        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            val lx = b4Rx * cos(rad)
            val ly = b4Ry * sin(rad)
            val rx = b4Cx + (lx * cos(rotRad) - ly * sin(rotRad))
            val ry = b4Cy + (lx * sin(rotRad) + ly * cos(rotRad))
            rotPoints.add(Point2D.Double(rx, ry))
        }

        val boundary4 = PolygonBoundary(polygon = rotPoints)
        val layout4 = EffectiveTextLayout.compute(boundary4, rotation = rotAngle, paddingPercentage = 0.05f)
        drawDiagnosticShape(
            title = "4. Rotated 25° Balloon (Transformation Matrix)",
            subtitle = "fitToBoundary automatically derived optimal box bounds and 25° affine rotation matrix",
            poly = rotPoints,
            layout = layout4,
            titleX = 950,
            titleY = 635
        )

        g2d.dispose()

        // Convert BufferedImage to PixelData for PSD background layer
        val bgData = ByteArray(canvasWidth * canvasHeight * 4)
        for (y in 0 until canvasHeight) {
            for (x in 0 until canvasWidth) {
                val argb = bgImage.getRGB(x, y)
                val idx = (y * canvasWidth + x) * 4
                bgData[idx] = ((argb shr 16) and 0xFF).toByte()     // R
                bgData[idx + 1] = ((argb shr 8) and 0xFF).toByte()  // G
                bgData[idx + 2] = (argb and 0xFF).toByte()         // B
                bgData[idx + 3] = ((argb shr 24) and 0xFF).toByte() // A
            }
        }
        val bgPixelData = PixelData(canvasWidth, canvasHeight, bgData)

        // Build native PSD document with background layer and live Photoshop text layers
        val doc = psd(width = canvasWidth, height = canvasHeight) {
            layer("Diagnostic Contours & Crosshairs") {
                top = 0
                left = 0
                bottom = canvasHeight
                right = canvasWidth
                imageData = bgPixelData
            }

            group("Validated Speech Balloon Layers", opened = true) {
                // Quadrant 1 Text Layer
                textLayer(
                    name = "1. Speech Balloon (Polylabel)",
                    textValue = "Speech balloon with tail! Notice that Polylabel positions text inside the bubble body rather than drifting toward the tail pointer."
                ) {
                    fitToBoundary(boundary = boundary1, paddingPercentage = 0.06f)
                    verticalAlignment = VerticalAlignment.CENTER
                    style {
                        font("ArialMT")
                        fontSize = 18f
                        fillColor(15, 23, 42)
                        autoFit = AutoFit(minSize = 12f, maxSize = 22f)
                    }
                    paragraphStyle {
                        justification = Justification.CENTER
                    }
                }

                // Quadrant 2 Text Layer
                textLayer(
                    name = "2. Tapering Funnel (extremeY)",
                    textValue = "Tapering Funnel Contour. KPsd samples available width at both top and bottom edges (minOf(topY, bottomY)), guaranteeing that text wraps cleanly without clipping through narrowing diagonal borders."
                ) {
                    fitToBoundary(boundary = boundary2, paddingPercentage = 0.05f)
                    verticalAlignment = VerticalAlignment.CENTER
                    style {
                        font("ArialMT")
                        fontSize = 18f
                        fillColor(15, 23, 42)
                        autoFit = AutoFit(minSize = 10f, maxSize = 22f)
                    }
                    paragraphStyle {
                        justification = Justification.CENTER
                    }
                }

                // Quadrant 3 Text Layer
                textLayer(
                    name = "3. Asymmetric Bubble (HorizontalSpan)",
                    textValue = "Asymmetric Bubble Contour. HorizontalSpan symmetrically constrains text relative to the visual center, preventing overflow on the closer right wall."
                ) {
                    fitToBoundary(boundary = boundary3, paddingPercentage = 0.05f)
                    verticalAlignment = VerticalAlignment.CENTER
                    style {
                        font("ArialMT")
                        fontSize = 18f
                        fillColor(15, 23, 42)
                        autoFit = AutoFit(minSize = 11f, maxSize = 22f)
                    }
                    paragraphStyle {
                        justification = Justification.CENTER
                    }
                }

                // Quadrant 4 Text Layer
                textLayer(
                    name = "4. Rotated 25deg Balloon",
                    textValue = "Rotated Speech Balloon. fitToBoundary automatically computed the 25 degree rotation matrix, centered the text box on the visual center, and wrapped the text smoothly."
                ) {
                    fitToBoundary(boundary = boundary4, rotation = rotAngle, paddingPercentage = 0.06f)
                    verticalAlignment = VerticalAlignment.CENTER
                    style {
                        font("ArialMT")
                        fontSize = 18f
                        fillColor(15, 23, 42)
                        autoFit = AutoFit(minSize = 12f, maxSize = 22f)
                    }
                    paragraphStyle {
                        justification = Justification.CENTER
                    }
                }
            }
        }

        // Write validation PSD file
        val psdBytes = KPsd.write(doc, compress = true)
        assertNotNull(psdBytes)
        assertTrue(psdBytes.isNotEmpty())

        val psdFile = File(outDir, "polygon_boundary_validation.psd")
        psdFile.writeBytes(psdBytes)
        println("Generated test PSD at: ${psdFile.absolutePath}")

        // Render formatted text layers onto preview image so user can inspect directly
        val previewImage = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
        val pg2d = previewImage.createGraphics()
        pg2d.drawImage(bgImage, 0, 0, null)
        pg2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        pg2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val textGroup = doc.children[1]
        for (layer in textGroup.children ?: emptyList()) {
            val textData = layer.text ?: continue
            val style = textData.style ?: continue
            val fontSize = style.fontSize ?: 16f
            val font = AwtFont("SansSerif", AwtFont.BOLD, fontSize.toInt())
            pg2d.font = font
            pg2d.color = Color(15, 23, 42)

            val transform = textData.transform
            val origTransform = pg2d.transform
            if (transform != null && transform.size >= 6) {
                val at = AffineTransform(
                    transform[0],
                    transform[1],
                    transform[2],
                    transform[3],
                    transform[4],
                    transform[5]
                )
                pg2d.transform = at
            }

            val lines = textData.text.split("\r")
            val fm = pg2d.fontMetrics
            val leading = fontSize * 1.25f
            val totalTextHeight = (lines.size - 1) * leading + fm.ascent + fm.descent
            val boxWidth = textData.boxBounds?.getOrNull(2) ?: (layer.right - layer.left).toFloat()
            val boxHeight = textData.boxBounds?.getOrNull(3) ?: (layer.bottom - layer.top).toFloat()

            var startY = (boxHeight - totalTextHeight) / 2f + fm.ascent
            for (line in lines) {
                val lineWidth = fm.stringWidth(line)
                val startX = (boxWidth - lineWidth) / 2f
                pg2d.drawString(line, startX, startY)
                startY += leading
            }

            pg2d.transform = origTransform
        }
        pg2d.dispose()

        // Write companion PNG preview for instant visual verification
        val pngFile = File(outDir, "polygon_boundary_validation_preview.png")
        ImageIO.write(previewImage, "png", pngFile)
        println("Generated visual preview at: ${pngFile.absolutePath}")

        // Verify roundtrip read back
        val parsed = KPsd.read(psdBytes)
        assertNotNull(parsed)
        assertEquals(canvasWidth, parsed.width)
        assertEquals(canvasHeight, parsed.height)
        assertEquals(2, parsed.children.size)

        val folder = parsed.children[1]
        assertEquals("Validated Speech Balloon Layers", folder.name)
        assertNotNull(folder.children)
        assertEquals(4, folder.children!!.size)
    }
}
