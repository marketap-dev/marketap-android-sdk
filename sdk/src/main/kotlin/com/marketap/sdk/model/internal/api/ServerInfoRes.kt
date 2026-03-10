package com.marketap.sdk.model.internal.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServerInfoRes(
    val serverTimeOffset: Long,
    val useWebClickRouting: Boolean
)
