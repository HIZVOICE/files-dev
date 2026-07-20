package com.hyperfiles.manager

import android.graphics.Canvas
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

/** iOS-style springy overscroll bounce for RecyclerViews (top & bottom). */
object Bounce {
    fun attach(rv: RecyclerView) {
        rv.edgeEffectFactory = BounceEdgeEffectFactory()
    }
}

private class BounceEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {
    override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
        return object : EdgeEffect(recyclerView.context) {
            private val pullFraction = 0.32f
            private val flingFraction = 0.4f
            private var anim: SpringAnimation? = null

            override fun onPull(deltaDistance: Float) = handlePull(deltaDistance)
            override fun onPull(deltaDistance: Float, displacement: Float) = handlePull(deltaDistance)

            private fun handlePull(deltaDistance: Float) {
                anim?.cancel()
                val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                recyclerView.translationY += sign * recyclerView.height * deltaDistance * pullFraction
            }

            override fun onRelease() {
                if (recyclerView.translationY != 0f) settle(0f)
            }

            override fun onAbsorb(velocity: Int) {
                val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                settle(sign * velocity * flingFraction)
            }

            private fun settle(startVelocity: Float) {
                anim?.cancel()
                anim = SpringAnimation(recyclerView, SpringAnimation.TRANSLATION_Y, 0f).apply {
                    if (startVelocity != 0f) setStartVelocity(startVelocity)
                    spring = SpringForce(0f).apply {
                        dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                        stiffness = SpringForce.STIFFNESS_LOW
                    }
                    start()
                }
            }

            // We animate translationY ourselves; suppress the default glow.
            override fun draw(canvas: Canvas?) = false
            override fun isFinished() = anim?.isRunning != true
        }
    }
}
