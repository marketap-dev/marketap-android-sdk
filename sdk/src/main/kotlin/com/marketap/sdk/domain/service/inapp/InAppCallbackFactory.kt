package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.InAppEventBuilder
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.SdkIntegrationState
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.withSdkIntegrationState
import java.util.UUID

/** 인앱 메시지 한 번의 노출에 묶이는 기록 콜백. */
internal interface InAppCallbacks {
    fun onImpression(campaign: InAppCampaign)
    fun onClick(campaign: InAppCampaign, locationId: String)
    fun onTrack(campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?)
    fun onSetUserProperties(properties: Map<String, Any>)
}

/** 인앱 메시지를 띄운 시점의 프로젝트·사용자·기기·세션. */
internal class InAppEventIdentity(
    val projectId: String,
    val userId: String?,
    val device: DeviceReq,
    val sessionId: String,
    val sdkIntegrationState: String,
)

/** 띄우는 시점의 사용자·세션·기기와 새 messageId로 콜백을 만든다. */
internal class InAppCallbackFactory(
    private val backend: MarketapBackend,
    private val projectId: () -> String,
    private val userId: () -> String?,
    private val device: () -> DeviceReq,
    private val sessionId: () -> String,
    private val trackEvent: (eventName: String, properties: Map<String, Any>, identity: InAppEventIdentity) -> Unit,
    private val setUserProperties: (properties: Map<String, Any>) -> Unit,
    private val withSessionUtm: (properties: Map<String, Any>, sessionId: String) -> Map<String, Any> = { properties, _ -> properties },
    private val sdkIntegrationState: () -> String = SdkIntegrationState::toJsonString,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun create(): InAppCallbacks {
        val projectId = projectId()
        val userId = userId()
        val device = device()
        val sessionId = sessionId()
        val sdkIntegrationState = sdkIntegrationState()
        val messageId = newId()
        val identity = InAppEventIdentity(projectId, userId, device, sessionId, sdkIntegrationState)

        fun send(name: String, properties: Map<String, Any>) {
            backend.track(
                projectId,
                IngestEventRequest(
                    newId(), name, userId, device,
                    withSessionUtm(properties, sessionId).withSdkIntegrationState(sdkIntegrationState),
                )
            )
        }

        return object : InAppCallbacks {
            override fun onImpression(campaign: InAppCampaign) {
                send(
                    "mkt_delivery_message",
                    InAppEventBuilder.impressionEventProperties(
                        campaignId = campaign.id,
                        messageId = messageId,
                        layoutSubType = campaign.layout.layoutSubType,
                        sessionId = sessionId
                    )
                )
            }

            override fun onClick(campaign: InAppCampaign, locationId: String) {
                send(
                    "mkt_click_message",
                    InAppEventBuilder.clickEventProperties(
                        campaignId = campaign.id,
                        messageId = messageId,
                        locationId = locationId,
                        url = null,
                        layoutSubType = campaign.layout.layoutSubType,
                        sessionId = sessionId
                    )
                )
            }

            override fun onTrack(campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) {
                val baseProps = InAppEventBuilder.impressionEventProperties(
                    campaignId = campaign.id,
                    messageId = messageId,
                    layoutSubType = campaign.layout.layoutSubType,
                    sessionId = sessionId
                )
                trackEvent(eventName, (properties ?: emptyMap()) + baseProps, identity)
            }

            override fun onSetUserProperties(properties: Map<String, Any>) = setUserProperties(properties)
        }
    }
}

internal object NoopInAppCallbacks : InAppCallbacks {
    override fun onImpression(campaign: InAppCampaign) {}
    override fun onClick(campaign: InAppCampaign, locationId: String) {}
    override fun onTrack(campaign: InAppCampaign, eventName: String, properties: Map<String, Any>?) {}
    override fun onSetUserProperties(properties: Map<String, Any>) {}
}
