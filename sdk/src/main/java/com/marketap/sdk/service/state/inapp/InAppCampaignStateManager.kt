package com.marketap.sdk.service.state.inapp

import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.FetchCampaignReq

internal interface InAppCampaignStateManager {
    fun fetchInAppCampaigns(fetchCampaignReq: FetchCampaignReq)

    fun getInAppCampaigns(
        fetchCampaignReq: FetchCampaignReq,
        block: (List<InAppCampaign>) -> Unit
    )

    fun hideCampaign(campaignId: String, until: Long)
    fun isCampaignHidden(campaignId: String): Boolean
    fun impression(campaignId: String)
    fun getImpressionCount(campaignId: String, from: Long): Long
}