package com.example.aidocumentscanner.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.example.aidocumentscanner.util.BitmapCache
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Image enhancement filters for scanned documents.
 * All processing is done locally - no internet required.
 */
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

    /**
     * Apply enhancement filter to bitmap with crash protection
     */
    fun applyFilter(bitmap: Bitmap, filter: FilterType): Bitmap {
        val cacheKey = "${bitmap.hashCode()}_${filter.name}"
        BitmapCache.get(cacheKey)?.let { return it.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true) }
        
        val result = try {
            when (filter) {
                FilterType.ORIGINAL -> bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
                FilterType.MAGIC_COLOR -> applyMagicColor(bitmap)
                FilterType.GRAYSCALE -> applyGrayscale(bitmap)
                FilterType.BLACK_WHITE -> applyBlackWhite(bitmap)
                FilterType.LIGHTEN -> adjustBrightness(bitmap, 40)
                FilterType.DARKEN -> adjustBrightness(bitmap, -40)
                FilterType.SEPIA -> applySepia(bitmap)
                FilterType.HIGH_CONTRAST -> applyHighContrast(bitmap)
                FilterType.SHARPEN -> applySharpen(bitmap)
                FilterType.INVERT -> applyInvert(bitmap)
                FilterType.WARM -> applyWarm(bitmap)
                FilterType.COOL -> applyCool(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying filter: ${e.message}", e)
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        }
        
        BitmapCache.put(cacheKey, result)
        return result
    }
    
    /**
     * Magic color filter - auto contrast and color enhancement
     */
    private fun applyMagicColor(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            // Convert to Lab color space
            val lab = Mat()
            Imgproc.cvtColor(mat, lab, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2Lab)
            
            // Split channels
            val channels = ArrayList<Mat>()
            Core.split(lab, channels)
            
            // Apply CLAHE to L channel
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(channels[0], channels[0])
            
            // Merge channels
            Core.merge(channels, lab)
            
            // Convert back to RGB
            Imgproc.cvtColor(lab, mat, Imgproc.COLOR_Lab2RGB)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2RGBA)
            
            // Increase saturation slightly
            val hsv = Mat()
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
            
            val hsvChannels = ArrayList<Mat>()
            Core.split(hsv, hsvChannels)
            hsvChannels[1].convertTo(hsvChannels[1], -1, 1.2, 0.0)
            Core.merge(hsvChannels, hsv)
            
            Imgproc.cvtColor(hsv, mat, Imgproc.COLOR_HSV2RGB)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2RGBA)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            // Clean up
            mat.release()
            lab.release()
            hsv.release()
            channels.forEach { it.release() }
            hsvChannels.forEach { it.release() }
            
            return result
        } catch (e: Exception) {
            mat.release()
            throw e
        }
    }
    
    /**
     * Convert to grayscale
     */
    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(gray, mat, Imgproc.COLOR_GRAY2RGBA)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            gray.release()
            
            return result
        } catch (e: Exception) {
            mat.release()
            throw e
        }
    }
    
    /**
     * Black and white with adaptive thresholding
     */
    private fun applyBlackWhite(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
            
            val thresh = Mat()
            Imgproc.adaptiveThreshold(
                gray, thresh, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 11, 2.0
            )
            
            Imgproc.cvtColor(thresh, mat, Imgproc.COLOR_GRAY2RGBA)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            gray.release()
            thresh.release()
            
            return result
        } catch (e: Exception) {
            mat.release()
            throw e
        }
    }
    
    /**
     * Sepia tone filter
     */
    private fun applySepia(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)
            
            val sepiaKernel = Mat(3, 3, CvType.CV_32F)
            sepiaKernel.put(0, 0, 
                0.272, 0.534, 0.131,
                0.349, 0.686, 0.168,
                0.393, 0.769, 0.189
            )
            
            Core.transform(mat, mat, sepiaKernel)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2RGBA)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            sepiaKernel.release()
            
            return result
        } catch (e: Exception) {
            mat.release()
            throw e
        }
    }
    
    /**
     * High contrast filter
     */
    private fun applyHighContrast(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            mat.convertTo(mat, -1, 1.5, -50.0) // alpha=1.5 for contrast, beta=-50 for brightness
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            return result
        } catch (e: Exception) {
            mat.release()
            throw e
        }
    }
    
    /**
     * Sharpen filter for text clarity
     */
    private fun applySharpen(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            val kernel = Mat(3, 3, CvType.CV_32F)
            kernel.put(0, 0,
                0.0, -1.0, 0.0,
                -1.0, 5.0, -1.0,
                0.0, -1.0, 0.0
            )
            
            Imgproc.filter2D(mat, mat, -1, kernel)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            kernel.release()
            
            return result
        } catch (e: Exception) {
            mat.release()
            throw e
        }
    }
    
    /**
     * Invert colors
     */
    private fun applyInvert(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            Core.bitwise_not(mat, mat)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            return result
        } catch (e: Exception) {
            mat.release()
            throw e
        }
    }
    
    /**
     * Warm color temperature
     */
    private fun applyWarm(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            val channels = ArrayList<Mat>()
            Core.split(mat, channels)
            
            // Increase red, decrease blue
            channels[0].convertTo(channels[0], -1, 1.0, 30.0) // R
            channels[2].convertTo(channels[2], -1, 1.0, -30.0) // B
            
            Core.merge(channels, mat)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            channels.forEach { it.release() }
            
            return result
        } catch (e: Exception) {
            mat.release()
            throw e
        }
    }
    
    /**
     * Cool color temperature
     */
    private fun applyCool(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            val channels = ArrayList<Mat>()
            Core.split(mat, channels)
            
            // Decrease red, increase blue
            channels[0].convertTo(channels[0], -1, 1.0, -30.0) // R
            channels[2].convertTo(channels[2], -1, 1.0, 30.0) // B
            
            Core.merge(channels, mat)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            channels.forEach { it.release() }
            
            return result
        } catch (e: Exception) {
            mat.release()
            throw e
        }
    }
    
    /**
     * Adjust brightness
     */
    fun adjustBrightness(bitmap: Bitmap, brightness: Int): Bitmap {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            
            mat.convertTo(mat, -1, 1.0, brightness.toDouble())
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting brightness: ${e.message}", e)
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }
    
    /**
     * Adjust contrast
     */
    fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            
            mat.convertTo(mat, -1, contrast.toDouble(), 0.0)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, result)
            
            mat.release()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting contrast: ${e.message}", e)
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }
    
    /**
     * Rotate image by degrees - FIXED VERSION
     */
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        return try {
            // Use Android's Matrix for reliable rotation
            val matrix = Matrix()
            matrix.postRotate(degrees)
            
            Bitmap.createBitmap(
                bitmap,
                0, 0,
                bitmap.width, bitmap.height,
                matrix,
                true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating: ${e.message}", e)
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }
    
    /**
     * Crop bitmap to specified rectangle
     */
    fun crop(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
        return try {
            val safeLeft = left.coerceIn(0, bitmap.width - 1)
            val safeTop = top.coerceIn(0, bitmap.height - 1)
            val safeWidth = width.coerceIn(1, bitmap.width - safeLeft)
            val safeHeight = height.coerceIn(1, bitmap.height - safeTop)
            
            Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping: ${e.message}", e)
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }
}

