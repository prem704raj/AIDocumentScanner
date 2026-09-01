package com.example.aidocumentscanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import kotlin.math.max

/**
 * Central bounded bitmap decoder used by scan/gallery flows.
 *
 * Phase 3 keeps the Phase-2 memory rule: scanner bitmaps are working images, not
 * 30-50 MP camera originals. EXIF rotation is normalized before editor/crop logic.
 */
object BitmapLoader {
    const val DEFAULT_MAX_DIMENSION = 2400

    fun decode(
        context: Context,
        uri: Uri,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): Bitmap? {
        require(maxDimension > 0)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(context, uri, maxDimension)
            } else {
                decodeWithBitmapFactory(context, uri, maxDimension)
            }
        }.getOrNull()
    }

    fun decode(
        file: File,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): Bitmap? {
        require(maxDimension > 0)
        if (!file.isFile) return null

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val sample = calculateSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                maxDimension
            )
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                ?: return@runCatching null

            val rotated = applyExifRotation(file, decoded)
            scaleDownIfNeeded(rotated, maxDimension)
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val largest = max(width, height)
            if (largest > maxDimension) {
                val scale = maxDimension.toFloat() / largest
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1)
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        return if (decoded.config == Bitmap.Config.HARDWARE) {
            decoded.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            decoded
        }
    }

    private fun decodeWithBitmapFactory(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                maxDimension
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return scaleDownIfNeeded(decoded, maxDimension)
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > maxDimension * 2) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / largest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return scaled
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
        if (rotated !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return rotated
    }
}
