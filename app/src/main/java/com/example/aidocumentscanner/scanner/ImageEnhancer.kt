package com.example.aidocumentscanner.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.example.aidocumentscanner.util.BitmapCache
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Local document enhancement filters.
 *
 * Legacy enum values remain for source compatibility, but Phase 3 surfaces the five useful
 * scan filters in the editor: Original, Document, B&W, Grayscale, Color.
 */
object ImageEnhancer {
    private const val TAG = "ImageEnhancer"

    enum class FilterType {
        ORIGINAL,
        DOCUMENT,
        BLACK_WHITE,
        GRAYSCALE,
        COLOR_ENHANCE,

        // Kept for compatibility with any existing saved/source references.
        MAGIC_COLOR,
        LIGHTEN,
        DARKEN,
        SEPIA,
        HIGH_CONTRAST,
        SHARPEN,
        INVERT,
        WARM,
        COOL
    }

    fun applyFilter(bitmap: Bitmap, filter: FilterType): Bitmap {
        require(!bitmap.isRecycled)
        val key = "${System.identityHashCode(bitmap)}:${bitmap.width}x${bitmap.height}:${filter.name}"
        BitmapCache.get(key)?.let { return it }

        val result = runCatching {
            when (filter) {
                FilterType.ORIGINAL ->
                    bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                FilterType.DOCUMENT -> applyDocument(bitmap)
                FilterType.BLACK_WHITE -> applyBlackWhite(bitmap)
                FilterType.GRAYSCALE -> applyGrayscale(bitmap)
                FilterType.COLOR_ENHANCE,
                FilterType.MAGIC_COLOR -> applyColorEnhance(bitmap)
                FilterType.LIGHTEN -> adjustBrightness(bitmap, 30)
                FilterType.DARKEN -> adjustBrightness(bitmap, -30)
                FilterType.SEPIA -> applySepia(bitmap)
                FilterType.HIGH_CONTRAST -> adjustContrast(bitmap, 1.4f, -28.0)
                FilterType.SHARPEN -> applySharpen(bitmap)
                FilterType.INVERT -> single(bitmap) { Core.bitwise_not(it, it) }
                FilterType.WARM -> tint(bitmap, 20.0, -16.0)
                FilterType.COOL -> tint(bitmap, -16.0, 20.0)
            }
        }.getOrElse { error ->
            Log.e(TAG, "Filter failed: ${filter.name}", error)
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        }

        BitmapCache.put(key, result)
        return result
    }

    /**
     * Bright paper + clear text while preserving color.
     */
    private fun applyDocument(bitmap: Bitmap): Bitmap {
        val source = Mat()
        val rgb = Mat()
        val lab = Mat()
        val channels = ArrayList<Mat>()
        val clahe = Imgproc.createCLAHE(2.2, Size(8.0, 8.0))
        val sharpened = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            clahe.apply(channels[0], channels[0])
            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)

            Imgproc.GaussianBlur(rgb, sharpened, Size(0.0, 0.0), 2.0)
            Core.addWeighted(rgb, 1.35, sharpened, -0.35, 10.0, rgb)
            Imgproc.cvtColor(rgb, source, Imgproc.COLOR_RGB2RGBA)
            return fromMat(source)
        } finally {
            source.release()
            rgb.release()
            lab.release()
            sharpened.release()
            channels.forEach { it.release() }
            clahe.clear()
        }
    }

    private fun applyColorEnhance(bitmap: Bitmap): Bitmap {
        val source = Mat()
        val rgb = Mat()
        val lab = Mat()
        val channels = ArrayList<Mat>()
        val clahe = Imgproc.createCLAHE(1.8, Size(8.0, 8.0))
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            clahe.apply(channels[0], channels[0])
            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)
            rgb.convertTo(rgb, -1, 1.06, 3.0)
            Imgproc.cvtColor(rgb, source, Imgproc.COLOR_RGB2RGBA)
            return fromMat(source)
        } finally {
            source.release()
            rgb.release()
            lab.release()
            channels.forEach { it.release() }
            clahe.clear()
        }
    }

    private fun applyBlackWhite(bitmap: Bitmap): Bitmap {
        val source = Mat()
        val gray = Mat()
        val threshold = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
            Imgproc.adaptiveThreshold(
                gray,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                12.0
            )
            Imgproc.cvtColor(threshold, source, Imgproc.COLOR_GRAY2RGBA)
            return fromMat(source)
        } finally {
            source.release()
            gray.release()
            threshold.release()
        }
    }

    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val source = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Core.normalize(gray, gray, 0.0, 255.0, Core.NORM_MINMAX)
            Imgproc.cvtColor(gray, source, Imgproc.COLOR_GRAY2RGBA)
            return fromMat(source)
        } finally {
            source.release()
            gray.release()
        }
    }

    private fun applySharpen(bitmap: Bitmap): Bitmap {
        val source = Mat()
        val kernel = Mat(3, 3, CvType.CV_32F)
        try {
            Utils.bitmapToMat(bitmap, source)
            kernel.put(
                0, 0,
                0.0, -1.0, 0.0,
                -1.0, 5.0, -1.0,
                0.0, -1.0, 0.0
            )
            Imgproc.filter2D(source, source, -1, kernel)
            return fromMat(source)
        } finally {
            source.release()
            kernel.release()
        }
    }

    private fun applySepia(bitmap: Bitmap): Bitmap {
        val source = Mat()
        val rgb = Mat()
        val kernel = Mat(3, 3, CvType.CV_32F)
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)
            kernel.put(
                0, 0,
                0.272, 0.534, 0.131,
                0.349, 0.686, 0.168,
                0.393, 0.769, 0.189
            )
            Core.transform(rgb, rgb, kernel)
            Imgproc.cvtColor(rgb, source, Imgproc.COLOR_RGB2RGBA)
            return fromMat(source)
        } finally {
            source.release()
            rgb.release()
            kernel.release()
        }
    }

    fun adjustBrightness(bitmap: Bitmap, amount: Int): Bitmap =
        single(bitmap) { it.convertTo(it, -1, 1.0, amount.toDouble()) }

    fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap =
        adjustContrast(bitmap, contrast.coerceIn(0.2f, 3f), 0.0)

    private fun adjustContrast(bitmap: Bitmap, contrast: Float, offset: Double): Bitmap =
        single(bitmap) { it.convertTo(it, -1, contrast.toDouble(), offset) }

    private fun tint(bitmap: Bitmap, redOffset: Double, blueOffset: Double): Bitmap {
        val source = Mat()
        val channels = ArrayList<Mat>()
        try {
            Utils.bitmapToMat(bitmap, source)
            Core.split(source, channels)
            channels.getOrNull(0)?.convertTo(channels[0], -1, 1.0, redOffset)
            channels.getOrNull(2)?.convertTo(channels[2], -1, 1.0, blueOffset)
            Core.merge(channels, source)
            return fromMat(source)
        } finally {
            source.release()
            channels.forEach { it.release() }
        }
    }

    private inline fun single(bitmap: Bitmap, block: (Mat) -> Unit): Bitmap {
        val source = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            block(source)
            return fromMat(source)
        } finally {
            source.release()
        }
    }

    private fun fromMat(mat: Mat): Bitmap =
        Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888).also {
            Utils.matToBitmap(mat, it)
        }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        require(!bitmap.isRecycled)
        if (degrees % 360f == 0f) {
            return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    fun crop(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
        require(!bitmap.isRecycled)
        val safeLeft = left.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
        val safeTop = top.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
        val safeWidth = width.coerceIn(1, bitmap.width - safeLeft)
        val safeHeight = height.coerceIn(1, bitmap.height - safeTop)
        return Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
    }
}
