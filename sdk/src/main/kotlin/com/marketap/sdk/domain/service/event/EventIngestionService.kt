package com.marketap.sdk.domain.service.event

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.inapp.InAppEventIdentity
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.SdkIntegrationState
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.withSdkIntegrationState
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import java.util.UUID

internal class EventIngestionService(
    private val marketapBackend: MarketapBackend,
    private val inAppService: InAppService,
    private val clientStateManager: ClientStateManager,
    private val sessionManager: SessionManager,
    private val deviceManager: DeviceManager,
    private val userIngestionService: UserIngestionService,
    private val sessionUtmStore: SessionUtmStore? = null,
    private val clickIdStore: ClickIdStore? = null,
) {
    fun trackEvent(eventName: String, eventProperties: Map<String, Any>) {
        trackEvent(eventName, eventProperties, origin = null)
    }

    fun trackEvent(eventName: String, eventProperties: Map<String, Any>, origin: InAppOrigin?) {
        val generation = inAppService.currentGeneration()
        val userId = clientStateManager.getUserId()
        val projectId = clientStateManager.getProjectId()
        val device = deviceManager.getDevice().toReq()
        val sdkIntegrationState = SdkIntegrationState.toJsonString()

        val sessionId = currentSessionId(projectId, userId, device, sdkIntegrationState, eventProperties)
        val (properties, clickId) = clickIdStore?.attach(eventName, eventProperties) ?: (eventProperties to null)

        val eventRequest = IngestEventRequest(
            generateRandomUUID(),
            eventName,
            userId,
            device,
            withSessionUtm(properties + ("mkt_session_id" to sessionId), sessionId)
                .withSdkIntegrationState(sdkIntegrationState),
        )

        if (clickId == null) {
            marketapBackend.track(projectId, eventRequest)
        } else {
            marketapBackend.track(projectId, eventRequest) { clickIdStore?.consume(clickId) }
        }

        inAppService.onEvent(eventRequest, origin, generation)

        // 실제 사용자 이벤트 시 device liveness 갱신(변경 OR 24h). 위에서 만든 device 재사용(getDevice 중복 호출 방지).
        // session_start/in-app delivery·click 은 marketapBackend.track 직접 호출이라 여길 안 거침 → liveness 미발동.
        userIngestionService.pushDevice(DevicePushPolicy.LIVENESS, device)

        sessionManager.updateActivity()
    }

    /** 인앱 메시지 안에서 보낸 이벤트를 그 메시지를 띄운 시점의 [identity]로 보낸다. 지금 사용자와 다르면 인앱 판정에 쓰지 않는다. */
    fun trackInAppEvent(eventName: String, eventProperties: Map<String, Any>, identity: InAppEventIdentity) {
        val generation = inAppService.currentGeneration()
        val eventRequest = IngestEventRequest(
            generateRandomUUID(),
            eventName,
            identity.userId,
            identity.device,
            withSessionUtm(eventProperties + ("mkt_session_id" to identity.sessionId), identity.sessionId)
                .withSdkIntegrationState(identity.sdkIntegrationState),
        )

        marketapBackend.track(identity.projectId, eventRequest)

        if (identity.userId == clientStateManager.getUserId()) {
            inAppService.onEvent(eventRequest, origin = null, generation)
        }
        userIngestionService.pushDevice(DevicePushPolicy.LIVENESS, deviceManager.getDevice().toReq())
        sessionManager.updateActivity()
    }

    /** 현재 세션 ID. 세션이 만료돼 새로 시작되면 `mkt_session_start`를 보낸다. */
    fun currentSessionId(): String = currentSessionId(
        clientStateManager.getProjectId(),
        clientStateManager.getUserId(),
        deviceManager.getDevice().toReq(),
        SdkIntegrationState.toJsonString(),
        emptyMap(),
    )

    fun setPendingUtm(utm: Map<String, String>) {
        sessionUtmStore?.setPending(utm)
    }

    fun setPendingClickId(clickId: String, receivedAt: Long) {
        clickIdStore?.setPending(clickId, receivedAt)
    }

    fun withSessionUtm(properties: Map<String, Any>, sessionId: String): Map<String, Any> =
        sessionUtmStore?.apply(properties, sessionId) ?: properties

    private fun currentSessionId(
        projectId: String,
        userId: String?,
        device: DeviceReq,
        sdkIntegrationState: String,
        eventProperties: Map<String, Any>,
    ): String {
        // 세션을 여는 이벤트의 UTM은 세션 시작 이벤트에도 세션 UTM으로 실어야 세션 단위 조건에 잡힌다.
        val openingSessionUtm = sessionUtmStore?.openingSessionUtm(eventProperties).orEmpty()
        return sessionManager.getSessionId { sessionId ->
            marketapBackend.track(
                projectId, IngestEventRequest(
                    generateRandomUUID(),
                    "mkt_session_start",
                    userId,
                    device,
                    (openingSessionUtm + ("mkt_session_id" to sessionId))
                        .withSdkIntegrationState(sdkIntegrationState),
                )
            )
        }
    }

    private fun generateRandomUUID(): String {
        return UUID.randomUUID().toString()
    }
}
