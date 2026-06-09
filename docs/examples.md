# KPsd Documentation & Code Examples

`KPsd` is a Kotlin JVM library to read, parse, build, and write Photoshop Document (PSD) files.

This guide showcases both the traditional direct model instantiation API and the new declarative Kotlin DSL builder API.

---

## 1. Reading and Writing PSDs

The `KPsd` entry-point provides simple utility functions for reading and writing binary PSD data.

### Reading a PSD File
```kotlin
import com.wip.kpsd.KPsd
import java.io.File

// Read bytes from a file and parse it into a Psd model
val bytes = File("input.psd").readBytes()
val psd = KPsd.read(bytes)

println("PSD Size: ${psd.width}x${psd.height}")
println("Layer Count: ${psd.children.size}")
```

### Writing a PSD File
```kotlin
import com.wip.kpsd.KPsd
import java.io.File

val psd = // ... construct PSD document ...

// Serialize model to bytes (with optional compression)
val bytes = KPsd.write(psd, compress = true)
File("output.psd").writeBytes(bytes)
```

---

## 2. Using the Kotlin DSL Builder

The declarative Kotlin DSL allows you to construct deep PSD hierarchies in a clean, legible, nested block structure.

### Basic Document Layout
```kotlin
import com.wip.kpsd.*

val doc = psd(width = 800, height = 600) {
    layer("Background") {
        top = 0
        left = 0
        bottom = 600
        right = 800
        imageData = bgPixelData
    }
}
```

### Nested Groups (Folder Structures)
Folder nesting is modeled seamlessly via the `group` builder block:

```kotlin
val doc = psd(width = 1920, height = 1080) {
    group("Main Container") {
        opened = true // Keep folder expanded in Photoshop UI
        
        layer("Hero Image") {
            top = 100; left = 100; bottom = 500; right = 900
            imageData = heroPixelData
        }

        group("Sub Folder") {
            opened = false
            layer("Thumbnail") {
                top = 600; left = 100; bottom = 800; right = 300
                imageData = thumbData
            }
        }
    }
}
```

### Text Layers with Custom Styling
Adding text layers and custom styling with full font and alignment properties:

```kotlin
val doc = psd(width = 1000, height = 1000) {
    textLayer(name = "Heading Title", textValue = "Antigravity Power!") {
        top = 50
        left = 100
        bottom = 150
        right = 900
        
        // Anti-aliasing setting
        antiAlias = AntiAlias.SMOOTH
        
        // Inline font definition
        style {
            font(name = "Montserrat-Bold")
            fontSize = 36f
            fillColor(255, 255, 255) // White
            underline = true
        }

        // Paragraph justification
        paragraphStyle {
            justification = Justification.CENTER
        }
    }
}
```

### Applying Layer Effects (Strokes and Shadows)
Apply Photoshop effects directly using the nested `effects` block:

```kotlin
val doc = psd(width = 500, height = 500) {
    layer("Stroked Box") {
        top = 50; left = 50; bottom = 250; right = 250
        imageData = shapePixelData
        
        effects {
            scale = 1f
            disabled = false
            
            stroke {
                size = UnitsValue(Units.PIXELS, 4f)
                position = StrokePosition.OUTSIDE
                fillType = StrokeFillType.COLOR
                rgb(0, 0, 0) // Black stroke
                opacity = 0.8f
            }

            dropShadow {
                size = UnitsValue(Units.PIXELS, 12f) // blur size
                distance = UnitsValue(Units.PIXELS, 6f)
                angle = 120f
                rgb(0, 0, 0)
                opacity = 0.5f
                useGlobalLight = true
            }
        }
    }
}
```

### Layer Masks
Creating user layer masks:

```kotlin
val doc = psd(width = 400, height = 400) {
    layer("Masked Element") {
        top = 0; left = 0; bottom = 400; right = 400
        imageData = elementPixelData
        
        mask {
            top = 50
            left = 50
            bottom = 350
            right = 350
            defaultColor = 0
            disabled = false
            positionRelativeToLayer = true
            imageData = maskPixelData // alpha channel mask buffer
        }
    }
}
```
