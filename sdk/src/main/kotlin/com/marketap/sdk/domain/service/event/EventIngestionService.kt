package com.marketap.sdk.domain.service.event

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.inapp.InAppService

import com.marketap.sdk.utils.getNow
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
) {
    fun trackEvent(eventName: String, eventProperties: Map<String, Any>) {
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
                    getNow()
                )
            )
        }


        val eventRequest = IngestEventRequest(
            generateRandomUUID(),
            eventName,
            userId,
            device,
            eventProperties + ("mkt_session_id" to sessionId),
            getNow()
        )

        marketapBackend.track(projectId, eventRequest)

        val messageId = generateRandomUUID()
        inAppService.onEvent(eventRequest,
            { campaign ->
                marketapBackend.track(
                    projectId, IngestEventRequest(
                        generateRandomUUID(),
                        "mkt_delivery_message",
                        userId,
                        device,
                        mapOf(
                            "mkt_campaign_id" to campaign.id,
                            "mkt_campaign_category" to "ON_SITE",
                            "mkt_channel_type" to "IN_APP_MESSAGE",
                            "mkt_sub_channel_type" to campaign.layout.layoutSubType,
                            "mkt_result_status" to 200,
                            "mkt_result_message" to "SUCCESS",
                            "mkt_is_success" to true,
                            "mkt_message_id" to messageId,
                            "mkt_session_id" to sessionId
                        ),
                        timestamp = getNow()
                    )
                )
            },
            { campaign, locationId ->
                marketapBackend.track(
                    projectId, IngestEventRequest(
                        generateRandomUUID(),
                        "mkt_click_message",
                        userId,
                        device,
                        mapOf(
                            "mkt_campaign_id" to campaign.id,
                            "mkt_campaign_category" to "ON_SITE",
                            "mkt_channel_type" to "IN_APP_MESSAGE",
                            "mkt_sub_channel_type" to campaign.layout.layoutSubType,
                            "mkt_result_status" to 200,
                            "mkt_result_message" to "SUCCESS",
                            "mkt_is_success" to true,
                            "mkt_message_id" to messageId,
                            "mkt_location_id" to locationId,
                            "mkt_session_id" to sessionId
                        ),
                        getNow()
                    )
                )
            }
        )

        sessionManager.updateActivity()
    }

    private fun generateRandomUUID(): String {
        return UUID.randomUUID().toString()
    }
}