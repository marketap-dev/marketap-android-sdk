package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.utils.longAdapter

internal class CampaignExposureService(
    private val internalStorage: InternalStorage,
) {
    fun hideCampaign(campaignId: String, until: Long) {
        internalStorage.setItem("hide_campaign_${campaignId}", until, longAdapter)
    }

    fun isCampaignHidden(campaignId: String): Boolean {
        val hiddenUntil =
            internalStorage.getItem("hide_campaign_${campaignId}", longAdapter)
        return hiddenUntil != null && hiddenUntil > System.currentTimeMillis()
    }

    fun recordImpression(campaignId: String) {
        internalStorage.queueItem(
            "impressions_${campaignId}",
            System.currentTimeMillis(),
            longAdapter
        )
    }

    fun hasReachedImpressionLimit(
        campaignId: String,
        windowMinutes: Int,
        maxCount: Int
    ): Boolean {
        val impressions =
            internalStorage.getItemList("impressions_${campaignId}", longAdapter)

        val now = System.currentTimeMillis()
        val windowStart = now - windowMinutes * 60 * 1000
        return impressions.count { it > windowStart } >= maxCount
    }
}