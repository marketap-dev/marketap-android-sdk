package com.marketap.sdk.service.state.inapp

import com.marketap.sdk.api.MarketapBackend
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.service.state.InternalStorage
import com.marketap.sdk.utils.getTypeToken

internal class InAppCampaignStateManagerImpl(
    private val internalStorage: InternalStorage,
    private val marketapBackend: MarketapBackend
) : InAppCampaignStateManager {
    override fun fetchInAppCampaigns(fetchCampaignReq: FetchCampaignReq) {
        marketapBackend.fetchCampaigns(fetchCampaignReq) {
            internalStorage.setItem("inAppCampaigns", it.campaigns)
            internalStorage.setItem("inAppCampaignsFetchedAt", System.currentTimeMillis())
        }
    }

    override fun getInAppCampaigns(
        fetchCampaignReq: FetchCampaignReq,
        block: (List<InAppCampaign>) -> Unit
    ) {
        val fetchedAt = internalStorage.getItem("inAppCampaignsFetchedAt", getTypeToken<Long>())
        if (fetchedAt == null || fetchedAt < System.currentTimeMillis() - 1000 * 60 * 5) {
            marketapBackend.fetchCampaigns(fetchCampaignReq, {
                block(it.campaigns)
            }) {
                internalStorage.setItem("inAppCampaigns", it.campaigns)
                internalStorage.setItem("inAppCampaignsFetchedAt", System.currentTimeMillis())
            }
        } else {
            block(internalStorage.getItem("inAppCampaigns", getTypeToken()) ?: emptyList())
        }
    }


    override fun hideCampaign(campaignId: String, until: Long) {
        internalStorage.setItem("hide_campaign_${campaignId}", until)
    }

    override fun isCampaignHidden(campaignId: String): Boolean {
        val hiddenUntil =
            internalStorage.getItem("hide_campaign_${campaignId}", getTypeToken<Long>())
        return hiddenUntil != null && hiddenUntil > System.currentTimeMillis()
    }

    override fun impression(campaignId: String) {
        internalStorage.queueItem(
            "impressions_${campaignId}",
            System.currentTimeMillis()
        )
    }

    override fun getImpressionCount(campaignId: String, from: Long): Long {
        val impressions =
            internalStorage.getItemList("impressions_${campaignId}", getTypeToken<Int>())
        return impressions.count { it >= from }.toLong()
    }
}