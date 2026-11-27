package com.example.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
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
    internal const val MAX_TASK_EVIDENCE_FILE_BYTES = 1_500_000
    internal const val MAX_TASK_EVIDENCE_FILES = 5

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
        maxTotalBytes: Int = MAX_TOTAL_FILE_BYTES,
        maxSizeLabel: String = "3.5 MB",
        encoder: (ByteArray) -> String = { slice -> Base64.encodeToString(slice, Base64.NO_WRAP) }
    ): List<String> {
        require(bytes.isNotEmpty()) { "El archivo seleccionado está vacío." }
        require(bytes.size <= maxTotalBytes) {
            "El archivo supera el máximo permitido de $maxSizeLabel."
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

    fun encodeUriAttachment(
        context: Context,
        uri: Uri,
        maxTotalBytes: Int = MAX_TOTAL_FILE_BYTES,
        maxChunks: Int = 8,
        maxSizeLabel: String = "3.5 MB"
    ): Result<EncodedAttachment> = runCatching {
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
                require(total <= maxTotalBytes) {
                    "El archivo supera el máximo permitido de $maxSizeLabel."
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: error("No se pudo abrir el archivo seleccionado.")

        require(bytes.isNotEmpty()) { "El archivo seleccionado está vacío." }
        val chunks = chunkBytesToBase64(
            bytes = bytes,
            maxTotalBytes = maxTotalBytes,
            maxSizeLabel = maxSizeLabel
        )
        require(chunks.size <= maxChunks && chunks.all { it.isNotEmpty() && it.length <= 700_000 }) {
            "El archivo supera el máximo permitido de $maxSizeLabel."
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
        val transformedBitmap: Bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(resolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val maxSide = max(w, h)
                if (maxSide > MAX_DIMENSION_PX && maxSide > 0) {
                    val scale = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(resolver, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: error("No se pudo abrir la imagen seleccionada.")

            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            require(srcWidth > 0 && srcHeight > 0) { "Formato de imagen no válido." }

            var sampleSize = 1
            while (srcWidth / sampleSize > MAX_DIMENSION_PX * 2 || srcHeight / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = openStream(resolver, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: error("No se pudo decodificar la fotografía.")

            val maxSide = max(rawBitmap.width, rawBitmap.height)
            if (maxSide > MAX_DIMENSION_PX) {
                val scale = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
                val matrix = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        }

        var quality = JPEG_QUALITY
        var encoded: String
        do {
            val out = ByteArrayOutputStream()
            transformedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            quality -= 10
        } while (encoded.length > MAX_BASE64_CHARS && quality >= 25)

        if (transformedBitmap !== rawBitmap) {
            transformedBitmap.recycle()
        }
        rawBitmap.recycle()

        require(encoded.isNotEmpty() && encoded.length <= MAX_BASE64_CHARS) {
            "La fotografía excede el tamaño permitido para evidencia."
        }
        encoded
    }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ChunksToImageBitmap(chunks: List<String>): ImageBitmap? {
        if (chunks.isEmpty()) return null
        if (chunks.size == 1) return decodeBase64ToImageBitmap(chunks.first())
        return try {
            val out = ByteArrayOutputStream()
            chunks.forEach { chunk ->
                out.write(Base64.decode(chunk, Base64.DEFAULT))
            }
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun createViewableCacheUri(context: Context, fileName: String, base64: String): Uri? {
        return createViewableCacheUriFromChunks(context, fileName, listOf(base64))
    }

    fun createViewableCacheUriFromChunks(
        context: Context,
        fileName: String,
        base64Chunks: List<String>
    ): Uri? {
        if (base64Chunks.isEmpty()) return null
        return try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "adjunto.bin" }
            val dir = File(context.cacheDir, "evidences").apply { mkdirs() }
            val outFile = File(dir, safeName)
            outFile.outputStream().use { fos ->
                base64Chunks.forEach { chunk ->
                    val decoded = Base64.decode(chunk, Base64.DEFAULT)
                    fos.write(decoded)
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        } catch (_: Exception) {
            null
        }
    }
}
