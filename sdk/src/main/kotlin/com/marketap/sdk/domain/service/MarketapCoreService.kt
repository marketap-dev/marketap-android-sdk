package com.marketap.sdk.domain.service

import android.app.Activity
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.event.DevicePushPolicy
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.filterSupportedProperties
import com.marketap.sdk.utils.findUnsupportedPropertyName

internal class MarketapCoreService(
    private val eventIngestionService: EventIngestionService,
    private val userIngestionService: UserIngestionService,
    private val deviceManager: DeviceManager,
    private val inAppService: InAppService,
    private val marketapBackend: MarketapBackend,
    private val clientStateManager: ClientStateManager,
    private val sessionManager: SessionManager,
) {

    /** 보낼 수 없는 값이 하나라도 있으면 이벤트를 보내지 않는다. */
    private fun hasSupportedEventProperties(name: String, properties: Map<String, Any>?): Boolean {
        val unsupported = properties.findUnsupportedPropertyName() ?: return true
        logger.e {
            "Event \"$name\" is not sent: property \"$unsupported\" has an unsupported value " +
                    "(${properties?.get(unsupported)?.javaClass?.name})"
        }
        return false
    }

    /** 유저 속성은 보낼 수 없는 값을 가진 속성만 빼고 나머지를 보낸다. */
    private fun supportedUserProperties(properties: Map<String, Any>?): Map<String, Any> {
        return properties.filterSupportedProperties { name ->
            logger.e {
                "User property \"$name\" is not sent: unsupported value (${properties?.get(name)?.javaClass?.name})"
            }
        }
    }

    fun identify(userId: String, properties: Map<String, Any>?) {
        try {
            val supported = supportedUserProperties(properties)
            val identify = { userIngestionService.identify(userId, supported) }
            inAppService.switchIdentity(userId, clientStateManager::getUserId, identify)
        } catch (t: Throwable) {
            // Ignore
            logger.e(t) { "Failed to identify user: $userId" }
        }
    }

    fun resetIdentity() {
        try {
            inAppService.switchIdentity(null, clientStateManager::getUserId, userIngestionService::resetIdentity)
        } catch (t: Throwable) {
            // Ignore
            logger.e(t) { "Failed to reset identity" }
        }
    }

    fun track(name: String, properties: Map<String, Any>?) {
        try {
            if (!hasSupportedEventProperties(name, properties)) return
            eventIngestionService.trackEvent(name, properties ?: emptyMap())
        } catch (t: Throwable) {
            // Ignore
            logger.e(t) { "Failed to track event $name" }
        }
    }

    fun setUtm(utm: Map<String, String>) {
        try {
            eventIngestionService.setPendingUtm(utm)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to set utm" }
        }
    }

    fun trackFromWebBridge(name: String, properties: Map<String, Any>?, origin: InAppOrigin?) {
        try {
            if (!hasSupportedEventProperties(name, properties)) return
            eventIngestionService.trackEvent(name, properties ?: emptyMap(), origin)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to track event from web bridge: $name" }
        }
    }

    fun screenChanged() {
        try {
            inAppService.screenChanged()
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle screen change" }
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
            userIngestionService.setUserProperties(supportedUserProperties(userProperties))
        } catch (t: Throwable) {
            logger.e(t) { "Failed to set user properties" }
        }
    }

    fun setDeviceOptIn(optIn: Boolean?) {
        try {
            deviceManager.setDeviceOptIn(optIn)
            userIngestionService.pushDevice(DevicePushPolicy.CHANGE_ONLY)
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
