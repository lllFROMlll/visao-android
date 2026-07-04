package com.example.visaoandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.tensorflow.lite.task.vision.detector.Detection

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var detections: List<Detection> = listOf()
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 42f
    }

    fun setResults(results: List<Detection>) {
        detections = results
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        for (detection in detections) {
            val box = detection.boundingBox
            canvas.drawRect(box, boxPaint)
            val label = detection.categories.firstOrNull()?.label ?: ""
            canvas.drawText(label, box.left, box.top - 10f, textPaint)
        }
    }
}
