package com.marketap.sdk.domain.service

import android.app.Activity
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.utils.logger

internal class MarketapCoreService(
    private val eventIngestionService: EventIngestionService,
    private val userIngestionService: UserIngestionService,
    private val deviceManager: DeviceManager,
) {

    fun identify(userId: String, properties: Map<String, Any>?) {
        try {
            userIngestionService.identify(userId, properties ?: emptyMap())
        } catch (e: Exception) {
            // Ignore
            logger.e("Failed to identify user", userId, exception = e)
        }
    }

    fun resetIdentity() {
        try {
            userIngestionService.resetIdentity()
        } catch (e: Exception) {
            // Ignore
            logger.e("Failed to reset identity", exception = e)
        }
    }

    fun track(name: String, properties: Map<String, Any>?) {
        try {
            eventIngestionService.trackEvent(name, properties ?: emptyMap())
        } catch (e: Exception) {
            // Ignore
            logger.e("Failed to track event", name, exception = e)
        }
    }

    fun requestAuthorizationForPushNotifications(activity: Activity) {
        try {
            deviceManager.requestAuthorizationForPushNotifications(activity)
        } catch (e: Exception) {
            // Ignore
            logger.e(
                "Failed to request push notification authorization",
                exception = e
            )
        }
    }
}