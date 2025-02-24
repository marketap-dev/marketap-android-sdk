package com.marketap.sdk.service.inapp

import com.marketap.sdk.model.internal.InAppCampaign

internal interface CampaignComponentHandler : InAppCallBack {
    fun showCampaign(
        campaign: InAppCampaign,
        onImpression: () -> Unit,
        onClick: (locationId: String) -> Unit
    )
}