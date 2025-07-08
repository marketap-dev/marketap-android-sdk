package com.marketap.sdk.client

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.marketap.sdk.utils.logger
import java.lang.ref.WeakReference

object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    private var reservedAction: ((Activity) -> Unit)? = null

    fun useActivity(block: (Activity) -> Unit) {
        val activity = currentActivityRef?.get()
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            block(activity)
        } else {
            reservedAction = block
        }
    }

    fun set(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        reservedAction?.let {
            reservedAction = null
            it(activity)
        }
    }

    fun get(): Activity? = currentActivityRef?.get()

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        reservedAction?.let {
            reservedAction = null
            it(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}


    private var isRegistered = false
    fun applyToApplication(application: Application) {
        if (isRegistered) {
            logger.w { "applyToApplication: Already registered" }
            return
        }
        isRegistered = true
        application.registerActivityLifecycleCallbacks(this)
    }
}