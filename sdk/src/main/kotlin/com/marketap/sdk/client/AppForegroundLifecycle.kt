package com.marketap.sdk.client

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.marketap.sdk.domain.service.inapp.AppForegroundTracker

internal class AppForegroundLifecycle(
    private val tracker: AppForegroundTracker,
) : Application.ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: Activity) = tracker.screenResumed(activity)
    override fun onActivityPaused(activity: Activity) = tracker.screenPaused(activity)
    override fun onActivityStopped(activity: Activity) = tracker.screenStopped(activity)
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        /** 콜백 등록 전에 이미 앞에 있는 화면. 창 포커스가 있으면 resume된 것으로 본다. */
        fun probeResumedScreen(): Activity? =
            CurrentActivityHolder.getScreen()?.takeIf { it.hasWindowFocus() }
    }
}
