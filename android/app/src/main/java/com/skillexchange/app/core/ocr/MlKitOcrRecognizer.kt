package com.skillexchange.app.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrRecognizer(
    private val context: Context,
    private val targetMaxDimension: Int = 1600
) : IOcrRecognizer {

    override suspend fun recognizeText(imageUri: Uri): Result<String> = withContext(Dispatchers.Default) {
        var bitmap: Bitmap? = null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            bitmap = decodeAndDownscaleBitmap(context, imageUri, targetMaxDimension)
                ?: return@withContext Result.failure(IllegalArgumentException("Không thể đọc hoặc giải mã hình ảnh."))

            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val visionText = suspendCancellableCoroutine<Text> { continuation ->
                val task = recognizer.process(inputImage)
                task.addOnSuccessListener { text ->
                    if (continuation.isActive) {
                        continuation.resume(text)
                    }
                }
                task.addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
                continuation.invokeOnCancellation {
                    // Cooperative cancel: the Task will complete or fail, continuation is cancelled
                }
            }

            val text = visionText.text.trim()
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Không nhận dạng được văn bản nào từ hình ảnh."))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            bitmap?.recycle()
            try {
                recognizer.close()
            } catch (_: Exception) {}
        }
    }

    private fun decodeAndDownscaleBitmap(context: Context, uri: Uri, targetMaxDimension: Int): Bitmap? {
        // 1st pass: Decode bounds only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val s1 = openStream(context, uri)
        if (s1 == null) {
            android.util.Log.e("MlKitOcrRecognizer", "openStream 1 failed for uri=$uri")
            return null
        }
        s1.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            android.util.Log.e("MlKitOcrRecognizer", "raw bounds <= 0: w=$rawWidth, h=$rawHeight")
            return null
        }

        var inSampleSize = 1
        val maxRaw = maxOf(rawWidth, rawHeight)
        while (maxRaw / (inSampleSize * 2) >= targetMaxDimension) {
            inSampleSize *= 2
        }

        // 2nd pass: Decode actual bitmap directly from stream with inSampleSize
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val s2 = openStream(context, uri)
        if (s2 == null) {
            android.util.Log.e("MlKitOcrRecognizer", "openStream 2 failed for uri=$uri")
            return null
        }
        var bitmap = s2.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: run {
            android.util.Log.e("MlKitOcrRecognizer", "decodeStream pass 2 returned null")
            return null
        }

        // Fine scaling if still slightly larger than targetMaxDimension
        val currentMax = maxOf(bitmap.width, bitmap.height)
        if (currentMax > targetMaxDimension) {
            val scale = targetMaxDimension.toFloat() / currentMax
            val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            if (scaled != bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }

        // Handle EXIF orientation rotation
        val rotation = getRotationDegrees(context, uri)
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
                bitmap = rotated
            }
        }

        return bitmap
    }

    private fun getRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            openStream(context, uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun openStream(context: Context, uri: Uri): java.io.InputStream? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                java.io.File(path).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            android.util.Log.e("MlKitOcrRecognizer", "openStream exception for uri=$uri: ${e.message}", e)
            null
        }
    }
}
