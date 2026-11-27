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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth == 0f || viewHeight == 0f) return

        if (!isFourFiveEnabled) return

        // Calculate 4:5 crop area (centered)
        val targetRatio = 4f / 5f
        val currentRatio = viewWidth / viewHeight

        var cropWidth = viewWidth
        var cropHeight = viewHeight

        if (currentRatio > targetRatio) {
            // Too wide → crop sides
            cropWidth = viewHeight * targetRatio
        } else {
            // Too tall → crop top/bottom
            cropHeight = viewWidth / targetRatio
        }

        val left = (viewWidth - cropWidth) / 2f
        val top = (viewHeight - cropHeight) / 2f
        val right = left + cropWidth
        val bottom = top + cropHeight

        frameRect.set(left, top, right, bottom)

        // Dark mask outside the 4:5 area
        canvas.drawRect(0f, 0f, viewWidth, top, maskPaint)
        canvas.drawRect(0f, bottom, viewWidth, viewHeight, maskPaint)
        canvas.drawRect(0f, top, left, bottom, maskPaint)
        canvas.drawRect(right, top, viewWidth, bottom, maskPaint)

        // White border
        canvas.drawRect(frameRect, borderPaint)

        // Rule of thirds (optional)
        if (showRuleOfThirds) {
            val thirdW = cropWidth / 3f
            val thirdH = cropHeight / 3f

            canvas.drawLine(left + thirdW, top, left + thirdW, bottom, gridPaint)
            canvas.drawLine(left + thirdW * 2, top, left + thirdW * 2, bottom, gridPaint)
            canvas.drawLine(left, top + thirdH, right, top + thirdH, gridPaint)
            canvas.drawLine(left, top + thirdH * 2, right, top + thirdH * 2, gridPaint)
        }
    }
}
