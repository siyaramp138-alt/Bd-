package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Play-policy compliant helper for capturing temporary camera photos via FileProvider
 * and reliably compressing/encoding CameraX & Gallery photos to Base64 for Firestore cloud sync.
 */
object OrderPhotoHelper {

    const val TARGET_PHOTO_BASE64_CHARS = 180_000
    const val MAX_PHOTO_BASE64_CHARS = 380_000

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun createTempCameraImageFile(context: Context): File {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        return File(dir, "bd_photo_${UUID.randomUUID()}.jpg")
    }

    fun createTempCameraImageUri(context: Context): Uri {
        val file = createTempCameraImageFile(context)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Persists a compressed Base64 draft photo string to app cache so CreateOrderScreen
     * can keep only the short file path in rememberSaveable (avoiding TransactionTooLargeException
     * while surviving Activity recreation during Gallery / Camera flows).
     */
    suspend fun saveDraftPhotoToCache(context: Context, base64: String): String =
        withContext(Dispatchers.IO) {
            if (base64.isBlank()) return@withContext ""
            val dir = File(context.cacheDir, "draft_photos").apply { mkdirs() }
            val file = File(dir, "draft_${UUID.randomUUID()}.b64")
            file.writeText(base64)
            file.absolutePath
        }

    suspend fun loadDraftPhotoFromCache(path: String): String =
        withContext(Dispatchers.IO) {
            if (path.isBlank()) return@withContext ""
            runCatching {
                val f = File(path)
                if (f.exists() && f.isFile) f.readText() else ""
            }.getOrDefault("")
        }

    fun deleteDraftPhotoCache(path: String) {
        if (path.isBlank()) return
        runCatching { File(path).delete() }
    }

    suspend fun encodeFileToCompressedBase64(
        context: Context,
        file: File,
        maxDimensionPx: Int = 720,
        maxBase64Chars: Int = TARGET_PHOTO_BASE64_CHARS
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            runCatching {
                require(file.exists() && file.length() > 0L) {
                    "Captured photo file is empty. Please retake the photo."
                }
                val rawBytes = file.readBytes()
                encodeBytesToCompressedBase64(
                    rawBytes = rawBytes,
                    maxDimensionPx = maxDimensionPx,
                    targetBase64Chars = maxBase64Chars
                )
            }
        } finally {
            runCatching { file.delete() }
        }
    }

    suspend fun encodeUriToCompressedBase64(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = 720,
        maxBase64Chars: Int = TARGET_PHOTO_BASE64_CHARS
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: error("Unable to open selected photo.")
            require(rawBytes.isNotEmpty()) { "Selected photo is empty." }
            encodeBytesToCompressedBase64(
                rawBytes = rawBytes,
                maxDimensionPx = maxDimensionPx,
                targetBase64Chars = maxBase64Chars
            )
        }
    }

    private fun encodeBytesToCompressedBase64(
        rawBytes: ByteArray,
        maxDimensionPx: Int,
        targetBase64Chars: Int
    ): String {
        val decoded = decodeBitmapFromBytes(rawBytes, maxDimensionPx)
            ?: error("Could not decode selected photo format.")

        val rotationDegrees = readExifRotationDegrees(rawBytes)
        val oriented = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val dimensionSteps = listOf(maxDimensionPx, 560, 420, 320).distinct()
        val qualitySteps = listOf(76, 64, 52, 40, 30)

        var bestEncoded = ""
        try {
            for (dim in dimensionSteps) {
                val candidateBitmap = scaleDownIfNeeded(oriented, dim)
                try {
                    for (quality in qualitySteps) {
                        val out = ByteArrayOutputStream()
                        candidateBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        val candidate = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        bestEncoded = candidate
                        if (candidate.length <= targetBase64Chars) {
                            return candidate
                        }
                    }
                } finally {
                    if (candidateBitmap !== oriented) {
                        candidateBitmap.recycle()
                    }
                }
            }
        } finally {
            oriented.recycle()
        }

        require(bestEncoded.isNotBlank() && bestEncoded.length <= MAX_PHOTO_BASE64_CHARS) {
            "Photo is too large to sync. Please retake a closer photo."
        }
        return bestEncoded
    }

    private fun decodeBitmapFromBytes(rawBytes: ByteArray, maxDimensionPx: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

        if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
            var inSampleSize = 1
            val longestOrig = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while ((longestOrig / inSampleSize) > maxDimensionPx * 2) {
                inSampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
            if (bitmap != null) return bitmap
        }

        // Fallback for modern formats (e.g., HEIC / AVIF / 10-bit camera images on API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return runCatching {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(rawBytes))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxDimensionPx * 2) {
                        val ratio = (maxDimensionPx * 2).toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            maxOf(1, (info.size.width * ratio).toInt()),
                            maxOf(1, (info.size.height * ratio).toInt())
                        )
                    }
                }
            }.getOrNull()
        }
        return null
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun readExifRotationDegrees(rawBytes: ByteArray): Float {
        return runCatching {
            ByteArrayInputStream(rawBytes).use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrDefault(0f)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val targetW = maxOf(1, (w * ratio).toInt())
        val targetH = maxOf(1, (h * ratio).toInt())
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
