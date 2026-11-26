package com.example

import com.example.utils.ImageCompressor
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCompressorTest {

    @Test
    fun `small files under 50 bytes encode cleanly into single chunk`() {
        val smallBytes = "Hola".toByteArray()
        val chunks = ImageCompressor.chunkBytesToBase64(smallBytes) { slice ->
            Base64.getEncoder().encodeToString(slice)
        }
        assertEquals(1, chunks.size)
        assertEquals("SG9sYQ==", chunks.first())
    }

    @Test
    fun `large files up to 3_5 MB chunk into 450KB slices without exceeding 8 chunks`() {
        val largeBytes = ByteArray(1_500_000) { (it % 127).toByte() }
        val chunks = ImageCompressor.chunkBytesToBase64(largeBytes) { slice ->
            Base64.getEncoder().encodeToString(slice)
        }
        assertEquals(4, chunks.size)
        assertTrue(chunks.all { it.isNotEmpty() && it.length <= 700_000 })

        val reassembled = chunks.flatMap { Base64.getDecoder().decode(it).toList() }.toByteArray()
        assertEquals(largeBytes.size, reassembled.size)
        assertTrue(largeBytes.contentEquals(reassembled))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `files larger than 3_5 MB are rejected`() {
        val oversized = ByteArray(ImageCompressor.MAX_TOTAL_FILE_BYTES + 1)
        ImageCompressor.chunkBytesToBase64(oversized) { slice ->
            Base64.getEncoder().encodeToString(slice)
        }
    }

    @Test
    fun `inferMimeType resolves extensions when provider returns null or octet-stream`() {
        assertEquals("application/pdf", ImageCompressor.inferMimeType("corte_caja.pdf", null))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ImageCompressor.inferMimeType("ventas.XLSX", "application/octet-stream")
        )
        assertEquals("image/jpeg", ImageCompressor.inferMimeType("ticket.jpg", "application/octet-stream"))
        assertEquals("text/csv", ImageCompressor.inferMimeType("datos.csv", ""))
        assertEquals("application/pdf", ImageCompressor.inferMimeType("doc", "application/pdf"))
    }

    @Test
    fun `isLikelyImage detects image extensions even with generic mime type`() {
        assertTrue(ImageCompressor.isLikelyImage("foto_sucursal.JPG", "application/octet-stream"))
        assertTrue(ImageCompressor.isLikelyImage("captura.png", null))
        assertTrue(ImageCompressor.isLikelyImage("recibo.heic", "application/octet-stream"))
        assertTrue(ImageCompressor.isLikelyImage("sin_extension", "image/webp"))
        assertFalse(ImageCompressor.isLikelyImage("reporte.pdf", "application/pdf"))
    }

    @Test
    fun `formatFileSize formats bytes KB and MB cleanly`() {
        assertEquals("512 B", ImageCompressor.formatFileSize(512))
        assertEquals("120 KB", ImageCompressor.formatFileSize(120 * 1024))
        assertEquals("2.5 MB", ImageCompressor.formatFileSize((2.5 * 1024 * 1024).toInt()))
    }

    @Test
    fun `task evidence file up to 1_5 MB fits within 4 chunks`() {
        val bytes = ByteArray(ImageCompressor.MAX_TASK_EVIDENCE_FILE_BYTES) { (it % 97).toByte() }
        val chunks = ImageCompressor.chunkBytesToBase64(
            bytes = bytes,
            maxTotalBytes = ImageCompressor.MAX_TASK_EVIDENCE_FILE_BYTES,
            maxSizeLabel = "1.5 MB"
        ) { slice ->
            Base64.getEncoder().encodeToString(slice)
        }
        assertEquals(4, chunks.size)
        assertTrue(chunks.all { it.isNotEmpty() && it.length <= 700_000 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `task evidence file over 1_5 MB is rejected`() {
        val oversized = ByteArray(ImageCompressor.MAX_TASK_EVIDENCE_FILE_BYTES + 1)
        ImageCompressor.chunkBytesToBase64(
            bytes = oversized,
            maxTotalBytes = ImageCompressor.MAX_TASK_EVIDENCE_FILE_BYTES,
            maxSizeLabel = "1.5 MB"
        ) { slice ->
            Base64.getEncoder().encodeToString(slice)
        }
    }
}
