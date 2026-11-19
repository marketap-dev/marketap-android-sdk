package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass

/** 최초 노출 기준 [minutes]분 타이머. [revision] 이 바뀌면 시작 시각을 새로 기록한다. */
@JsonClass(generateAdapter = true)
internal data class InAppCampaignTimer(
    val minutes: Int,
    val revision: String,
)
