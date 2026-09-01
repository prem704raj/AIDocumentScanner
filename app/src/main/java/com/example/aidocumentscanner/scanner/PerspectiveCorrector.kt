package com.example.aidocumentscanner.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.sqrt

object PerspectiveCorrector {
    private const val MAX_OUTPUT_PIXELS = 16_000_000L

    fun correctPerspective(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        require(!bitmap.isRecycled) { "Source bitmap is recycled" }
        require(corners.size == 4) { "Perspective correction needs exactly four corners" }

        val safe = corners.map { point ->
            PointF(
                point.x.coerceIn(0f, bitmap.width.toFloat()),
                point.y.coerceIn(0f, bitmap.height.toFloat())
            )
        }

        val rawWidth = max(distance(safe[0], safe[1]), distance(safe[3], safe[2]))
            .toInt().coerceAtLeast(2)
        val rawHeight = max(distance(safe[0], safe[3]), distance(safe[1], safe[2]))
            .toInt().coerceAtLeast(2)

        val outputScale = minOf(
            1.0,
            sqrt(MAX_OUTPUT_PIXELS.toDouble() / (rawWidth.toLong() * rawHeight.toLong()).toDouble())
        )
        val width = (rawWidth * outputScale).toInt().coerceAtLeast(2)
        val height = (rawHeight * outputScale).toInt().coerceAtLeast(2)

        val source = Mat()
        val output = Mat()
        val sourcePoints = MatOfPoint2f(
            Point(safe[0].x.toDouble(), safe[0].y.toDouble()),
            Point(safe[1].x.toDouble(), safe[1].y.toDouble()),
            Point(safe[2].x.toDouble(), safe[2].y.toDouble()),
            Point(safe[3].x.toDouble(), safe[3].y.toDouble())
        )
        val destinationPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width.toDouble(), 0.0),
            Point(width.toDouble(), height.toDouble()),
            Point(0.0, height.toDouble())
        )
        var transform: Mat? = null

        try {
            Utils.bitmapToMat(bitmap, source)
            transform = Imgproc.getPerspectiveTransform(sourcePoints, destinationPoints)
            Imgproc.warpPerspective(
                source,
                output,
                transform,
                Size(width.toDouble(), height.toDouble()),
                Imgproc.INTER_LINEAR
            )
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(output, it)
            }
        } finally {
            source.release()
            output.release()
            sourcePoints.release()
            destinationPoints.release()
            transform?.release()
        }
    }

    private fun distance(first: PointF, second: PointF): Float {
        val dx = second.x - first.x
        val dy = second.y - first.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}
