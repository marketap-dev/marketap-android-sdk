package com.marketap.sdk.service.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.marketap.sdk.service.push.MarketapNotificationOpenHandler

internal class MarketapActivityLifecycleCallbacks(
    private val notificationOpenHandler: MarketapNotificationOpenHandler,
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
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Do nothing
    }

    override fun onActivityDestroyed(activity: Activity) {
    }
}