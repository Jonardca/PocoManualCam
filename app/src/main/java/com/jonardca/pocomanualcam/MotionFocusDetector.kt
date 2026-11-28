package com.jonardca.pocomanualcam

import android.graphics.Rect
import android.media.Image
import kotlin.math.abs

/**
 * Lightweight motion detection using Y-plane frame differencing.
 * No AI libraries – pure CPU.
 */
class MotionFocusDetector(
    private val sensorArraySize: Rect,
    private val sensitivityThreshold: Int = 35,
    private val minChangedPixels: Int = 12,
    private val maxChangedPixels: Int = 2500
) {
    private var previousFrameY: ByteArray? = null
    private var lastTriggerTime = 0L
    private val cooldownMs = 400L

    /**
     * @return Pair(previewBox 320x240 coords, sensorBox in active array coords)
     *         or null if no significant motion
     */
    fun processFrame(image: Image, activeCropRegion: Rect? = null): Pair<Rect, Rect>? {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride

        val currentFrameY = ByteArray(width * height)
        for (row in 0 until height) {
            yBuffer.position(row * rowStride)
            yBuffer.get(currentFrameY, row * width, width)
        }

        val prevY = previousFrameY
        previousFrameY = currentFrameY
        if (prevY == null || prevY.size != currentFrameY.size) return null

        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var changedCount = 0

        // Sample every 2 pixels for speed
        for (y in 0 until height step 2) {
            val rowOffset = y * width
            for (x in 0 until width step 2) {
                val idx = rowOffset + x
                val diff = abs((currentFrameY[idx].toInt() and 0xFF) - (prevY[idx].toInt() and 0xFF))
                if (diff > sensitivityThreshold) {
                    changedCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (changedCount !in minChangedPixels..maxChangedPixels) return null

        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < cooldownMs) return null
        lastTriggerTime = now

        // Add padding
        val padX = (width * 0.08f).toInt()
        val padY = (height * 0.08f).toInt()
        val boxLeft = (minX - padX).coerceAtLeast(0)
        val boxTop = (minY - padY).coerceAtLeast(0)
        val boxRight = (maxX + padX).coerceAtMost(width)
        val boxBottom = (maxY + padY).coerceAtMost(height)

        val previewBox = Rect(boxLeft, boxTop, boxRight, boxBottom)

        // Map to sensor / active crop coordinates
        val targetRegion = activeCropRegion ?: sensorArraySize
        val scaleX = targetRegion.width().toFloat() / width.toFloat()
        val scaleY = targetRegion.height().toFloat() / height.toFloat()

        val sensorBox = Rect(
            (targetRegion.left + boxLeft * scaleX).toInt().coerceIn(targetRegion.left, targetRegion.right - 1),
            (targetRegion.top + boxTop * scaleY).toInt().coerceIn(targetRegion.top, targetRegion.bottom - 1),
            (targetRegion.left + boxRight * scaleX).toInt().coerceIn(targetRegion.left + 1, targetRegion.right),
            (targetRegion.top + boxBottom * scaleY).toInt().coerceIn(targetRegion.top + 1, targetRegion.bottom)
        )

        return Pair(previewBox, sensorBox)
    }

    fun reset() {
        previousFrameY = null
        lastTriggerTime = 0L
    }
}
