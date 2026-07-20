package com.hyperfiles.manager

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class FilesDevApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Theming.applyNightMode(this)

        // targetSdk 36 forces edge-to-edge (the windowOptOutEdgeToEdgeEnforcement flag is
        // ignored), so content draws under the status/navigation bars. Pad every activity's
        // content view by the system-bar insets so toolbars/menus aren't hidden under the
        // status bar and the bottom bar clears the navigation bar. When the platform is NOT
        // edge-to-edge, these insets arrive as 0, so there's no double padding.
        registerActivityLifecycleCallbacks(object : SimpleActivityLifecycle() {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is PlayerActivity) return   // intentionally full-screen / immersive
                val content = activity.findViewById<View>(android.R.id.content) ?: return
                ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
                    insets
                }
                ViewCompat.requestApplyInsets(content)
            }
        })
    }
}

/** No-op base so we only override onActivityCreated. */
private abstract class SimpleActivityLifecycle : Application.ActivityLifecycleCallbacks {
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
