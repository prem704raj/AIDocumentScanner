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

object ImageEnhancer {
    private const val TAG = "ImageEnhancer"

    enum class FilterType {
        ORIGINAL,
        MAGIC_COLOR,
        GRAYSCALE,
        BLACK_WHITE,
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
        require(!bitmap.isRecycled) { "Cannot filter a recycled bitmap" }
        val cacheKey = "${System.identityHashCode(bitmap)}:${bitmap.width}x${bitmap.height}:${filter.name}"
        BitmapCache.get(cacheKey)?.let { return it }

        val result = runCatching {
            when (filter) {
                FilterType.ORIGINAL -> bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                FilterType.MAGIC_COLOR -> applyMagicColor(bitmap)
                FilterType.GRAYSCALE -> applyGrayscale(bitmap)
                FilterType.BLACK_WHITE -> applyBlackWhite(bitmap)
                FilterType.LIGHTEN -> adjustBrightness(bitmap, 36)
                FilterType.DARKEN -> adjustBrightness(bitmap, -36)
                FilterType.SEPIA -> applySepia(bitmap)
                FilterType.HIGH_CONTRAST -> adjustContrast(bitmap, 1.45f, -35.0)
                FilterType.SHARPEN -> applySharpen(bitmap)
                FilterType.INVERT -> transformSingleMat(bitmap) { mat -> Core.bitwise_not(mat, mat) }
                FilterType.WARM -> tint(bitmap, redOffset = 24.0, blueOffset = -20.0)
                FilterType.COOL -> tint(bitmap, redOffset = -20.0, blueOffset = 24.0)
            }
        }.getOrElse { error ->
            Log.e(TAG, "Filter ${filter.name} failed", error)
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        }

        BitmapCache.put(cacheKey, result)
        return result
    }

    private fun applyMagicColor(bitmap: Bitmap): Bitmap {
        val source = Mat()
        val rgb = Mat()
        val lab = Mat()
        val channels = ArrayList<Mat>()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            clahe.apply(channels[0], channels[0])
            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)
            Imgproc.cvtColor(rgb, source, Imgproc.COLOR_RGB2RGBA)
            return bitmapFromMat(source)
        } finally {
            source.release()
            rgb.release()
            lab.release()
            channels.forEach { it.release() }
            clahe.clear()
        }
    }

    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val source = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(gray, source, Imgproc.COLOR_GRAY2RGBA)
            return bitmapFromMat(source)
        } finally {
            source.release()
            gray.release()
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
                21,
                10.0
            )
            Imgproc.cvtColor(threshold, source, Imgproc.COLOR_GRAY2RGBA)
            return bitmapFromMat(source)
        } finally {
            source.release()
            gray.release()
            threshold.release()
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
                0,
                0,
                0.272, 0.534, 0.131,
                0.349, 0.686, 0.168,
                0.393, 0.769, 0.189
            )
            Core.transform(rgb, rgb, kernel)
            Imgproc.cvtColor(rgb, source, Imgproc.COLOR_RGB2RGBA)
            return bitmapFromMat(source)
        } finally {
            source.release()
            rgb.release()
            kernel.release()
        }
    }

    private fun applySharpen(bitmap: Bitmap): Bitmap {
        val source = Mat()
        val kernel = Mat(3, 3, CvType.CV_32F)
        try {
            Utils.bitmapToMat(bitmap, source)
            kernel.put(
                0,
                0,
                0.0, -1.0, 0.0,
                -1.0, 5.0, -1.0,
                0.0, -1.0, 0.0
            )
            Imgproc.filter2D(source, source, -1, kernel)
            return bitmapFromMat(source)
        } finally {
            source.release()
            kernel.release()
        }
    }

    fun adjustBrightness(bitmap: Bitmap, brightness: Int): Bitmap =
        transformSingleMat(bitmap) { it.convertTo(it, -1, 1.0, brightness.toDouble()) }

    fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap =
        adjustContrast(bitmap, contrast.coerceIn(0.2f, 3f), 0.0)

    private fun adjustContrast(bitmap: Bitmap, contrast: Float, offset: Double): Bitmap =
        transformSingleMat(bitmap) { it.convertTo(it, -1, contrast.toDouble(), offset) }

    private fun tint(bitmap: Bitmap, redOffset: Double, blueOffset: Double): Bitmap {
        val source = Mat()
        val channels = ArrayList<Mat>()
        try {
            Utils.bitmapToMat(bitmap, source)
            Core.split(source, channels)
            // OpenCV RGBA channel order after Utils.bitmapToMat is R, G, B, A.
            channels.getOrNull(0)?.convertTo(channels[0], -1, 1.0, redOffset)
            channels.getOrNull(2)?.convertTo(channels[2], -1, 1.0, blueOffset)
            Core.merge(channels, source)
            return bitmapFromMat(source)
        } finally {
            source.release()
            channels.forEach { it.release() }
        }
    }

    private inline fun transformSingleMat(bitmap: Bitmap, transform: (Mat) -> Unit): Bitmap {
        val source = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            transform(source)
            return bitmapFromMat(source)
        } finally {
            source.release()
        }
    }

    private fun bitmapFromMat(mat: Mat): Bitmap =
        Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888).also {
            Utils.matToBitmap(mat, it)
        }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        require(!bitmap.isRecycled)
        if (degrees % 360f == 0f) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
