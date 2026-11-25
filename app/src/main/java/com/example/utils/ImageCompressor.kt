package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

data class EncodedAttachment(
    val fileName: String,
    val mimeType: String,
    val base64: String
)

object ImageCompressor {

    private const val MAX_DIMENSION_PX = 768
    private const val JPEG_QUALITY = 68
    private const val MAX_BASE64_CHARS = 650_000
    private const val MAX_RAW_FILE_BYTES = 480_000

    fun createTempCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "evidences").apply { mkdirs() }
        val file = File(dir, "evidencia_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun encodeUriAttachment(context: Context, uri: Uri): Result<EncodedAttachment> = runCatching {
        val resolver = context.contentResolver
        val rawName = queryDisplayName(context, uri) ?: "adjunto_${System.currentTimeMillis()}"
        val safeName = rawName.trim().ifBlank { "adjunto" }.take(120)
        val rawMime = resolver.getType(uri)?.trim()?.takeIf { it.isNotEmpty() }?.take(100)
            ?: "application/octet-stream"

        if (rawMime.startsWith("image/")) {
            val base64 = compressUriToBase64(context, uri).getOrThrow()
            val imageName = if (safeName.contains(".")) safeName else "$safeName.jpg"
            return@runCatching EncodedAttachment(
                fileName = imageName,
                mimeType = "image/jpeg",
                base64 = base64
            )
        }

        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                require(total <= MAX_RAW_FILE_BYTES) {
                    "El archivo supera el máximo permitido de 480 KB."
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: error("No se pudo abrir el archivo seleccionado.")

        require(bytes.isNotEmpty()) { "El archivo seleccionado está vacío." }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        require(base64.length in 50..MAX_BASE64_CHARS) {
            "El archivo no cumple con el tamaño válido."
        }
        EncodedAttachment(
            fileName = safeName,
            mimeType = rawMime,
            base64 = base64
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
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
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
        val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: error("No se pudo decodificar la fotografía.")

        val maxSide = max(rawBitmap.width, rawBitmap.height)
        val scaledBitmap = if (maxSide > MAX_DIMENSION_PX) {
            val scale = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
            val matrix = Matrix().apply { postScale(scale, scale) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }

        var quality = JPEG_QUALITY
        var encoded: String
        do {
            val out = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            quality -= 10
        } while (encoded.length > MAX_BASE64_CHARS && quality >= 25)

        if (scaledBitmap !== rawBitmap) {
            scaledBitmap.recycle()
        }
        rawBitmap.recycle()

        require(encoded.length in 50..MAX_BASE64_CHARS) {
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

    fun createViewableCacheUri(context: Context, fileName: String, base64: String): Uri? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "adjunto.bin" }
            val dir = File(context.cacheDir, "evidences").apply { mkdirs() }
            val outFile = File(dir, safeName)
            outFile.writeBytes(bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        } catch (_: Exception) {
            null
        }
    }
}
