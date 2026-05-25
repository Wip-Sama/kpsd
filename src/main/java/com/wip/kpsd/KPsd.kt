package com.wip.kpsd

/**
 * Entry point for the KPsd library, providing high-level utility functions to read
 * and write Photoshop Document (PSD) files.
 */
object KPsd {
    /**
     * Parses a binary PSD byte array and returns a structured [Psd] model representation.
     *
     * @param bytes Binary content of the PSD file.
     * @return The parsed [Psd] document structure.
     * @throws IllegalStateException If the signature is invalid or file structure is corrupt.
     */
    fun read(bytes: ByteArray): Psd {
        return PsdReader(bytes).readPsd()
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
}