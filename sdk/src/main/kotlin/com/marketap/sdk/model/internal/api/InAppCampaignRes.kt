package com.marketap.sdk.model.internal.api

import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.inapp.GlobalFrequencyCapRule
import com.marketap.sdk.utils.NullIfAnyUndecodable
import com.marketap.sdk.utils.SkipUndecodable
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
internal data class InAppCampaignRes(
    val checksum: String,
    @SkipUndecodable val campaigns: List<InAppCampaign>?,
    @NullIfAnyUndecodable val globalFrequencyCaps: List<GlobalFrequencyCapRule>? = null,
)

@JsonClass(generateAdapter = true)
internal data class InAppCampaignSingleRes(
    val campaign: InAppCampaign?
)
