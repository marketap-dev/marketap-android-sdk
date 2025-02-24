package com.marketap.sdk.service.ingestion

import com.marketap.sdk.api.MarketapBackend
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.service.inapp.comparison.types.util.getNow

internal class MarketapEventService(
    private val marketapBackend: MarketapBackend
) : EventService {
    override fun ingestEvent(projectId: String, eventRequest: IngestEventRequest) {
        val request = if (eventRequest.timestamp == null) {
            eventRequest.copy(timestamp = getNow())
        } else {
            eventRequest
        }

        marketapBackend.track(projectId, request)
    }
}