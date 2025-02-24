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
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun identify(userId: String, properties: Map<String, Any>?, then: (() -> Unit)?) {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun resetIdentity() {
        try {
            stateManager.setUserId(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun hideCampaign(campaignId: String, hideType: HideType) {
        try {
            campaignComponentHandler.hideCampaign(campaignId, hideType)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun click(campaignId: String, locationId: String, uri: Uri) {
        try {
            campaignComponentHandler.click(campaignId, locationId, uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}