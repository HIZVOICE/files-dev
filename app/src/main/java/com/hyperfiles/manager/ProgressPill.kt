package com.hyperfiles.manager

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

/**
 * A floating progress pill that any Activity can host. It attaches to the
 * Activity's content view and mirrors ExtractionManager's overall progress,
 * so leaving/returning to a screen keeps the background extraction visible.
 */
object ProgressPill {

    fun attach(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewById<View>(R.id.extract_pill) != null) return  // already hosted here

        val pill = activity.layoutInflater.inflate(R.layout.view_progress_pill, content, false)
        val text = pill.findViewById<TextView>(R.id.pillText).apply { maxLines = 2 }
        val bar = pill.findViewById<ProgressBar>(R.id.pillBar)
        val cancel = pill.findViewById<ImageView>(R.id.pillCancel)
        cancel.setOnClickListener { ExtractionManager.cancel() }

        val density = activity.resources.displayMetrics.density
        val m = (density * 22).toInt()
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(m, m, m, (density * 28).toInt())
        }

        val listener: (ExtractionManager.State?) -> Unit = { s -> render(pill, text, bar, cancel, s) }
        pill.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = ExtractionManager.addListener(listener)
            override fun onViewDetachedFromWindow(v: View) = ExtractionManager.removeListener(listener)
        })
        content.addView(pill, lp)
    }

    private fun render(pill: View, text: TextView, bar: ProgressBar, cancel: ImageView, s: ExtractionManager.State?) {
        if (s == null) { pill.visibility = View.GONE; return }
        pill.visibility = View.VISIBLE
        cancel.visibility = if (s.status == ExtractionManager.Status.RUNNING) View.VISIBLE else View.GONE
        when (s.status) {
            ExtractionManager.Status.RUNNING -> {
                text.text = if (s.progress >= 0) "Extracting ${s.name} — ${s.progress}%"
                else "Extracting ${s.name}…"
                if (s.progress >= 0) { bar.isIndeterminate = false; bar.progress = s.progress }
                else bar.isIndeterminate = true
            }
            ExtractionManager.Status.DONE -> {
                text.text = "Extracted ${s.name}  ✓"
                bar.isIndeterminate = false; bar.progress = 100
            }
            ExtractionManager.Status.CANCELLED -> {
                text.text = "Extraction canceled"
                bar.isIndeterminate = false; bar.progress = 0
            }
            ExtractionManager.Status.FAILED -> {
                text.text = "Extract failed: ${s.error ?: ""}".trim()
                bar.isIndeterminate = false; bar.progress = 0
            }
        }
    }
}
