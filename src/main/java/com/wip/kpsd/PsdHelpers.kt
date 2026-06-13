package com.wip.kpsd

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object PsdHelpers {

    val toBlendMode = mapOf(
        "pass" to BlendMode.PASS_THROUGH,
        "norm" to BlendMode.NORMAL,
        "diss" to BlendMode.DISSOLVE,
        "dark" to BlendMode.DARKEN,
        "mul " to BlendMode.MULTIPLY,
        "idiv" to BlendMode.COLOR_BURN,
        "lbrn" to BlendMode.LINEAR_BURN,
        "dkCl" to BlendMode.DARKER_COLOR,
        "lite" to BlendMode.LIGHTEN,
        "scrn" to BlendMode.SCREEN,
        "div " to BlendMode.COLOR_DODGE,
        "lddg" to BlendMode.LINEAR_DODGE,
        "lgCl" to BlendMode.LIGHTER_COLOR,
        "over" to BlendMode.OVERLAY,
        "sLit" to BlendMode.SOFT_LIGHT,
        "hLit" to BlendMode.HARD_LIGHT,
        "vLit" to BlendMode.VIVID_LIGHT,
        "lLit" to BlendMode.LINEAR_LIGHT,
        "pLit" to BlendMode.PIN_LIGHT,
        "hMix" to BlendMode.HARD_MIX,
        "diff" to BlendMode.DIFFERENCE,
        "smud" to BlendMode.EXCLUSION,
        "fsub" to BlendMode.SUBTRACT,
        "fdiv" to BlendMode.DIVIDE,
        "hue " to BlendMode.HUE,
        "sat " to BlendMode.SATURATION,
        "colr" to BlendMode.COLOR,
        "lum " to BlendMode.LUMINOSITY
    )

    val fromBlendMode = toBlendMode.entries.associate { it.value to it.key }

    val fromBlendModeDescriptor = mapOf(
        BlendMode.PASS_THROUGH to "pass",
        BlendMode.NORMAL to "Nrml",
        BlendMode.DISSOLVE to "Dslv",
        BlendMode.DARKEN to "Drkn",
        BlendMode.MULTIPLY to "Mltp",
        BlendMode.COLOR_BURN to "CBrn",
        BlendMode.LINEAR_BURN to "linearBurn",
        BlendMode.DARKER_COLOR to "darkerColor",
        BlendMode.LIGHTEN to "Lghn",
        BlendMode.SCREEN to "Scrn",
        BlendMode.COLOR_DODGE to "CDdg",
        BlendMode.LINEAR_DODGE to "linearDodge",
        BlendMode.LIGHTER_COLOR to "lighterColor",
        BlendMode.OVERLAY to "Ovrl",
        BlendMode.SOFT_LIGHT to "SftL",
        BlendMode.HARD_LIGHT to "HrdL",
        BlendMode.VIVID_LIGHT to "vividLight",
        BlendMode.LINEAR_LIGHT to "linearLight",
        BlendMode.PIN_LIGHT to "pinLight",
        BlendMode.HARD_MIX to "hardMix",
        BlendMode.DIFFERENCE to "Dfrn",
        BlendMode.EXCLUSION to "Xclu",
        BlendMode.SUBTRACT to "blendSubtraction",
        BlendMode.DIVIDE to "blendDivide",
        BlendMode.HUE to "H   ",
        BlendMode.SATURATION to "Strt",
        BlendMode.COLOR to "Clr ",
        BlendMode.LUMINOSITY to "Lmns"
    )

    fun clamp(value: Float, min: Float, max: Float): Float {
        return if (value < min) min else if (value > max) max else value
    }

    fun clamp(value: Int, min: Int, max: Int): Int {
        return if (value < min) min else if (value > max) max else value
    }

    fun hasAlpha(pixelData: PixelData): Boolean {
        val size = pixelData.width * pixelData.height * 4
        if (pixelData.data.size < size) return false
        for (i in 3 until size step 4) {
            if (pixelData.data[i].toInt() and 0xff != 255) {
                return true
            }
        }
        return false
    }

    fun offsetForChannel(channelId: ChannelID, cmyk: Boolean): Int {
        return when (channelId) {
            ChannelID.Color0 -> 0
            ChannelID.Color1 -> 1
            ChannelID.Color2 -> 2
            ChannelID.Color3 -> if (cmyk) 3 else channelId.value + 1
            ChannelID.Transparency -> if (cmyk) 4 else 3
            else -> channelId.value + 1
        }
    }

    fun copyChannelToPixelData(pixelData: PixelData, channel: ByteArray, offset: Int, step: Int) {
        val size = pixelData.width * pixelData.height
        val data = pixelData.data
        var p = offset
        for (i in 0 until size) {
            if (p < data.size && i < channel.size) {
                data[p] = channel[i]
            }
            p += step
        }
    }

    fun writeDataZipWithoutPrediction(pixelData: PixelData, offsets: IntArray): ByteArray {
        val size = pixelData.width * pixelData.height
        val channel = ByteArray(size)
        val buffers = mutableListOf<ByteArray>()
        var totalLength = 0

        for (offset in offsets) {
            for (i in 0 until size) {
                val p = i * 4 + offset
                channel[i] = if (p < pixelData.data.size) pixelData.data[p] else 0
            }

            val deflater = Deflater()
            deflater.setInput(channel)
            deflater.finish()

            val bos = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                bos.write(buf, 0, count)
            }
            deflater.end()

            val compressed = bos.toByteArray()
            buffers.add(compressed)
            totalLength += compressed.size
        }

        val result = ByteArray(totalLength)
        var destOffset = 0
        for (b in buffers) {
            System.arraycopy(b, 0, result, destOffset, b.size)
            destOffset += b.size
        }
        return result
    }

    fun readDataZip(
        compressed: ByteArray,
        pixelData: PixelData?,
        width: Int,
        height: Int,
        bitDepth: Int,
        step: Int,
        offset: Int,
        prediction: Boolean
    ) {
        if (bitDepth != 8) {
            throw UnsupportedOperationException("Bit depths other than 8 are not supported in Zip compression for MVP")
        }

        val inflater = Inflater()
        inflater.setInput(compressed)

        val bos = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inflater.finished() && !inflater.needsInput()) {
            val count = inflater.inflate(buf)
            if (count > 0) {
                bos.write(buf, 0, count)
            }
        }
        inflater.end()

        val decompressed = bos.toByteArray()

        if (pixelData != null && offset < step) {
            if (prediction) {
                decodePredicted(decompressed, width, height, 0x100)
            }
            copyChannelToPixelData(pixelData, decompressed, offset, step)
        }
    }

    private fun decodePredicted(data: ByteArray, width: Int, height: Int, mod: Int) {
        for (y in 0 until height) {
            val offset = y * width
            for (x in 1 until width) {
                val o = offset + x
                val prev = data[o - 1].toInt() and 0xff
                val curr = data[o].toInt() and 0xff
                data[o] = ((prev + curr) % mod).toByte()
            }
        }
    }

    fun writeDataRLE(pixelData: PixelData, channels: IntArray, large: Boolean): ByteArray {
        val width = pixelData.width
        val height = pixelData.height
        if (width == 0 || height == 0) return ByteArray(0)

        val stride = 4 * width
        var ol = 0
        val offsetLength = channels.size * (if (large) 4 else 2) * height
        val buffer = ByteArray(offsetLength + width * height * 4) // Safe upper bound buffer
        println("BUFFER ALLOCATED WITH SIZE: ${buffer.size}, offsetLength: $offsetLength, w*h*4: ${width*height*4}")
        var o = offsetLength

        for (offset in channels) {
            for (y in 0 until height) {
                val strideStart = y * stride
                val strideEnd = strideStart + stride
                val lastIndex = strideEnd + offset - 4
                val lastIndex2 = lastIndex - 4
                val startOffset = o

                var p = strideStart + offset
                while (p < strideEnd) {
                    if (p < lastIndex2) {
                        val value1 = if (p >= 0 && p < pixelData.data.size) pixelData.data[p] else 0
                        val value2 = if (p + 4 >= 0 && p + 4 < pixelData.data.size) pixelData.data[p + 4] else 0
                        val value3 = if (p + 8 >= 0 && p + 8 < pixelData.data.size) pixelData.data[p + 8] else 0

                        if (value1 == value2 && value1 == value3) {
                            var count = 3
                            p += 8
                            while (count < 128 && p < lastIndex && (if (p + 4 >= 0 && p + 4 < pixelData.data.size) pixelData.data[p + 4] else 0) == value1) {
                                count++
                                p += 4
                            }
                            buffer[o++] = (1 - count).toByte()
                            buffer[o++] = value1
                        } else {
                            val countIndex = o
                            var writeLast = true
                            var count = 1
                            buffer[o++] = 0 // placeholder
                            buffer[o++] = value1

                            var val1 = value1
                            var val2 = value2
                            var val3 = value3

                            p += 8
                            while (p < lastIndex && count < 128) {
                                p += 4
                                val1 = val2
                                val2 = val3
                                val3 = if (p >= 0 && p < pixelData.data.size) pixelData.data[p] else 0
                                if (val1 == val2 && val1 == val3) {
                                    p -= 12
                                    writeLast = false
                                    break
                                } else {
                                    count++
                                    buffer[o++] = val1
                                }
                            }

                            if (writeLast) {
                                if (count < 127) {
                                    buffer[o++] = val2
                                    buffer[o++] = val3
                                    count += 2
                                } else if (count < 128) {
                                    buffer[o++] = val2
                                    count++
                                    p -= 4
                                } else {
                                    p -= 8
                                }
                            }
                            buffer[countIndex] = (count - 1).toByte()
                        }
                    } else if (p == lastIndex) {
                        buffer[o++] = 0
                        buffer[o++] = if (p >= 0 && p < pixelData.data.size) pixelData.data[p] else 0
                    } else { // p == lastIndex2
                        buffer[o++] = 1
                        buffer[o++] = if (p >= 0 && p < pixelData.data.size) pixelData.data[p] else 0
                        buffer[o++] = if (p + 4 >= 0 && p + 4 < pixelData.data.size) pixelData.data[p + 4] else 0
                        p += 4
                    }
                    p += 4
                }

                val len = o - startOffset
                if (large) {
                    buffer[ol++] = ((len shr 24) and 0xff).toByte()
                    buffer[ol++] = ((len shr 16) and 0xff).toByte()
                }
                buffer[ol++] = ((len shr 8) and 0xff).toByte()
                buffer[ol++] = (len and 0xff).toByte()
            }
        }

        return buffer.copyOf(o)
    }

    fun readDataRLE(
        lengths: IntArray,
        readerBytes: ByteArray,
        readerOffset: Int,
        pixelData: PixelData?,
        width: Int,
        height: Int,
        step: Int,
        offsets: IntArray
    ): Int {
        val data = pixelData?.data
        val extraLimit = step - 1
        var li = 0
        var currOffset = readerOffset

        for (c in offsets.indices) {
            val offset = offsets[c]
            val extra = c > extraLimit || offset > extraLimit

            if (data == null || extra) {
                for (y in 0 until height) {
                    if (li < lengths.size) {
                        currOffset += lengths[li++]
                    }
                }
            } else {
                for (y in 0 until height) {
                    if (li >= lengths.size) break
                    val length = lengths[li++]
                    var i = 0
                    var x = 0
                    var p = y * (width * step) + offset

                    while (i < length) {
                        if (currOffset + i >= readerBytes.size) break
                        var header = readerBytes[currOffset + i++].toInt() and 0xff
                        if (header > 128) {
                            if (currOffset + i >= readerBytes.size) break
                            val value = readerBytes[currOffset + i++]
                            header = 256 - header
                            for (j in 0..header) {
                                if (x < width) {
                                    if (p < data.size) {
                                        data[p] = value
                                    }
                                    p += step
                                    x++
                                }
                            }
                        } else if (header < 128) {
                            for (j in 0..header) {
                                if (x < width) {
                                    if (currOffset + i >= readerBytes.size) break
                                    if (p < data.size) {
                                        data[p] = readerBytes[currOffset + i++]
                                    } else {
                                        i++
                                    }
                                    p += step
                                    x++
                                }
                            }
                        }
                    }
                    currOffset += length
                }
            }
        }
        return currOffset
    }
}
