package com.marketap.sdk.service.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.marketap.sdk.service.push.MarketapNotificationOpenHandler

internal class MarketapActivityLifecycleCallbacks(
    private val notificationOpenHandler: MarketapNotificationOpenHandler,
    private val activityManager: ActivityManager,
    application: Application
) : Application.ActivityLifecycleCallbacks {
    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
        notificationOpenHandler.maybeClickPush(activity)
        if (activity is AppCompatActivity) {
            activityManager.setCurrentActivity(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity == activityManager.getCurrentActivity()) {
            activityManager.setCurrentActivity(null)
        }
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }
}