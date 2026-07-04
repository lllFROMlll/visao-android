package com.example.visaoandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import org.tensorflow.lite.task.vision.detector.Detection

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var detections: List<Detection> = listOf()
    private var faces: List<RectF> = listOf()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var lastSeenTime: Long = 0
    private val holdMillis = 400L

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val facePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 42f
    }

    fun setResults(results: List<Detection>, faceRects: List<RectF>, imgWidth: Int, imgHeight: Int) {
        val now = System.currentTimeMillis()
        if (results.isNotEmpty() || faceRects.isNotEmpty()) {
            detections = results
            faces = faceRects
            lastSeenTime = now
        } else if (now - lastSeenTime > holdMillis) {
            detections = results
            faces = faceRects
        }
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (imageWidth == 0 || imageHeight == 0) return

        val scaleX = width.toFloat() / imageWidth.toFloat()
        val scaleY = height.toFloat() / imageHeight.toFloat()

        for (detection in detections) {
            val box = detection.boundingBox
            val left = box.left * scaleX
            val top = box.top * scaleY
            val right = box.right * scaleX
            val bottom = box.bottom * scaleY
            canvas.drawRect(left, top, right, bottom, boxPaint)
            val label = detection.categories.firstOrNull()?.label ?: ""
            canvas.drawText(label, left, top - 10f, textPaint)
        }

        for (face in faces) {
            val left = face.left * scaleX
            val top = face.top * scaleY
            val right = face.right * scaleX
            val bottom = face.bottom * scaleY
            canvas.drawRect(left, top, right, bottom, facePaint)
        }
    }
}
