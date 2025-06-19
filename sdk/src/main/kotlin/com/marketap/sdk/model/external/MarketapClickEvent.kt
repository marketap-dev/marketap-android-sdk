package com.marketap.sdk.model.external

data class MarketapClickEvent(
    val campaignType: MarketapCampaignType,
    val campaignId: String,
    val url: String?
)