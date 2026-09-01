package com.example.aidocumentscanner.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/** Local OpenCV document-boundary detection with bounded working resolution. */
object DocumentScanner {
    private const val TAG = "DocumentScanner"
    private const val MAX_DETECTION_DIMENSION = 1400

    data class ScanResult(
        val corners: List<PointF>,
        val confidence: Float,
        val croppedBitmap: Bitmap? = null
    )

    fun detectDocumentEdges(bitmap: Bitmap): ScanResult {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return ScanResult(emptyList(), 0f)
        }
        if (!OpenCVManager.isReady()) return fullBounds(bitmap)

        val scale = minOf(
            1f,
            MAX_DETECTION_DIMENSION.toFloat() / max(bitmap.width, bitmap.height).toFloat()
        )
        val workingBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        val source = Mat()
        val gray = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        var kernel: Mat? = null
        val contours = ArrayList<MatOfPoint>()

        return try {
            Utils.bitmapToMat(workingBitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 70.0, 190.0)
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, edges, kernel)
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea = source.rows().toDouble() * source.cols().toDouble()
            var bestCorners: List<PointF>? = null
            var bestConfidence = 0f

            contours
                .sortedByDescending(Imgproc::contourArea)
                .take(12)
                .forEach { contour ->
                    val area = Imgproc.contourArea(contour)
                    if (area < imageArea * 0.12 || area > imageArea * 0.98) return@forEach

                    val contour2f = MatOfPoint2f(*contour.toArray())
                    val approx = MatOfPoint2f()
                    try {
                        val perimeter = Imgproc.arcLength(contour2f, true)
                        Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)
                        if (approx.total() == 4L) {
                            val ordered = orderPoints(
                                approx.toArray().map { point ->
                                    PointF(point.x.toFloat(), point.y.toFloat())
                                }
                            )
                            val confidence = calculateConfidence(
                                ordered,
                                source.cols().toFloat(),
                                source.rows().toFloat()
                            )
                            if (confidence > bestConfidence) {
                                bestConfidence = confidence
                                bestCorners = ordered
                            }
                        }
                    } finally {
                        contour2f.release()
                        approx.release()
                    }
                }

            val detected = bestCorners ?: return fullBounds(bitmap)
            val inverseScale = 1f / scale
            ScanResult(
                corners = detected.map { PointF(it.x * inverseScale, it.y * inverseScale) },
                confidence = bestConfidence
            )
        } catch (error: Exception) {
            Log.e(TAG, "Document detection failed", error)
            fullBounds(bitmap)
        } finally {
            source.release()
            gray.release()
            edges.release()
            hierarchy.release()
            kernel?.release()
            contours.forEach { it.release() }
            if (workingBitmap !== bitmap && !workingBitmap.isRecycled) workingBitmap.recycle()
        }
    }

    private fun fullBounds(bitmap: Bitmap): ScanResult {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        return ScanResult(
            corners = listOf(
                PointF(0f, 0f),
                PointF(width, 0f),
                PointF(width, height),
                PointF(0f, height)
            ),
            confidence = 0f
        )
    }

    private fun orderPoints(points: List<PointF>): List<PointF> {
        require(points.size == 4)
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val remaining = points.filterNot { it === topLeft || it === bottomRight }
        val topRight = remaining.maxBy { it.x - it.y }
        val bottomLeft = remaining.minBy { it.x - it.y }
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun calculateConfidence(
        corners: List<PointF>,
        imageWidth: Float,
        imageHeight: Float
    ): Float {
        if (corners.size != 4 || imageWidth <= 0f || imageHeight <= 0f) return 0f
        val areaRatio = polygonArea(corners) / (imageWidth * imageHeight)
        val width = distance(corners[0], corners[1])
        val height = distance(corners[1], corners[2])
        val aspect = if (height > 0f) width / height else 0f
        val aspectBonus = if (aspect in 0.35f..2.8f) 0.15f else 0f
        val angleBonus = if (corners.indices.all { index ->
                val previous = corners[(index + 3) % 4]
                val current = corners[index]
                val next = corners[(index + 1) % 4]
                calculateAngle(previous, current, next) in 65f..115f
            }) 0.25f else 0f
        return (areaRatio + aspectBonus + angleBonus).coerceIn(0f, 1f)
    }

    private fun polygonArea(points: List<PointF>): Float {
        var area = 0f
        points.indices.forEach { index ->
            val next = (index + 1) % points.size
            area += points[index].x * points[next].y - points[next].x * points[index].y
        }
        return abs(area) / 2f
    }

    private fun distance(first: PointF, second: PointF): Float {
        val dx = second.x - first.x
        val dy = second.y - first.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun calculateAngle(first: PointF, vertex: PointF, third: PointF): Float {
        val ax = first.x - vertex.x
        val ay = first.y - vertex.y
        val bx = third.x - vertex.x
        val by = third.y - vertex.y
        val dot = ax * bx + ay * by
        val cross = ax * by - ay * bx
        return abs(Math.toDegrees(atan2(cross.toDouble(), dot.toDouble()))).toFloat()
    }
}
