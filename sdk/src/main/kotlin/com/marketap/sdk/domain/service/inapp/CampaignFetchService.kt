package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.utils.getTypeToken


internal class CampaignFetchService(
    private val internalStorage: InternalStorage,
    private val inAppCampaignApi: MarketapBackend,
    private val clientStateManager: ClientStateManager,
    private val deviceManager: DeviceManager
) {

    private val CAMPAIGN_CACHE_KEY = "last_campaigns"
    private val CAMPAIGN_CACHED_AT = "campaign_cached_at"
    private val expirationTime: Long = 30 * 60 * 1000 // 30분 (테스트 기준)

    fun useCampaigns(block: (campaigns: List<InAppCampaign>) -> Unit) {
        fetchLocalCampaign()?.let { localCampaigns ->
            block(localCampaigns)
            return
        }

        val userId = clientStateManager.getUserId()
        val projectId = clientStateManager.getProjectId()
        val device = deviceManager.getDevice()

        var isResolved = false

        inAppCampaignApi.fetchCampaigns(FetchCampaignReq(projectId, userId, device.toReq()), {
            block(it.campaigns)
        })
        { campaigns ->
            internalStorage.setItem(CAMPAIGN_CACHE_KEY, campaigns)
            internalStorage.setItem(CAMPAIGN_CACHED_AT, System.currentTimeMillis())
        }
    }

    private fun fetchLocalCampaign(): List<InAppCampaign>? {
        val campaigns =
            internalStorage.getItem<InAppCampaignRes>(CAMPAIGN_CACHE_KEY, getTypeToken())
        val lastFetchedAt = internalStorage.getItem<Long>(CAMPAIGN_CACHED_AT, getTypeToken())

        if (campaigns == null || lastFetchedAt == null) {
            return null
        }

        val currentTime = System.currentTimeMillis()

        // 캐시된 데이터가 expirationTime(30분) 이상 경과했으면 무효화
        return if (lastFetchedAt + expirationTime < currentTime) null else campaigns.campaigns
    }
}