package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass

/** 여러 캠페인을 합쳐 [durationMinutes] 동안 [limit]번까지만 노출한다. [tagIds]가 비어 있으면 모든 캠페인에 적용된다. */
@JsonClass(generateAdapter = true)
data class GlobalFrequencyCapRule(
    val channelType: String? = null,
    val limit: Int,
    val durationMinutes: Int,
    val tagIds: List<String> = emptyList(),
)
