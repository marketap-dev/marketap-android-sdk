package com.marketap.sdk.domain.service

import android.app.Activity
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.utils.logger

internal class MarketapCoreService(
    private val eventIngestionService: EventIngestionService,
    private val userIngestionService: UserIngestionService,
    private val deviceManager: DeviceManager,
    private val inAppService: InAppService,
    private val marketapBackend: MarketapBackend,
    private val clientStateManager: ClientStateManager,
    private val sessionManager: SessionManager,
) {

    fun identify(userId: String, properties: Map<String, Any>?) {
        try {
            userIngestionService.identify(userId, properties ?: emptyMap())
        } catch (t: Throwable) {
            // Ignore
            logger.e(t) { "Failed to identify user: $userId" }
        }
    }

    fun resetIdentity() {
        try {
            userIngestionService.resetIdentity()
        } catch (t: Throwable) {
            // Ignore
            logger.e(t) { "Failed to reset identity" }
        }
    }

    fun track(name: String, properties: Map<String, Any>?) {
        try {
            eventIngestionService.trackEvent(name, properties ?: emptyMap())
        } catch (t: Throwable) {
            // Ignore
            logger.e(t) { "Failed to track event $name" }
        }
    }

    fun trackFromWebBridge(name: String, properties: Map<String, Any>?) {
        try {
            eventIngestionService.trackEvent(name, properties ?: emptyMap(), fromWebBridge = true)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to track event from web bridge: $name" }
        }
    }

    fun hideCampaign(campaignId: String, hideType: HideType) {
        try {
            inAppService.hideCampaign(campaignId, hideType)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to hide campaign: $campaignId" }
        }
    }

    fun setUserProperties(userProperties: Map<String, Any>) {
        try {
            userIngestionService.setUserProperties(userProperties)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to set user properties" }
        }
    }

    fun setDeviceOptIn(optIn: Boolean) {
        try {
            deviceManager.setDeviceOptIn(optIn)
            userIngestionService.pushDevice()
        } catch (t: Throwable) {
            logger.e(t) { "Failed to set device opt-in" }
        }
    }

    fun requestAuthorizationForPushNotifications(activity: Activity) {
        try {
            deviceManager.requestAuthorizationForPushNotifications(activity)
        } catch (t: Throwable) {
            // Ignore
            logger.e(t) { "Failed to request push notification authorization" }
        }
    }
}
