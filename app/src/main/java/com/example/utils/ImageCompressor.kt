package com.example.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import kotlin.math.max

data class EncodedAttachment(
    val fileName: String,
    val mimeType: String,
    val totalBytes: Int,
    val base64Chunks: List<String>
) {
    val isChunked: Boolean get() = base64Chunks.size > 1
    val singleBase64OrNull: String? get() = if (base64Chunks.size == 1) base64Chunks.first() else null
}

object ImageCompressor {

    private const val MAX_DIMENSION_PX = 768
    private const val JPEG_QUALITY = 68
    private const val MAX_BASE64_CHARS = 650_000
    internal const val CHUNK_RAW_BYTES = 450_000
    internal const val MAX_TOTAL_FILE_BYTES = 3_500_000

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")

    private val KNOWN_MIME_TYPES = mapOf(
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "csv" to "text/csv",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "zip" to "application/zip",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "gif" to "image/gif",
        "bmp" to "image/bmp"
    )

    internal fun inferMimeType(fileName: String, reportedMimeType: String?): String {
        val cleanedReported = reportedMimeType?.trim()?.takeIf { it.isNotEmpty() }?.take(100)
        if (cleanedReported != null && !cleanedReported.equals("application/octet-stream", ignoreCase = true)) {
            return cleanedReported
        }
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotEmpty()) {
            KNOWN_MIME_TYPES[ext]?.let { return it }
            runCatching {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it.take(100) }
        }
        return cleanedReported ?: "application/octet-stream"
    }

    internal fun isLikelyImage(fileName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("image/", ignoreCase = true) == true) return true
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in IMAGE_EXTENSIONS
    }

    internal fun chunkBytesToBase64(
        bytes: ByteArray,
        chunkSizeBytes: Int = CHUNK_RAW_BYTES,
        encoder: (ByteArray) -> String = { slice -> Base64.encodeToString(slice, Base64.NO_WRAP) }
    ): List<String> {
        require(bytes.isNotEmpty()) { "El archivo seleccionado está vacío." }
        require(bytes.size <= MAX_TOTAL_FILE_BYTES) {
            "El archivo supera el máximo permitido de 3.5 MB."
        }
        if (bytes.size <= chunkSizeBytes) {
            return listOf(encoder(bytes))
        }
        val chunks = mutableListOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSizeBytes, bytes.size)
            chunks.add(encoder(bytes.copyOfRange(offset, end)))
            offset = end
        }
        return chunks
    }

    fun formatFileSize(bytes: Int): String = when {
        bytes <= 0 -> "Archivo"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes / 1024.0).toInt().coerceAtLeast(1)} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    fun createTempCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "evidences").apply { mkdirs() }
        val file = File(dir, "evidencia_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun openStream(resolver: ContentResolver, uri: Uri): InputStream? {
        return runCatching { resolver.openInputStream(uri) }.getOrNull()
            ?: runCatching { resolver.openAssetFileDescriptor(uri, "r")?.createInputStream() }.getOrNull()
    }

    private fun probeImageBounds(resolver: ContentResolver, uri: Uri): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return openStream(resolver, uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
            options.outWidth > 0 && options.outHeight > 0
        } ?: false
    }

    fun encodeUriAttachment(context: Context, uri: Uri): Result<EncodedAttachment> = runCatching {
        val resolver = context.contentResolver
        val rawName = queryDisplayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "adjunto_${System.currentTimeMillis()}"
        val safeName = rawName.trim().ifBlank { "adjunto" }.take(120)
        val effectiveMime = inferMimeType(safeName, runCatching { resolver.getType(uri) }.getOrNull())

        val treatAsImage = isLikelyImage(safeName, effectiveMime) ||
            (effectiveMime == "application/octet-stream" && probeImageBounds(resolver, uri))

        if (treatAsImage) {
            val base64 = compressUriToBase64(context, uri).getOrThrow()
            val imageName = if (safeName.contains(".")) safeName else "$safeName.jpg"
            val approxBytes = ((base64.length * 3) / 4).coerceAtLeast(1)
            return@runCatching EncodedAttachment(
                fileName = imageName,
                mimeType = "image/jpeg",
                totalBytes = approxBytes,
                base64Chunks = listOf(base64)
            )
        }

        val bytes = openStream(resolver, uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                require(total <= MAX_TOTAL_FILE_BYTES) {
                    "El archivo supera el máximo permitido de 3.5 MB."
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: error("No se pudo abrir el archivo seleccionado.")

        require(bytes.isNotEmpty()) { "El archivo seleccionado está vacío." }
        val chunks = chunkBytesToBase64(bytes)
        require(chunks.size <= 8 && chunks.all { it.isNotEmpty() && it.length <= 700_000 }) {
            "El archivo supera el máximo permitido de 3.5 MB."
        }
        EncodedAttachment(
            fileName = safeName,
            mimeType = effectiveMime,
            totalBytes = bytes.size,
            base64Chunks = chunks
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    fun compressUriToBase64(context: Context, uri: Uri): Result<String> = runCatching {
        val resolver = context.contentResolver
        val tempFile = File(context.cacheDir, "tmp_img_${System.currentTimeMillis()}.bin")
        try {
            val copied = openStream(resolver, uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false

            val rawBitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = if (copied && tempFile.exists() && tempFile.length() > 0L) {
                    ImageDecoder.createSource(tempFile)
                } else {
                    ImageDecoder.createSource(resolver, uri)
                }
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxSide = max(info.size.width, info.size.height)
                    if (maxSide > MAX_DIMENSION_PX) {
                        val scale = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (copied && tempFile.exists()) {
                    BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
                } else {
                    openStream(resolver, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                }
                var sampleSize = 1
                
