package com.example.camera_segmentation_app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

class Utils {
    companion object {
        fun segmentResultToBitmap(
                segmentedImage: IntArray,
                classColors: Array<Int>,
                targetBitmap: Bitmap
        ) {
            targetBitmap.apply {
                val pixels = IntArray(width * height) {
                    classColors[segmentedImage[it]]
                }

                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }

        fun drawReversingGuidanceOverlay(bitmap: Bitmap, guidanceState: Int) {
            val canvas = Canvas(bitmap)
            val width = bitmap.width.toFloat()
            val height = bitmap.height.toFloat()

            // Key Y coordinates for perspective zones (Far = Green, Mid = Yellow, Near = Red)
            val yStart = height * 0.50f
            val yGreen = height * 0.55f
            val yYellow = height * 0.72f
            val yRed = height * 0.88f
            val yEnd = height * 0.95f

            // Perspective X boundaries (Slanted trapezoid matching vehicle/walking corridor)
            fun getLeftX(y: Float): Float {
                val t = (y - yStart) / (yEnd - yStart)
                return width * 0.28f + t * (width * 0.10f - width * 0.28f)
            }

            fun getRightX(y: Float): Float {
                val t = (y - yStart) / (yEnd - yStart)
                return width * 0.72f + t * (width * 0.90f - width * 0.72f)
            }

            val strokeW = (width * 0.02f).coerceAtLeast(2f)

            val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GREEN
                strokeWidth = strokeW
                style = Paint.Style.STROKE
            }

            val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.YELLOW
                strokeWidth = strokeW
                style = Paint.Style.STROKE
            }

            val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                strokeWidth = strokeW * 1.5f
                style = Paint.Style.STROKE
            }

            // 1. Draw Trapezoidal Corridor Side Lines
            canvas.drawLine(getLeftX(yStart), yStart, getLeftX(yGreen), yGreen, greenPaint)
            canvas.drawLine(getLeftX(yGreen), yGreen, getLeftX(yYellow), yYellow, yellowPaint)
            canvas.drawLine(getLeftX(yYellow), yYellow, getLeftX(yEnd), yEnd, redPaint)

            canvas.drawLine(getRightX(yStart), yStart, getRightX(yGreen), yGreen, greenPaint)
            canvas.drawLine(getRightX(yGreen), yGreen, getRightX(yYellow), yYellow, yellowPaint)
            canvas.drawLine(getRightX(yYellow), yYellow, getRightX(yEnd), yEnd, redPaint)

            // 2. Draw Distance Crossbars (Green, Yellow, Red)
            canvas.drawLine(getLeftX(yGreen), yGreen, getRightX(yGreen), yGreen, greenPaint)
            canvas.drawLine(getLeftX(yYellow), yYellow, getRightX(yYellow), yYellow, yellowPaint)
            canvas.drawLine(getLeftX(yRed), yRed, getRightX(yRed), yRed, redPaint)

            // 3. Draw Side Distance Ticks
            val tickSize = width * 0.04f
            canvas.drawLine(getLeftX(yYellow), yYellow, getLeftX(yYellow) - tickSize, yYellow, yellowPaint)
            canvas.drawLine(getRightX(yYellow), yYellow, getRightX(yYellow) + tickSize, yYellow, yellowPaint)
            canvas.drawLine(getLeftX(yRed), yRed, getLeftX(yRed) - tickSize, yRed, redPaint)
            canvas.drawLine(getRightX(yRed), yRed, getRightX(yRed) + tickSize, yRed, redPaint)

            // 4. Draw Obstacle Hazard Shading when path is blocked or obstacle detected
            if (guidanceState == 5 || guidanceState == 2 || guidanceState == 3 || guidanceState == 4) {
                val alertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(90, 255, 0, 0)
                    style = Paint.Style.FILL
                }
                val path = Path().apply {
                    moveTo(getLeftX(yYellow), yYellow)
                    lineTo(getRightX(yYellow), yYellow)
                    lineTo(getRightX(yEnd), yEnd)
                    lineTo(getLeftX(yEnd), yEnd)
                    close()
                }
                canvas.drawPath(path, alertPaint)
            }
        }

        fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val scaledBitmap = if (height > width)
                Bitmap.createScaledBitmap(bitmap, width, width, true)
            else
                Bitmap.createScaledBitmap(bitmap, height, height, true)

            val pX = (width - scaledBitmap.width) / 2
            val pY = (height - scaledBitmap.height) / 2
            val can = Canvas(result)
            can.drawBitmap(scaledBitmap, pX.toFloat(), pY.toFloat(), null)

            return result
        }
    }
}
