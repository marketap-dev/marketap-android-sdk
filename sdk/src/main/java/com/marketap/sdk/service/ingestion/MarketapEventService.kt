package com.marketap.sdk.service.ingestion

import com.marketap.sdk.api.MarketapBackend
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.service.inapp.comparison.types.util.getNow
import java.util.UUID

internal class MarketapEventService(
    private val marketapBackend: MarketapBackend
) : EventService {
    private var sessionId: String = UUID.randomUUID().toString()
    private var lastEventTimestamp: Long = 0


    override fun ingestEvent(projectId: String, eventRequest: IngestEventRequest) {
        if (System.currentTimeMillis() - lastEventTimestamp > 30 * 60 * 1000) {
            sessionId = UUID.randomUUID().toString()
            marketapBackend.track(
                projectId, eventRequest.copy(
                    name = "mkt_session_start",
                    properties = mapOf("mkt_session_id" to sessionId),
                    timestamp = getNow()
                )
            )
        }


        val request = if (eventRequest.timestamp == null) {
            eventRequest.copy(timestamp = getNow())
        } else {
            eventRequest
        }.copy(
            properties = eventRequest.properties?.plus("mkt_session_id" to sessionId)
        )

        marketapBackend.track(projectId, request)
    }
}