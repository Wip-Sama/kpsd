package com.wip.kpsd

import java.awt.image.BufferedImage

/**
 * Entry point for the KPsd library, providing high-level utility functions to read,
 * write, and lazily decode Photoshop Document (PSD) files.
 */
object KPsd {
    /**
     * Parses a binary PSD byte array and returns a structured [Psd] model representation.
     *
     * @param bytes Binary content of the PSD file.
     * @param options Configuration options for reading (e.g. `useRawData`, `totalMemoryLimit`).
     * @return The parsed [Psd] document structure.
     * @throws IllegalStateException If the signature is invalid, memory limit is exceeded, or file is corrupt.
     */
    fun read(bytes: ByteArray, options: ReadOptions = ReadOptions()): Psd {
        return PsdReader(bytes, options).readPsd()
    }

    /**
     * Serializes a structured [Psd] model into a binary PSD byte array.
     *
     * @param psd The [Psd] document model to write.
     * @param compress True to compress the layer channel data using ZipWithoutPrediction. Default is false (RleCompressed).
     * @param large True to output PSD in PSD Large (PSB) format. Default is false.
     * @return Binary representation of the serialized PSD document.
     */
    fun write(psd: Psd, compress: Boolean = false, large: Boolean = false): ByteArray {
        val writer = PsdWriter()
        writer.large = large
        writer.writePsd(psd, compress)
        return writer.getWriterBuffer()
    }

    /**
     * Lazily decompresses pixel data for a layer (including its mask and real mask)
     * if the PSD was read using `useRawData = true`.
     *
     * @param layer The target layer to decompress pixels for.
     * @param memoryLimit Optional maximum bytes allowed for allocation.
     */
    fun decodeLayerPixels(layer: Layer, memoryLimit: Long? = null) {
        PsdReader.decodeLayerPixels(layer, memoryLimit)
    }

    /**
     * Returns the decompressed RGBA [PixelData] for the specified layer.
     */
    fun getLayerImageData(layer: Layer, memoryLimit: Long? = null): PixelData? {
        return PsdReader.getLayerImageData(layer, memoryLimit)
    }

    /**
     * Returns the decompressed grayscale [PixelData] for the specified layer's user mask.
     */
    fun getLayerMaskImageData(layer: Layer, memoryLimit: Long? = null): PixelData? {
        return PsdReader.getLayerMaskImageData(layer, memoryLimit)
    }

    /**
     * Returns the decompressed grayscale [PixelData] for the specified layer's real vector mask.
     */
    fun getLayerRealMaskImageData(layer: Layer, memoryLimit: Long? = null): PixelData? {
        return PsdReader.getLayerRealMaskImageData(layer, memoryLimit)
    }

    /**
     * Returns the decompressed RGBA [PixelData] for the composite flattened document.
     */
    fun getCompositeImageData(psd: Psd, memoryLimit: Long? = null): PixelData? {
        return PsdReader.getCompositeImageData(psd, memoryLimit)
    }

    /**
     * Returns a [BufferedImage] view of the layer's image pixels.
     */
    fun getLayerCanvas(layer: Layer, memoryLimit: Long? = null): BufferedImage? {
        return pixelDataToBufferedImage(getLayerImageData(layer, memoryLimit))
    }

    /**
     * Returns a [BufferedImage] view of the layer's user mask.
     */
    fun getLayerMaskCanvas(layer: Layer, memoryLimit: Long? = null): BufferedImage? {
        return pixelDataToBufferedImage(getLayerMaskImageData(layer, memoryLimit))
    }

    /**
     * Returns a [BufferedImage] view of the layer's real mask.
     */
    fun getLayerRealMaskCanvas(layer: Layer, memoryLimit: Long? = null): BufferedImage? {
        return pixelDataToBufferedImage(getLayerRealMaskImageData(layer, memoryLimit))
    }

    /**
     * Returns a [BufferedImage] view of the composite document image.
     */
    fun getCompositeCanvas(psd: Psd, memoryLimit: Long? = null): BufferedImage? {
        return pixelDataToBufferedImage(getCompositeImageData(psd, memoryLimit))
    }

    /**
     * Converts a [PixelData] RGBA byte buffer to a [BufferedImage] (TYPE_INT_ARGB).
     */
    fun pixelDataToBufferedImage(pixelData: PixelData?): BufferedImage? {
        if (pixelData == null || pixelData.width <= 0 || pixelData.height <= 0) return null
        val img = BufferedImage(pixelData.width, pixelData.height, BufferedImage.TYPE_INT_ARGB)
        val data = pixelData.data
        var p = 0
        for (y in 0 until pixelData.height) {
            for (x in 0 until pixelData.width) {
                val r = if (p < data.size) data[p].toInt() and 0xff else 0
                val g = if (p + 1 < data.size) data[p + 1].toInt() and 0xff else 0
                val b = if (p + 2 < data.size) data[p + 2].toInt() and 0xff else 0
                val a = if (p + 3 < data.size) data[p + 3].toInt() and 0xff else 255
                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                img.setRGB(x, y, argb)
                p += 4
            }
        }
        return img
    }

    /**
     * Converts a [BufferedImage] to an RGBA [PixelData] byte buffer.
     */
    fun bufferedImageToPixelData(img: BufferedImage): PixelData {
        val width = img.width
        val height = img.height
        val data = ByteArray(width * height * 4)
        var p = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = img.getRGB(x, y)
                val a = (argb ushr 24) and 0xff
                val r = (argb ushr 16) and 0xff
                val g = (argb ushr 8) and 0xff
                val b = argb and 0xff
                data[p] = r.toByte()
                data[p + 1] = g.toByte()
                data[p + 2] = b.toByte()
                data[p + 3] = a.toByte()
                p += 4
            }
        }
        return PixelData(width, height, data)
    }
}