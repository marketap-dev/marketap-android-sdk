package com.marketap.sdk.client

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.marketap.sdk.client.inapp.InAppMessageActivity
import com.marketap.sdk.utils.logger
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var screenActivityRef: WeakReference<Activity>? = null

    private var reservedAction: ((Activity) -> Unit)? = null

    internal interface ScreenListener {
        fun onScreenResumed(activity: Activity)
        fun onScreenDestroyed(activity: Activity)
    }

    private val screenListeners = CopyOnWriteArrayList<ScreenListener>()

    internal fun interface ObscuredListener {
        fun onScreenObscured(activity: Activity)
    }

    private val obscuredListeners = CopyOnWriteArrayList<ObscuredListener>()

    internal val visibility = ActivityVisibility<Activity>()

    internal fun addObscuredListener(listener: ObscuredListener) {
        obscuredListeners.addIfAbsent(listener)
    }

    internal fun addScreenListener(listener: ScreenListener) {
        screenListeners.addIfAbsent(listener)
    }

    internal fun removeScreenListener(listener: ScreenListener) {
        screenListeners.remove(listener)
    }

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
        if (activity !is InAppMessageActivity) screenActivityRef = WeakReference(activity)
        reservedAction?.let {
            reservedAction = null
            it(activity)
        }
    }

    fun get(): Activity? = currentActivityRef?.get()

    /** 가장 최근에 앞에 온 앱 화면. 인앱 팝업 Activity 는 제외한다. */
    internal fun getScreen(): Activity? =
        screenActivityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        visibility.onResumed(activity)
        currentActivityRef = WeakReference(activity)
        reservedAction?.let {
            reservedAction = null
            it(activity)
        }
        if (activity !is InAppMessageActivity) screenActivityRef = WeakReference(activity)
        screenListeners.forEach { it.onScreenResumed(activity) }
    }

    override fun onActivityPaused(activity: Activity) {
        visibility.onPaused(activity)
    }

    override fun onActivityCreated(a: Activity, b: Bundle?) {}

    override fun onActivityStarted(a: Activity) {
        visibility.onStarted(a)
    }

    override fun onActivityStopped(a: Activity) {
        if (visibility.onStopped(a, a.isChangingConfigurations)) {
            obscuredListeners.forEach { it.onScreenObscured(a) }
        }
    }

    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {
        visibility.onDestroyed(a)
        screenListeners.forEach { it.onScreenDestroyed(a) }
    }


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