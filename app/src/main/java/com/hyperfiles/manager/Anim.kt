package com.hyperfiles.manager

import android.view.View
import android.view.animation.AnimationUtils

object Anim {
    /** Quick bounce/pop feedback on a view (e.g. tile or button tap). */
    fun bounce(v: View) {
        v.startAnimation(AnimationUtils.loadAnimation(v.context, R.anim.bounce))
    }
}
