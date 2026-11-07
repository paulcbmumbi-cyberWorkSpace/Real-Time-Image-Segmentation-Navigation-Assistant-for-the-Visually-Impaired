package com.example.camera_segmentation_app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class GuidanceOverlayView : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var guidanceState: Int = MainActivity.STATE_ALL_CLEAR
        set(value) {
            field = value
            postInvalidate()
        }

    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
    }

    private val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
    }

    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
    }

    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val alertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 0, 0)
        style = Paint.Style.FILL
    }

    private val narrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 255, 255)
        style = Paint.Style.FILL
    }

    private val hazardPath = Path()
    private val passagePath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val yStart = h * 0.45f
        val yGreen = h * 0.55f
        val yYellow = h * 0.72f
        val yRed = h * 0.88f
        val yEnd = h * 0.96f

        fun getLeftX(y: Float): Float {
            val t = (y - yStart) / (yEnd - yStart)
            return w * 0.25f + t * (w * 0.08f - w * 0.25f)
        }

        fun getRightX(y: Float): Float {
            val t = (y - yStart) / (yEnd - yStart)
            return w * 0.75f + t * (w * 0.92f - w * 0.75f)
        }

        val strokeW = (w * 0.012f).coerceAtLeast(4f)
        greenPaint.strokeWidth = strokeW
        yellowPaint.strokeWidth = strokeW
        redPaint.strokeWidth = strokeW * 1.5f
        cyanPaint.strokeWidth = strokeW * 1.5f

        // 1. Draw Side Boundary Lines (Trapezoid effect matching path perspective)
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

        // 3. Draw Side Distance Tick Markers
        val tickSize = w * 0.035f
        canvas.drawLine(getLeftX(yYellow), yYellow, getLeftX(yYellow) - tickSize, yYellow, yellowPaint)
        canvas.drawLine(getRightX(yYellow), yYellow, getRightX(yYellow) + tickSize, yYellow, yellowPaint)
        canvas.drawLine(getLeftX(yRed), yRed, getLeftX(yRed) - tickSize, yRed, redPaint)
        canvas.drawLine(getRightX(yRed), yRed, getRightX(yRed) + tickSize, yRed, redPaint)

        // 4. Highlight Obstacle Hazard Shading when path is blocked or obstacle detected
        if (guidanceState == MainActivity.STATE_BLOCKED ||
            guidanceState == MainActivity.STATE_OBSTACLE_CENTER ||
            guidanceState == MainActivity.STATE_OBSTACLE_LEFT ||
            guidanceState == MainActivity.STATE_OBSTACLE_RIGHT) {

            hazardPath.reset()
            hazardPath.moveTo(getLeftX(yYellow), yYellow)
            hazardPath.lineTo(getRightX(yYellow), yYellow)
            hazardPath.lineTo(getRightX(yEnd), yEnd)
            hazardPath.lineTo(getLeftX(yEnd), yEnd)
            hazardPath.close()
            canvas.drawPath(hazardPath, alertPaint)
        }

        // 5. Draw Narrow Passage / Doorway Guidance Alignment Guide
        if (guidanceState == MainActivity.STATE_NARROW_PASSAGE_CENTER ||
            guidanceState == MainActivity.STATE_NARROW_PASSAGE_SHIFT_LEFT ||
            guidanceState == MainActivity.STATE_NARROW_PASSAGE_SHIFT_RIGHT) {

            var nLeftOffset = w * 0.35f
            var nRightOffset = w * 0.65f

            if (guidanceState == MainActivity.STATE_NARROW_PASSAGE_SHIFT_LEFT) {
                nLeftOffset = w * 0.22f
                nRightOffset = w * 0.52f
            } else if (guidanceState == MainActivity.STATE_NARROW_PASSAGE_SHIFT_RIGHT) {
                nLeftOffset = w * 0.48f
                nRightOffset = w * 0.78f
            }

            // Draw cyan narrow passage guide corridor
            passagePath.reset()
            passagePath.moveTo(nLeftOffset, yStart)
            passagePath.lineTo(nRightOffset, yStart)
            passagePath.lineTo(nRightOffset, yEnd)
            passagePath.lineTo(nLeftOffset, yEnd)
            passagePath.close()

            canvas.drawPath(passagePath, narrowFillPaint)
            canvas.drawLine(nLeftOffset, yStart, nLeftOffset, yEnd, cyanPaint)
            canvas.drawLine(nRightOffset, yStart, nRightOffset, yEnd, cyanPaint)
        }
    }
}
