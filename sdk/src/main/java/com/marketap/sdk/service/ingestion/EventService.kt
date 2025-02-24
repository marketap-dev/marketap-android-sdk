package com.marketap.sdk.service.ingestion

import com.marketap.sdk.model.internal.api.IngestEventRequest

internal interface EventService {
    fun ingestEvent(projectId: String, eventRequest: IngestEventRequest)
}