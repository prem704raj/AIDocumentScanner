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

/**
 * Safe four-point perspective correction with bounded output allocation.
 */
object PerspectiveCorrector {
    private const val MAX_OUTPUT_PIXELS = 16_000_000L

    fun correctPerspective(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        require(!bitmap.isRecycled) { "Source bitmap is recycled" }
        require(corners.size == 4) { "Four corners are required" }

        val safe = corners.map { point ->
            PointF(
                point.x.coerceIn(0f, bitmap.width.toFloat()),
                point.y.coerceIn(0f, bitmap.height.toFloat())
            )
        }

        var outputWidth = max(
            distance(safe[0], safe[1]),
            distance(safe[3], safe[2])
        ).toInt().coerceAtLeast(2)

        var outputHeight = max(
            distance(safe[0], safe[3]),
            distance(safe[1], safe[2])
        ).toInt().coerceAtLeast(2)

        val pixels = outputWidth.toLong() * outputHeight.toLong()
        if (pixels > MAX_OUTPUT_PIXELS) {
            val scale = sqrt(MAX_OUTPUT_PIXELS.toDouble() / pixels.toDouble())
            outputWidth = (outputWidth * scale).toInt().coerceAtLeast(2)
            outputHeight = (outputHeight * scale).toInt().coerceAtLeast(2)
        }

        val source = Mat()
        val output = Mat()
        val src = MatOfPoint2f(
            Point(safe[0].x.toDouble(), safe[0].y.toDouble()),
            Point(safe[1].x.toDouble(), safe[1].y.toDouble()),
            Point(safe[2].x.toDouble(), safe[2].y.toDouble()),
            Point(safe[3].x.toDouble(), safe[3].y.toDouble())
        )
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outputWidth.toDouble(), 0.0),
            Point(outputWidth.toDouble(), outputHeight.toDouble()),
            Point(0.0, outputHeight.toDouble())
        )
        var transform: Mat? = null

        try {
            Utils.bitmapToMat(bitmap, source)
            transform = Imgproc.getPerspectiveTransform(src, dst)
            Imgproc.warpPerspective(
                source,
                output,
                transform,
                Size(outputWidth.toDouble(), outputHeight.toDouble()),
                Imgproc.INTER_CUBIC,
                org.opencv.core.Core.BORDER_REPLICATE
            )

            return Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888
            ).also { Utils.matToBitmap(output, it) }
        } finally {
            source.release()
            output.release()
            src.release()
            dst.release()
            transform?.release()
        }
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }
}
