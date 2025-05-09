package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.longAdapter


internal class CampaignFetchService(
    private val internalStorage: InternalStorage,
    private val inAppCampaignApi: MarketapBackend,
    private val clientStateManager: ClientStateManager,
    private val deviceManager: DeviceManager
) {

    private val CAMPAIGN_CACHE_KEY = "last_campaigns"
    private val CAMPAIGN_CACHED_AT = "campaign_cached_at"
    private val expirationTime: Long = 5 * 60 * 1000 // 5 minutes

    fun useCampaigns(block: (campaigns: List<InAppCampaign>) -> Unit) {
        val userId = clientStateManager.getUserId()
        fetchLocalCampaign(userId)?.let { localCampaigns ->
            block(localCampaigns)
            return
        }
        val projectId = clientStateManager.getProjectId()
        val device = deviceManager.getDevice()

        inAppCampaignApi.fetchCampaigns(FetchCampaignReq(projectId, userId, device.toReq()), {
            block(it.campaigns)
        })
        { campaigns ->
            internalStorage.setItem("$CAMPAIGN_CACHE_KEY:$userId", campaigns, adapter())
            internalStorage.setItem(
                "$CAMPAIGN_CACHED_AT:$userId",
                System.currentTimeMillis(),
                longAdapter
            )
        }
    }

    private fun fetchLocalCampaign(userId: String?): List<InAppCampaign>? {
        val campaigns =
            internalStorage.getItem<InAppCampaignRes>("$CAMPAIGN_CACHE_KEY:$userId", adapter())
        val lastFetchedAt =
            internalStorage.getItem<Long>("$CAMPAIGN_CACHED_AT:$userId", longAdapter)

        if (campaigns == null || lastFetchedAt == null) {
            return null
        }

        val currentTime = System.currentTimeMillis()

        return if (lastFetchedAt + expirationTime < currentTime) null else campaigns.campaigns
    }
}