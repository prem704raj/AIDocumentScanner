package com.example.aidocumentscanner.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Document scanner using OpenCV for edge detection and contour finding.
 * All processing is done locally - no internet required.
 */
object DocumentScanner {

    private const val TAG = "DocumentScanner"

    data class ScanResult(
        val corners: List<PointF>,
        val confidence: Float,
        val croppedBitmap: Bitmap? = null
    )

    /**
     * Detect document edges in the given bitmap.
     * Returns corners in order: top-left, top-right, bottom-right, bottom-left
     */
    fun detectDocumentEdges(bitmap: Bitmap): ScanResult {
        if (!OpenCVManager.isReady()) {
            Log.w(TAG, "OpenCV not initialized, returning full image bounds")
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            return ScanResult(
                listOf(
                    PointF(0f, 0f),
                    PointF(w, 0f),
                    PointF(w, h),
                    PointF(0f, h)
                ),
                0f
            )
        }

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        
        // Apply Gaussian blur to reduce noise
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        
        // Apply Canny edge detection
        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        
        // Dilate to connect edges
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel)
        
        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        
        // Sort by area descending
        contours.sortByDescending { Imgproc.contourArea(it) }
        
        var bestCorners: List<PointF>? = null
        var bestConfidence = 0f
        
        // Find the largest quadrilateral
        for (contour in contours.take(10)) {
            val area = Imgproc.contourArea(contour)
            val imageArea = mat.rows() * mat.cols()
            
            // Skip if contour is too small or too large
            if (area < imageArea * 0.1 || area > imageArea * 0.95) continue
            
            val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)
            
            // If we found a quadrilateral
            if (approx.rows() == 4) {
                val points = approx.toArray()
                val corners = orderPoints(points.map { PointF(it.x.toFloat(), it.y.toFloat()) })
                
                // Calculate confidence based on area ratio and shape
                val confidence = calculateConfidence(corners, mat.cols().toFloat(), mat.rows().toFloat())
                
                if (confidence > bestConfidence) {
                    bestConfidence = confidence
                    bestCorners = corners
                }
            }
        }
        
        // Clean up
        mat.release()
        gray.release()
        edges.release()
        hierarchy.release()
        contours.forEach { it.release() }
        
        // If no document found, return image corners
        if (bestCorners == null) {
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            bestCorners = listOf(
                PointF(0f, 0f),
                PointF(w, 0f),
                PointF(w, h),
                PointF(0f, h)
            )
            bestConfidence = 0f
        }
        
        return ScanResult(bestCorners, bestConfidence)
    }
    
    /**
     * Order points in consistent order: TL, TR, BR, BL
     */
    private fun orderPoints(points: List<PointF>): List<PointF> {
        val sorted = points.sortedBy { it.x + it.y }
        val tl = sorted.first()
        val br = sorted.last()
        
        val remaining = points.filter { it != tl && it != br }
        val tr = remaining.maxByOrNull { it.x - it.y } ?: remaining.first()
        val bl = remaining.minByOrNull { it.x - it.y } ?: remaining.last()
        
        return listOf(tl, tr, br, bl)
    }
    
    /**
     * Calculate confidence score for detected quadrilateral
     */
    private fun calculateConfidence(corners: List<PointF>, imageWidth: Float, imageHeight: Float): Float {
        // Calculate area
        val area = polygonArea(corners)
        val imageArea = imageWidth * imageHeight
        val areaRatio = area / imageArea
        
        // Check if it's a reasonable document shape
        val aspectRatio = calculateAspectRatio(corners)
        val isReasonableAspect = aspectRatio in 0.5f..2.0f
        
        // Check angles are roughly 90 degrees
        val anglesGood = checkAngles(corners)
        
        var confidence = areaRatio
        if (isReasonableAspect) confidence += 0.2f
        if (anglesGood) confidence += 0.3f
        
        return confidence.coerceIn(0f, 1f)
    }
    
    private fun polygonArea(points: List<PointF>): Float {
        var area = 0f
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return abs(area) / 2
    }
    
    private fun calculateAspectRatio(corners: List<PointF>): Float {
        val width = distance(corners[0], corners[1])
        val height = distance(corners[1], corners[2])
        return if (height > 0) width / height else 1f
    }
    
    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }
    
    private fun checkAngles(corners: List<PointF>): Boolean {
        for (i in corners.indices) {
            val prev = corners[(i + corners.size - 1) % corners.size]
            val curr = corners[i]
            val next = corners[(i + 1) % corners.size]
            
            val angle = calculateAngle(prev, curr, next)
            if (angle < 70 || angle > 110) return false
        }
        return true
    }
    
    private fun calculateAngle(p1: PointF, p2: PointF, p3: PointF): Float {
        val v1 = PointF(p1.x - p2.x, p1.y - p2.y)
        val v2 = PointF(p3.x - p2.x, p3.y - p2.y)
        
        val dot = v1.x * v2.x + v1.y * v2.y
        val cross = v1.x * v2.y - v1.y * v2.x
        
        return Math.toDegrees(kotlin.math.atan2(cross.toDouble(), dot.toDouble())).toFloat().let { abs(it) }
    }
}
