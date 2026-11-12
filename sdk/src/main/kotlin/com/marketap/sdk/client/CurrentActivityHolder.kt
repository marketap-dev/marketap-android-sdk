package com.marketap.sdk.client

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.marketap.sdk.utils.logger
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

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

    /** 앱이 지금 앞에 나와 있는 화면을 알려 준다. 생명주기 콜백 등록 전에 이미 resumed 였던 화면을 위해 resumed 로 기록한다. */
    fun set(activity: Activity) {
        visibility.onResumed(activity)
        currentActivityRef = WeakReference(activity)
        reservedAction?.let {
            reservedAction = null
            it(activity)
        }
    }

    fun get(): Activity? = currentActivityRef?.get()

    /** 가장 최근에 앞에 온 화면. 끝나는 중이거나 파괴된 화면이면 null. */
    internal fun getScreen(): Activity? =
        currentActivityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    /** 지금 resumed 상태인 가장 최근 화면. 메인 스레드에서 부른다. */
    internal fun resumedScreen(): Activity? =
        currentActivityRef?.get()?.takeIf { visibility.isResumed(it) && !it.isFinishing && !it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        visibility.onResumed(activity)
        currentActivityRef = WeakReference(activity)
        reservedAction?.let {
            reservedAction = null
            it(activity)
        }
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