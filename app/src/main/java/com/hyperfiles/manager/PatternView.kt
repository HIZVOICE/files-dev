package com.hyperfiles.manager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** Simple 3x3 pattern lock. Reports the node sequence (0..8) on finger up. */
class PatternView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onComplete: ((List<Int>) -> Unit)? = null

    private val centers = Array(9) { PointF() }
    private val selected = ArrayList<Int>()
    private var curX = 0f
    private var curY = 0f
    private var tracking = false

    private val accent = Color.parseColor("#3482FF")
    private val dim = Color.parseColor("#66888888")

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = dim
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = accent }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 10f; color = accent; strokeCap = Paint.Cap.ROUND
    }

    private var nodeRadius = 18f
    private var hitRadius = 60f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = minOf(w, h).toFloat()
        val pad = size * 0.14f
        val step = (size - 2 * pad) / 2f
        val offX = (w - size) / 2f
        val offY = (h - size) / 2f
        for (i in 0..8) {
            val r = i / 3; val c = i % 3
            centers[i].set(offX + pad + c * step, offY + pad + r * step)
        }
        nodeRadius = size * 0.035f
        hitRadius = step * 0.5f
    }

    fun reset() { selected.clear(); tracking = false; invalidate() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { selected.clear(); tracking = true; addNodeAt(event.x, event.y); curX = event.x; curY = event.y; invalidate() }
            MotionEvent.ACTION_MOVE -> if (tracking) { curX = event.x; curY = event.y; addNodeAt(event.x, event.y); invalidate() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tracking) {
                    tracking = false
                    if (selected.isNotEmpty()) onComplete?.invoke(selected.toList())
                    invalidate()
                }
            }
        }
        return true
    }

    private fun addNodeAt(x: Float, y: Float) {
        for (i in 0..8) {
            if (i !in selected && hypot((x - centers[i].x).toDouble(), (y - centers[i].y).toDouble()) < hitRadius) {
                selected.add(i); break
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // lines between selected nodes
        for (i in 0 until selected.size - 1) {
            val a = centers[selected[i]]; val b = centers[selected[i + 1]]
            canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
        }
        if (tracking && selected.isNotEmpty()) {
            val last = centers[selected.last()]
            canvas.drawLine(last.x, last.y, curX, curY, linePaint)
        }
        for (i in 0..8) {
            val c = centers[i]
            if (i in selected) canvas.drawCircle(c.x, c.y, nodeRadius * 1.6f, fill)
            else canvas.drawCircle(c.x, c.y, nodeRadius, outline)
        }
    }
}
