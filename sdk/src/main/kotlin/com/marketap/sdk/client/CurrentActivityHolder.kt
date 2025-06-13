package com.marketap.sdk.client

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

class CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var currentActivity: Activity? = null

    private var reservedAction: ((Activity) -> Unit)? = null
    fun useActivity(block: (Activity) -> Unit) {
        val activity = currentActivity
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            Log.d(
                "MESSAGE",
                activity.packageName + " useActivity: " + activity.javaClass.simpleName
            )
            block(activity)
        } else {
            reservedAction = block
        }
    }

    fun get(): Activity? = currentActivity

    override fun onActivityResumed(activity: Activity) {
        reservedAction?.let {
            reservedAction = null
            it(activity)
        }
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    // 나머지는 무시
    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
}