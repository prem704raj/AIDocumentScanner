package com.example.aidocumentscanner.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Document-edge detector optimized for phone scans.
 *
 * Detection runs on a bounded working image and maps the winning quadrilateral back to
 * source coordinates. Confidence combines occupied area, rectangularity, edge margin,
 * and near-right-angle geometry instead of choosing the first four-point contour.
 */
object DocumentScanner {
    private const val TAG = "DocumentScanner"
    private const val MAX_DETECTION_DIMENSION = 1400
    private const val MIN_AREA_RATIO = 0.12
    private const val MAX_AREA_RATIO = 0.985

    data class ScanResult(
        val corners: List<PointF>,
        val confidence: Float,
        val croppedBitmap: Bitmap? = null
    )

    data class AutoCropResult(
        val bitmap: Bitmap,
        val confidence: Float,
        val wasCropped: Boolean
    )

    fun detectDocumentEdges(bitmap: Bitmap): ScanResult {
        if (bitmap.isRecycled || bitmap.width < 2 || bitmap.height < 2) {
            return ScanResult(emptyList(), 0f)
        }
        if (!OpenCVManager.isReady()) {
            return ScanResult(fullBounds(bitmap), 0f)
        }

        val largest = max(bitmap.width, bitmap.height)
        val workScale = if (largest > MAX_DETECTION_DIMENSION) {
            MAX_DETECTION_DIMENSION.toFloat() / largest
        } else {
            1f
        }
        val working = if (workScale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * workScale).toInt().coerceAtLeast(2),
                (bitmap.height * workScale).toInt().coerceAtLeast(2),
                true
            )
        } else {
            bitmap
        }

        val source = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val closed = Mat()
        val hierarchy = Mat()
        var kernel: Mat? = null
        val contours = ArrayList<MatOfPoint>()

        return try {
            Utils.bitmapToMat(working, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)

            // Contrast normalization makes white paper on uneven desks more consistent.
            Imgproc.equalizeHist(gray, gray)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            val median = approximateMedian(blurred)
            val lower = max(20.0, 0.66 * median)
            val upper = min(240.0, 1.33 * median + 25.0)
            Imgproc.Canny(blurred, edges, lower, upper)

            kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(5.0, 5.0)
            )
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

            Imgproc.findContours(
                closed,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea = source.cols().toDouble() * source.rows().toDouble()
            var bestCorners: List<PointF>? = null
            var bestScore = 0f

            contours
                .asSequence()
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(25)
                .forEach { contour ->
                    val area = Imgproc.contourArea(contour)
                    val areaRatio = area / imageArea
                    if (areaRatio !in MIN_AREA_RATIO..MAX_AREA_RATIO) return@forEach

                    val contour2f = MatOfPoint2f(*contour.toArray())
                    try {
                        val perimeter = Imgproc.arcLength(contour2f, true)
                        for (epsilon in listOf(0.015, 0.02, 0.025, 0.03)) {
                            val approx = MatOfPoint2f()
                            try {
                                Imgproc.approxPolyDP(
                                    contour2f,
                                    approx,
                                    epsilon * perimeter,
                                    true
                                )
                                if (approx.rows() != 4) continue

                                val points = approx.toArray()
                                if (!Imgproc.isContourConvex(MatOfPoint(*points))) continue

                                val ordered = orderPoints(
                                    points.map { PointF(it.x.toFloat(), it.y.toFloat()) }
                                )
                                if (!isValidQuadrilateral(ordered)) continue

                                val score = scoreQuadrilateral(
                                    ordered,
                                    source.cols().toFloat(),
                                    source.rows().toFloat()
                                )
                                if (score > bestScore) {
                                    bestScore = score
                                    bestCorners = ordered
                                }
                            } finally {
                                approx.release()
                            }
                        }
                    } finally {
                        contour2f.release()
                    }
                }

            val winner = bestCorners ?: return ScanResult(fullBounds(bitmap), 0f)
            val inverse = 1f / workScale
            val mapped = winner.map { p ->
                PointF(
                    (p.x * inverse).coerceIn(0f, bitmap.width.toFloat()),
                    (p.y * inverse).coerceIn(0f, bitmap.height.toFloat())
                )
            }
            ScanResult(mapped, bestScore.coerceIn(0f, 1f))
        } catch (error: Throwable) {
            Log.e(TAG, "Document detection failed", error)
            ScanResult(fullBounds(bitmap), 0f)
        } finally {
            source.release()
            gray.release()
            blurred.release()
            edges.release()
            closed.release()
            hierarchy.release()
            kernel?.release()
            contours.forEach { it.release() }
            if (working !== bitmap && !working.isRecycled) working.recycle()
        }
    }

    /**
     * Applies automatic perspective correction only when confidence is high enough.
     * A failed/weak detection returns an independent copy of the source so caller ownership
     * stays simple.
     */
    fun autoCrop(
        bitmap: Bitmap,
        minConfidence: Float = 0.55f
    ): AutoCropResult {
        val detection = detectDocumentEdges(bitmap)
        if (detection.corners.size != 4 || detection.confidence < minConfidence) {
            return AutoCropResult(
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false),
                detection.confidence,
                false
            )
        }

        return runCatching {
            AutoCropResult(
                PerspectiveCorrector.correctPerspective(bitmap, detection.corners),
                detection.confidence,
                true
            )
        }.getOrElse {
            AutoCropResult(
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false),
                detection.confidence,
                false
            )
        }
    }

    private fun approximateMedian(gray: Mat): Double {
        val mean = Core.mean(gray).`val`[0]
        return mean.coerceIn(35.0, 220.0)
    }

    private fun scoreQuadrilateral(
        corners: List<PointF>,
        width: Float,
        height: Float
    ): Float {
        val areaRatio = polygonArea(corners) / (width * height)
        val areaScore = ((areaRatio - MIN_AREA_RATIO) /
            (0.80 - MIN_AREA_RATIO)).coerceIn(0.0, 1.0).toFloat()

        val angleError = corners.indices
            .map { i ->
                val previous = corners[(i + 3) % 4]
                val current = corners[i]
                val next = corners[(i + 1) % 4]
                abs(90f - angle(previous, current, next))
            }
            .average()
            .toFloat()
        val angleScore = (1f - angleError / 35f).coerceIn(0f, 1f)

        val top = distance(corners[0], corners[1])
        val bottom = distance(corners[3], corners[2])
        val left = distance(corners[0], corners[3])
        val right = distance(corners[1], corners[2])
        val parallelScore = (
            ratioSimilarity(top, bottom) + ratioSimilarity(left, right)
        ) / 2f

        val margin = min(width, height) * 0.012f
        val touchesEdge = corners.count {
            it.x < margin || it.y < margin ||
                it.x > width - margin || it.y > height - margin
        }
        val marginScore = 1f - touchesEdge * 0.08f

        return (
            areaScore * 0.42f +
                angleScore * 0.30f +
                parallelScore * 0.20f +
                marginScore * 0.08f
            ).coerceIn(0f, 1f)
    }

    private fun ratioSimilarity(a: Float, b: Float): Float {
        val bigger = max(a, b)
        if (bigger <= 0f) return 0f
        return (min(a, b) / bigger).coerceIn(0f, 1f)
    }

    private fun isValidQuadrilateral(points: List<PointF>): Boolean {
        if (points.size != 4) return false
        val edges = listOf(
            distance(points[0], points[1]),
            distance(points[1], points[2]),
            distance(points[2], points[3]),
            distance(points[3], points[0])
        )
        if (edges.any { it < 30f }) return false
        return polygonArea(points) > 1_000.0
    }

    private fun orderPoints(points: List<PointF>): List<PointF> {
        require(points.size == 4)
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val remaining = points.filter { it !== topLeft && it !== bottomRight }
        val topRight = remaining.maxBy { it.x - it.y }
        val bottomLeft = remaining.minBy { it.x - it.y }
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun polygonArea(points: List<PointF>): Double {
        if (points.size != 4) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            val next = (i + 1) % points.size
            sum += points[i].x * points[next].y - points[next].y * points[i].x
        }
        return abs(sum) / 2.0
    }

    private fun distance(a: PointF, b: PointF): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun angle(a: PointF, center: PointF, b: PointF): Float {
        val v1x = a.x - center.x
        val v1y = a.y - center.y
        val v2x = b.x - center.x
        val v2y = b.y - center.y
        val dot = v1x * v2x + v1y * v2y
        val cross = v1x * v2y - v1y * v2x
        return Math.toDegrees(
            kotlin.math.atan2(abs(cross).toDouble(), dot.toDouble())
        ).toFloat()
    }

    private fun fullBounds(bitmap: Bitmap): List<PointF> = listOf(
        PointF(0f, 0f),
        PointF(bitmap.width.toFloat(), 0f),
        PointF(bitmap.width.toFloat(), bitmap.height.toFloat()),
        PointF(0f, bitmap.height.toFloat())
    )
}
