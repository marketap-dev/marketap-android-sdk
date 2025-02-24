package com.marketap.sdk.service

import android.net.Uri
import com.marketap.sdk.api.MarketapBackend
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.service.inapp.CampaignComponentHandler
import com.marketap.sdk.service.inapp.comparison.types.util.getNow
import com.marketap.sdk.service.ingestion.EventService
import com.marketap.sdk.service.state.StateManager
import java.time.Instant

internal class AndroidMarketapCore(
    private val stateManager: StateManager,
    private val eventService: EventService,
    private val marketapBackend: MarketapBackend,
    private val campaignComponentHandler: CampaignComponentHandler
) : MarketapCore {
    override fun track(
        name: String,
        properties: Map<String, Any>?,
        id: String?,
        timestamp: Instant?,
        then: (() -> Unit)?
    ) {
        val state = stateManager.getState()
        eventService.ingestEvent(
            state.projectId,
            IngestEventRequest(
                id = id,
                name = name,
                properties = properties,
                timestamp = timestamp?.toString() ?: getNow(),
                device = state.device.toReq(),
                userId = state.userId
            )
        )
        then?.invoke()
    }

    override fun identify(userId: String, properties: Map<String, Any>?, then: (() -> Unit)?) {
        stateManager.setUserId(userId)
        val state = stateManager.getState()
        marketapBackend.updateProfile(
            state.projectId,
            UpdateProfileRequest(
                userId = userId,
                properties = properties?.toMap() ?: emptyMap(),
                device = state.device.toReq()
            )
        )
        then?.invoke()
    }

    override fun resetIdentity() {
        stateManager.setUserId(null)
    }

    override fun hideCampaign(campaignId: String, hideType: HideType) {
        campaignComponentHandler.hideCampaign(campaignId, hideType)
    }

    override fun click(campaignId: String, locationId: String, uri: Uri) {
        campaignComponentHandler.click(campaignId, locationId, uri)
    }
}