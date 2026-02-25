package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.FetchCampaignsReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.longAdapter
import com.marketap.sdk.utils.stringAdapter


internal class CampaignFetchService(
    private val internalStorage: InternalStorage,
    private val inAppCampaignApi: MarketapBackend,
    private val clientStateManager: ClientStateManager,
    private val deviceManager: DeviceManager
) {

    companion object {
        private const val CAMPAIGN_CACHE_KEY = "last_campaigns"
        private const val CAMPAIGN_CACHED_AT = "campaign_cached_at"
        private const val CAMPAIGN_CHECKSUM = "campaign_checksum"
        private const val EXPIRATION_TIME: Long = 5 * 60 * 1000 // 5 minutes
    }

    fun useCampaigns(block: (campaigns: List<InAppCampaign>) -> Unit) {
        val userId = clientStateManager.getUserId()
        fetchLocalCampaign(userId)?.let { localCampaigns ->
            logger.d { "Using cached campaigns for user $userId" }
            block(localCampaigns)
            return
        }
        val projectId = clientStateManager.getProjectId()
        val device = deviceManager.getDevice()
        val cachedChecksum = internalStorage.getItem<String>("$CAMPAIGN_CHECKSUM:$userId", stringAdapter)

        inAppCampaignApi.fetchCampaigns(FetchCampaignsReq(projectId, userId, device.toReq(), cachedChecksum), {
            logger.d { "Fetching campaigns from API for user $userId" }
            val campaigns = it.campaigns
                ?: internalStorage.getItem<InAppCampaignRes>("$CAMPAIGN_CACHE_KEY:$userId", adapter())?.campaigns
                ?: emptyList()
            block(campaigns)
        })
        { res ->
            logger.d { "Storing fetched campaigns for user $userId" }
            if (res.campaigns != null) {
                internalStorage.setItem("$CAMPAIGN_CACHE_KEY:$userId", res, adapter())
            }
            internalStorage.setItem("$CAMPAIGN_CHECKSUM:$userId", res.checksum, stringAdapter)
            internalStorage.setItem(
                "$CAMPAIGN_CACHED_AT:$userId",
                System.currentTimeMillis(),
                longAdapter
            )
        }
    }

    fun resolveCampaignHtml(
        campaign: InAppCampaign,
        event: IngestEventRequest
    ): InAppCampaign? {
        if (campaign.html != null) {
            return campaign
        }

        val projectId = clientStateManager.getProjectId()
        val userId = clientStateManager.getUserId()
        val device = deviceManager.getDevice().toReq()
        val request = FetchCampaignReq(
            projectId = projectId,
            userId = userId,
            device = device,
            eventName = event.name,
            eventProperties = event.properties
        )

        var resolved: InAppCampaign? = null
        inAppCampaignApi.fetchCampaign(campaign.id, request, {
            val fetched = it.campaign
            if (fetched?.html != null) {
                resolved = fetched
            }
        })
        {
            logger.d { "fetchCampaign completed for campaignId=${campaign.id}, hasHtml=${it.campaign?.html != null}" }
        }
        return resolved
    }

    private fun fetchLocalCampaign(userId: String?): List<InAppCampaign>? {
        try {
            val campaigns =
                internalStorage.getItem<InAppCampaignRes>("$CAMPAIGN_CACHE_KEY:$userId", adapter())
            val lastFetchedAt =
                internalStorage.getItem<Long>("$CAMPAIGN_CACHED_AT:$userId", longAdapter)

            if (campaigns == null || lastFetchedAt == null) {
                return null
            }

            val currentTime = System.currentTimeMillis()

            return if (lastFetchedAt + EXPIRATION_TIME < currentTime) null else campaigns.campaigns
        } catch (e: Exception) {
            return null
        }
    }
}