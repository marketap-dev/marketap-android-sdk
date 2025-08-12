package com.marketap.sdk.model.internal.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServerTimeOffsetRes(
    val serverTimeOffset: Long
)
