package com.jonardca.pocomanualcam

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class AspectRatioOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isFourFiveEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var showRuleOfThirds: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // Face / tracking boxes (screen coordinates)
    private val faceRects = mutableListOf<RectF>()
    private val trackingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
    private val trackingFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A00E676")
        style = Paint.Style.FILL
    }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private val frameRect = RectF()

    fun updateFaceBoxes(boxes: List<RectF>) {
        faceRects.clear()
        faceRects.addAll(boxes)
        invalidate()
    }

    fun clearFaceBoxes() {
        faceRects.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth == 0f || viewHeight == 0f) return

        // 4:5 Guide
        if (isFourFiveEnabled) {
            val targetRatio = 4f / 5f
            val currentRatio = viewWidth / viewHeight

            var cropWidth = viewWidth
            var cropHeight = viewHeight

            if (currentRatio > targetRatio) {
                cropWidth = viewHeight * targetRatio
            } else {
                cropHeight = viewWidth / targetRatio
            }

            val left = (viewWidth - cropWidth) / 2f
            val top = (viewHeight - cropHeight) / 2f
            val right = left + cropWidth
            val bottom = top + cropHeight

            frameRect.set(left, top, right, bottom)

            canvas.drawRect(0f, 0f, viewWidth, top, maskPaint)
            canvas.drawRect(0f, bottom, viewWidth, viewHeight, maskPaint)
            canvas.drawRect(0f, top, left, bottom, maskPaint)
            canvas.drawRect(right, top, viewWidth, bottom, maskPaint)
            canvas.drawRect(frameRect, borderPaint)

            if (showRuleOfThirds) {
                val thirdW = cropWidth / 3f
                val thirdH = cropHeight / 3f
                canvas.drawLine(left + thirdW, top, left + thirdW, bottom, gridPaint)
                canvas.drawLine(left + thirdW * 2, top, left + thirdW * 2, bottom, gridPaint)
                canvas.drawLine(left, top + thirdH, right, top + thirdH, gridPaint)
                canvas.drawLine(left, top + thirdH * 2, right, top + thirdH * 2, gridPaint)
            }
        }

        // Face / tracking boxes (DSLR-style corner brackets)
        for (box in faceRects) {
            canvas.drawRect(box, trackingFillPaint)

            val len = (minOf(box.width(), box.height()) * 0.25f)
                .coerceAtLeast(12f * resources.displayMetrics.density)

            // Top-left
            canvas.drawLine(box.left, box.top, box.left + len, box.top, trackingPaint)
            canvas.drawLine(box.left, box.top, box.left, box.top + len, trackingPaint)
            // Top-right
            canvas.drawLine(box.right - len, box.top, box.right, box.top, trackingPaint)
            canvas.drawLine(box.right, box.top, box.right, box.top + len, trackingPaint)
            // Bottom-left
            canvas.drawLine(box.left, box.bottom - len, box.left, box.bottom, trackingPaint)
            canvas.drawLine(box.left, box.bottom, box.left + len, box.bottom, trackingPaint)
            // Bottom-right
            canvas.drawLine(box.right - len, box.bottom, box.right, box.bottom, trackingPaint)
            canvas.drawLine(box.right, box.bottom - len, box.right, box.bottom, trackingPaint)
        }
    }
}
