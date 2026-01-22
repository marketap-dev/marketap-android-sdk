package com.marketap.sdk.domain.service.event

import com.marketap.sdk.InAppEventBuilder
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import java.util.UUID

internal class EventIngestionService(
    private val marketapBackend: MarketapBackend,
    private val inAppService: InAppService,
    private val clientStateManager: ClientStateManager,
    private val sessionManager: SessionManager,
    private val deviceManager: DeviceManager,
    private val userIngestionService: UserIngestionService,
) {
    fun trackEvent(eventName: String, eventProperties: Map<String, Any>) {
        trackEvent(eventName, eventProperties, fromWebBridge = false)
    }

    fun trackEvent(eventName: String, eventProperties: Map<String, Any>, fromWebBridge: Boolean) {
        val userId = clientStateManager.getUserId()
        val projectId = clientStateManager.getProjectId()
        val device = deviceManager.getDevice().toReq()

        val sessionId = sessionManager.getSessionId { sessionId ->
            marketapBackend.track(
                projectId, IngestEventRequest(
                    generateRandomUUID(),
                    "mkt_session_start",
                    userId,
                    device,
                    mapOf("mkt_session_id" to sessionId),
                )
            )
        }


        val eventRequest = IngestEventRequest(
            generateRandomUUID(),
            eventName,
            userId,
            device,
            eventProperties + ("mkt_session_id" to sessionId),
        )

        marketapBackend.track(projectId, eventRequest)

        val messageId = generateRandomUUID()
        inAppService.onEvent(
            eventRequest,
            fromWebBridge,
            { campaign ->
                val props = InAppEventBuilder.impressionEventProperties(
                    campaignId = campaign.id,
                    messageId = messageId,
                    layoutSubType = campaign.layout.layoutSubType,
                    sessionId = sessionId
                )
                marketapBackend.track(
                    projectId, IngestEventRequest(
                        generateRandomUUID(),
                        "mkt_delivery_message",
                        userId,
                        device,
                        props,
                    )
                )
            },
            { campaign, locationId ->
                val props = InAppEventBuilder.clickEventProperties(
                    campaignId = campaign.id,
                    messageId = messageId,
                    locationId = locationId,
                    url = null,
                    layoutSubType = campaign.layout.layoutSubType,
                    sessionId = sessionId
                )
                marketapBackend.track(
                    projectId, IngestEventRequest(
                        generateRandomUUID(),
                        "mkt_click_message",
                        userId,
                        device,
                        props,
                    )
                )
            },
            { campaign, eventName, properties ->
                val baseProps = InAppEventBuilder.impressionEventProperties(
                    campaignId = campaign.id,
                    messageId = messageId,
                    layoutSubType = campaign.layout.layoutSubType,
                    sessionId = sessionId
                )
                val mergedProperties = (properties ?: emptyMap()) + baseProps
                trackEvent(eventName, mergedProperties)
            },
            { properties ->
                userIngestionService.setUserProperties(properties)
            },
        )

        sessionManager.updateActivity()
    }

    private fun generateRandomUUID(): String {
        return UUID.randomUUID().toString()
    }
}
