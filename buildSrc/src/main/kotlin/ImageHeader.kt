import java.io.File

/** Format and dimensions read straight from an image file's header. */
data class ImageInfo(val format: String, val width: Int, val height: Int) {
    val aspectRatio: Double get() = width.toDouble() / height.toDouble()
}

/**
 * Minimal image header reader.
 *
 * Deliberately has no image-library dependency: the build must be able to say "this is not an
 * image" about a file that no decoder would accept, and a decoder that throws on malformed
 * input is a worse tool for that than a few lines of byte arithmetic.
 *
 * Only formats Android can decode from a drawable resource are recognised.
 */
object ImageHeader {

    val SUPPORTED_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "gif")

    /** Returns null when the bytes are not one of the supported formats. */
    fun read(file: File): ImageInfo? {
        if (!file.isFile || file.length() < MINIMUM_PLAUSIBLE_SIZE) return null
        val bytes = file.readBytes()
        return readJpeg(bytes) ?: readPng(bytes) ?: readWebp(bytes) ?: readGif(bytes)
    }

    private fun readJpeg(bytes: ByteArray): ImageInfo? {
        if (bytes.size < 4 || bytes.u8(0) != 0xFF || bytes.u8(1) != 0xD8) return null
        var index = 2
        while (index + 9 < bytes.size) {
            if (bytes.u8(index) != 0xFF) {
                index++
                continue
            }
            val marker = bytes.u8(index + 1)
            when {
                // Start-of-frame markers carry the dimensions. The baseline, extended,
                // progressive and lossless variants are all laid out the same way; the ranges
                // skip DHT (C4), JPG (C8) and DAC (CC), which are not frame headers.
                marker in 0xC0..0xC3 || marker in 0xC5..0xC7 ||
                    marker in 0xC9..0xCB || marker in 0xCD..0xCF -> {
                    return ImageInfo("JPEG", width = bytes.u16(index + 7), height = bytes.u16(index + 5))
                }

                marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7 -> index += 2
                marker == 0xD9 || marker == 0xDA -> return null
                marker == 0xFF -> index++
                else -> {
                    val length = bytes.u16(index + 2)
                    if (length < 2) return null
                    index += 2 + length
                }
            }
        }
        return null
    }

    private fun readPng(bytes: ByteArray): ImageInfo? {
        if (bytes.size < 24) return null
        val signature = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        if (!bytes.copyOfRange(0, 4).contentEquals(signature)) return null
        return ImageInfo("PNG", bytes.u32(16), bytes.u32(20))
    }

    private fun readWebp(bytes: ByteArray): ImageInfo? {
        if (bytes.size < 30) return null
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WEBP") return null
        return when (String(bytes, 12, 4)) {
            "VP8 " -> ImageInfo("WebP", bytes.u16le(26) and 0x3FFF, bytes.u16le(28) and 0x3FFF)
            "VP8L" -> {
                val packed = bytes.u32le(21)
                ImageInfo("WebP", (packed and 0x3FFF) + 1, ((packed shr 14) and 0x3FFF) + 1)
            }

            "VP8X" -> ImageInfo("WebP", bytes.u24le(24) + 1, bytes.u24le(27) + 1)
            else -> null
        }
    }

    private fun readGif(bytes: ByteArray): ImageInfo? {
        if (bytes.size < 10 || String(bytes, 0, 3) != "GIF") return null
        return ImageInfo("GIF", bytes.u16le(6), bytes.u16le(8))
    }

    private fun ByteArray.u8(index: Int) = this[index].toInt() and 0xFF
    private fun ByteArray.u16(index: Int) = (u8(index) shl 8) or u8(index + 1)
    private fun ByteArray.u32(index: Int) =
        (u8(index) shl 24) or (u8(index + 1) shl 16) or (u8(index + 2) shl 8) or u8(index + 3)

    private fun ByteArray.u16le(index: Int) = u8(index) or (u8(index + 1) shl 8)
    private fun ByteArray.u24le(index: Int) = u8(index) or (u8(index + 1) shl 8) or (u8(index + 2) shl 16)
    private fun ByteArray.u32le(index: Int) =
        u8(index) or (u8(index + 1) shl 8) or (u8(index + 2) shl 16) or (u8(index + 3) shl 24)

    /** Below this, nothing can be a real image, whatever its header claims. */
    private const val MINIMUM_PLAUSIBLE_SIZE = 16L
}
