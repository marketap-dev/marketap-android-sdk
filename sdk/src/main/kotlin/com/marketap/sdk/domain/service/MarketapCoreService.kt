package com.marketap.sdk.domain.service

import android.app.Activity
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.utils.logger
import java.util.UUID

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
        } catch (e: Exception) {
            // Ignore
            logger.e(e) { "Failed to identify user: $userId" }
        }
    }

    fun resetIdentity() {
        try {
            userIngestionService.resetIdentity()
        } catch (e: Exception) {
            // Ignore
            logger.e(e) { "Failed to reset identity" }
        }
    }

    fun track(name: String, properties: Map<String, Any>?) {
        try {
            eventIngestionService.trackEvent(name, properties ?: emptyMap())
        } catch (e: Exception) {
            // Ignore
            logger.e(e) { "Failed to track event $name" }
        }
    }

    /**
     * 웹브릿지에서 온 이벤트 추적 (인앱 캠페인을 웹으로 전달)
     */
    fun trackFromWebBridge(name: String, properties: Map<String, Any>?) {
        try {
            eventIngestionService.trackEvent(name, properties ?: emptyMap(), fromWebBridge = true)
        } catch (e: Exception) {
            logger.e(e) { "Failed to track event from web bridge: $name" }
        }
    }

    /**
     * 인앱 메시지 노출 이벤트 전송 (웹브릿지에서 호출)
     */
    fun trackInAppImpression(campaign: InAppCampaign, messageId: String) {
        try {
            val userId = clientStateManager.getUserId()
            val projectId = clientStateManager.getProjectId()
            val device = deviceManager.getDevice().toReq()
            val sessionId = sessionManager.getSessionId { }

            marketapBackend.track(
                projectId, IngestEventRequest(
                    UUID.randomUUID().toString(),
                    "mkt_delivery_message",
                    userId,
                    device,
                    mapOf(
                        "mkt_campaign_id" to campaign.id,
                        "mkt_campaign_category" to "ON_SITE",
                        "mkt_channel_type" to "IN_APP_MESSAGE",
                        "mkt_sub_channel_type" to campaign.layout.layoutSubType,
                        "mkt_result_status" to 200000,
                        "mkt_result_message" to "SUCCESS",
                        "mkt_is_success" to true,
                        "mkt_message_id" to messageId,
                        "mkt_session_id" to sessionId
                    ),
                )
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to track in-app impression" }
        }
    }

    /**
     * 인앱 메시지 클릭 이벤트 전송 (웹브릿지에서 호출)
     */
    fun trackInAppClick(campaign: InAppCampaign, messageId: String, locationId: String) {
        try {
            val userId = clientStateManager.getUserId()
            val projectId = clientStateManager.getProjectId()
            val device = deviceManager.getDevice().toReq()
            val sessionId = sessionManager.getSessionId { }

            marketapBackend.track(
                projectId, IngestEventRequest(
                    UUID.randomUUID().toString(),
                    "mkt_click_message",
                    userId,
                    device,
                    mapOf(
                        "mkt_campaign_id" to campaign.id,
                        "mkt_campaign_category" to "ON_SITE",
                        "mkt_channel_type" to "IN_APP_MESSAGE",
                        "mkt_sub_channel_type" to campaign.layout.layoutSubType,
                        "mkt_result_status" to 200000,
                        "mkt_result_message" to "SUCCESS",
                        "mkt_is_success" to true,
                        "mkt_message_id" to messageId,
                        "mkt_location_id" to locationId,
                        "mkt_session_id" to sessionId
                    ),
                )
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to track in-app click" }
        }
    }

    /**
     * 캠페인 숨김 처리 (웹브릿지에서 호출)
     */
    fun hideCampaign(campaignId: String, hideType: HideType) {
        try {
            inAppService.hideCampaign(campaignId, hideType)
        } catch (e: Exception) {
            logger.e(e) { "Failed to hide campaign: $campaignId" }
        }
    }

    /**
     * 사용자 속성 설정 (웹브릿지에서 호출)
     */
    fun setUserProperties(userProperties: Map<String, Any>) {
        try {
            userIngestionService.setUserProperties(userProperties)
        } catch (e: Exception) {
            logger.e(e) { "Failed to set user properties" }
        }
    }

    fun requestAuthorizationForPushNotifications(activity: Activity) {
        try {
            deviceManager.requestAuthorizationForPushNotifications(activity)
        } catch (e: Exception) {
            // Ignore
            logger.e(e) { "Failed to request push notification authorization" }
        }
    }
}
