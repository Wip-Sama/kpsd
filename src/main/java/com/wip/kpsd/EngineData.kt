package com.wip.kpsd

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object EngineData {

    private val floatKeys = setOf(
        "Axis", "XY", "Zone", "WordSpacing", "FirstLineIndent", "GlyphSpacing", "StartIndent", "EndIndent", "SpaceBefore",
        "SpaceAfter", "LetterSpacing", "Values", "GridSize", "GridLeading", "PointBase", "BoxBounds", "TransformPoint0", "TransformPoint1",
        "TransformPoint2", "FontSize", "Leading", "HorizontalScale", "VerticalScale", "BaselineShift", "Tsume",
        "OutlineWidth", "AutoLeading"
    )

    private val intArrays = setOf("RunLengthArray")

    fun serializeFloat(value: Double): String {
        var str = String.format(java.util.Locale.US, "%.5f", value)
        str = str.replace(Regex("(\\d)0+\$"), "$1")
        str = str.replace(Regex("^0+\\.([1-9])"), ".$1")
        str = str.replace(Regex("^-0+\\.0(\\d)"), "-.0$1")
        return str
    }

    private fun serializeInt(value: Int): String {
        return value.toString()
    }

    private fun serializeNumber(value: Number, key: String?): String {
        val isFloat = (key != null && floatKeys.contains(key)) || (value.toDouble() != value.toInt().toDouble())
        return if (isFloat) serializeFloat(value.toDouble()) else serializeInt(value.toInt())
    }

    fun serializeEngineData(data: Any?, condensed: Boolean = false): ByteArray {
        val bos = ByteArrayOutputStream()
        var indent = 0

        fun write(b: Byte) {
            bos.write(b.toInt())
        }

        fun writeString(str: String) {
            bos.write(str.toByteArray(StandardCharsets.US_ASCII))
        }

        fun writeIndent() {
            if (condensed) {
                writeString(" ")
            } else {
                for (i in 0 until indent) {
                    writeString("\t")
                }
            }
        }

        fun getKeys(map: Map<String, Any?>): List<String> {
            val keys = map.keys.toMutableList()
            val idx98 = keys.indexOf("98")
            if (idx98 != -1) {
                val k = keys.removeAt(idx98)
                keys.add(0, k)
            }
            val idx99 = keys.indexOf("99")
            if (idx99 != -1) {
                val k = keys.removeAt(idx99)
                keys.add(0, k)
            }
            return keys
        }

        fun writeStringByte(b: Int) {
            if (b == 40 || b == 41 || b == 92) { // '(', ')', '\'
                write(92) // '\'
            }
            write(b.toByte())
        }

        fun writeValue(value: Any?, key: String?, inProperty: Boolean) {
            fun writePrefix() {
                if (inProperty) {
                    writeString(" ")
                } else {
                    writeIndent()
                }
            }

            when (value) {
                null -> {
                    writePrefix()
                    writeString(if (condensed) "/nil" else "null")
                }
                is Number -> {
                    writePrefix()
                    writeString(serializeNumber(value, key))
                }
                is Boolean -> {
                    writePrefix()
                    writeString(if (value) "true" else "false")
                }
                is String -> {
                    writePrefix()
                    if ((key == "99" || key == "98") && value.startsWith("/")) {
                        writeString(value)
                    } else {
                        writeString("(")
                        write(0xfe.toByte())
                        write(0xff.toByte())
                        for (i in 0 until value.length) {
                            val code = value[i].code
                            writeStringByte((code shr 8) and 0xff)
                            writeStringByte(code and 0xff)
                        }
                        writeString(")")
                    }
                }
                is FloatArray -> {
                    writePrefix()
                    writeString("[")
                    val intArray = key != null && intArrays.contains(key)
                    for (x in value) {
                        writeString(" ")
                        writeString(if (intArray) serializeNumber(x.toInt(), key) else serializeFloat(x.toDouble()))
                    }
                    writeString(" ]")
                }
                is DoubleArray -> {
                    writePrefix()
                    writeString("[")
                    val intArray = key != null && intArrays.contains(key)
                    for (x in value) {
                        writeString(" ")
                        writeString(if (intArray) serializeNumber(x.toInt(), key) else serializeFloat(x))
                    }
                    writeString(" ]")
                }
                is IntArray -> {
                    writePrefix()
                    writeString("[")
                    for (x in value) {
                        writeString(" ")
                        writeString(serializeInt(x))
                    }
                    writeString(" ]")
                }
                is List<*> -> {
                    writePrefix()
                    val allNumbers = value.all { it is Number }
                    if (allNumbers && value.isNotEmpty()) {
                        writeString("[")
                        val intArray = key != null && intArrays.contains(key)
                        for (x in value) {
                            writeString(" ")
                            val num = x as Number
                            writeString(if (intArray) serializeNumber(num.toInt(), key) else serializeFloat(num.toDouble()))
                        }
                        writeString(" ]")
                    } else {
                        writeString("[")
                        if (!condensed) writeString("\n")
                        for (x in value) {
                            writeValue(x, key, false)
                            if (!condensed) writeString("\n")
                        }
                        writeIndent()
                        writeString("]")
                    }
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val map = value as Map<String, Any?>
                    if (inProperty && !condensed) writeString("\n")
                    writeIndent()
                    writeString("<<")
                    if (!condensed) writeString("\n")
                    indent++

                    for (k in getKeys(map)) {
                        writeIndent()
                        writeString("/$k")
                        writeValue(map[k], k, true)
                        if (!condensed) writeString("\n")
                    }

                    indent--
                    writeIndent()
                    writeString(">>")
                }
            }
        }

        if (condensed) {
            if (data is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val map = data as Map<String, Any?>
                for (k in getKeys(map)) {
                    writeIndent()
                    writeString("/$k")
                    writeValue(map[k], k, true)
                }
            }
        } else {
            writeString("\n\n")
            writeValue(data, null, false)
        }

        return bos.toByteArray()
    }

    fun parseEngineData(data: ByteArray): Map<String, Any?>? {
        var index = 0

        fun isWhitespace(b: Int): Boolean {
            return b == 32 || b == 10 || b == 13 || b == 9
        }

        fun isNumberChar(b: Int): Boolean {
            return (b in 48..57) || b == 46 || b == 45
        }

        fun skipWhitespace() {
            while (index < data.size && isWhitespace(data[index].toInt() and 0xff)) {
                index++
            }
        }

        fun getTextByte(): Int {
            var byte = data[index].toInt() and 0xff
            index++
            if (byte == 92) { // '\'
                byte = data[index].toInt() and 0xff
                index++
            }
            return byte
        }

        fun getText(): String {
            val sb = StringBuilder()
            if (data[index].toInt() == 41) { // ')'
                index++
                return ""
            }

            // String starts with UTF-16 BE BOM: 0xFE, 0xFF
            val bom1 = data[index].toInt() and 0xff
            val bom2 = data[index + 1].toInt() and 0xff
            if (bom1 != 0xFE || bom2 != 0xFF) {
                // Return raw ASCII if BOM is not present
                val start = index
                while (index < data.size && data[index].toInt() != 41) {
                    index++
                }
                val rawText = String(data, start, index - start, StandardCharsets.UTF_8)
                index++ // Skip ')'
                return rawText
            }

            index += 2
            while (index < data.size && data[index].toInt() != 41) {
                val high = getTextByte()
                val low = getTextByte()
                val char = (high shl 8) or low
                sb.append(char.toChar())
            }
            index++ // Skip ')'
            return sb.toString()
        }

        var root: Any? = null
        val stack = mutableListOf<Any?>()

        fun pop() {
            if (stack.isNotEmpty()) {
                stack.removeAt(stack.size - 1)
            }
        }

        fun pushValue(value: Any?) {
            if (stack.isEmpty()) return
            val top = stack.last()
            if (top is String) {
                // It is a property value
                val parentMap = stack[stack.size - 2] as MutableMap<String, Any?>
                parentMap[top] = value
                pop() // Pop property name
            } else if (top is MutableList<*>) {
                @Suppress("UNCHECKED_CAST")
                (top as MutableList<Any?>).add(value)
            }
        }

        fun pushContainer(value: Any) {
            if (stack.isEmpty()) {
                stack.add(value)
                root = value
            } else {
                pushValue(value)
                stack.add(value)
            }
        }

        fun pushProperty(name: String) {
            if (stack.isEmpty()) {
                pushContainer(mutableMapOf<String, Any?>())
            }
            val top = stack.last()
            if (top is String) {
                if (name == "nil") {
                    pushValue(null)
                } else {
                    pushValue("/$name")
                }
            } else if (top is MutableMap<*, *>) {
                stack.add(name)
            }
        }

        skipWhitespace()
        var dataLength = data.size
        while (dataLength > 0 && data[dataLength - 1].toInt() == 0) {
            dataLength--
        }

        while (index < dataLength) {
            val char = data[index].toInt() and 0xff
            if (char == 60 && index + 1 < dataLength && data[index + 1].toInt() == 60) { // '<<'
                index += 2
                pushContainer(mutableMapOf<String, Any?>())
            } else if (char == 62 && index + 1 < dataLength && data[index + 1].toInt() == 62) { // '>>'
                index += 2
                pop()
            } else if (char == 47) { // '/'
                index++
                val start = index
                while (index < dataLength && !isWhitespace(data[index].toInt() and 0xff)) {
                    index++
                }
                val name = String(data, start, index - start, StandardCharsets.US_ASCII)
                pushProperty(name)
            } else if (char == 40) { // '('
                index++
                pushValue(getText())
            } else if (char == 91) { // '['
                index++
                pushContainer(mutableListOf<Any?>())
            } else if (char == 93) { // ']'
                index++
                pop()
            } else if (char == 110 && index + 3 < dataLength &&
                data[index + 1].toInt() == 117 && data[index + 2].toInt() == 108 && data[index + 3].toInt() == 108) { // 'null'
                index += 4
                pushValue(null)
            } else if (char == 116 && index + 3 < dataLength &&
                data[index + 1].toInt() == 114 && data[index + 2].toInt() == 117 && data[index + 3].toInt() == 101) { // 'true'
                index += 4
                pushValue(true)
            } else if (char == 102 && index + 4 < dataLength &&
                data[index + 1].toInt() == 97 && data[index + 2].toInt() == 108 && data[index + 3].toInt() == 115 && data[index + 4].toInt() == 101) { // 'false'
                index += 5
                pushValue(false)
            } else if (isNumberChar(char)) {
                val start = index
                while (index < dataLength && isNumberChar(data[index].toInt() and 0xff)) {
                    index++
                }
                val valStr = String(data, start, index - start, StandardCharsets.US_ASCII)
                pushValue(valStr.toDoubleOrNull() ?: 0.0)
            } else {
                index++
            }
            skipWhitespace()
        }

        @Suppress("UNCHECKED_CAST")
        return root as? Map<String, Any?>
    }
}
