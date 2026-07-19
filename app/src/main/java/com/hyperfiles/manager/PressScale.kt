package com.hyperfiles.manager

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * HyperOS "Liquid Engine"-style springy press.
 *
 * On touch-down the view shrinks to [down] with a stiff, near-critical spring; on
 * release/cancel it springs back to 1f with an underdamped, rubbery overshoot
 * (damping ratio ~0.45). Uses [SpringAnimation.animateToFinalPosition], which
 * retargets an in-flight spring and preserves its velocity — so the motion is
 * fully interruptible (a re-press mid-overshoot continues smoothly, never snaps).
 *
 * Click-safe: the touch listener never consumes events (returns false), so the
 * view's own click / long-press / ripple handling runs normally. Attaches once per
 * physical view (guarded by a tag) so RecyclerView recycling doesn't stack springs.
 */
object PressScale {

    // Down: barely any bounce inbound, very fast.
    private const val DOWN_DAMPING = SpringForce.DAMPING_RATIO_LOW_BOUNCY   // 0.75
    private const val DOWN_STIFFNESS = SpringForce.STIFFNESS_HIGH           // 10000
    // Up: the signature rubbery overshoot.
    private const val UP_DAMPING = 0.45f
    private const val UP_STIFFNESS = SpringForce.STIFFNESS_MEDIUM           // 1500

    @SuppressLint("ClickableViewAccessibility")
    fun attach(v: View, down: Float = 0.94f) {
        if (v.getTag(R.id.tag_liquid_bounce) == true) return
        v.setTag(R.id.tag_liquid_bounce, true)

        val sx = SpringAnimation(v, SpringAnimation.SCALE_X, 1f)
        val sy = SpringAnimation(v, SpringAnimation.SCALE_Y, 1f)

        fun drive(target: Float, damping: Float, stiffness: Float) {
            for (anim in arrayOf(sx, sy)) {
                anim.spring = (anim.spring ?: SpringForce()).apply {
                    finalPosition = target
                    dampingRatio = damping
                    this.stiffness = stiffness
                }
                anim.animateToFinalPosition(target)
            }
        }

        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> drive(down, DOWN_DAMPING, DOWN_STIFFNESS)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drive(1f, UP_DAMPING, UP_STIFFNESS)
            }
            false // never consume — clicks, long-press and ripple keep working
        }
    }
}
