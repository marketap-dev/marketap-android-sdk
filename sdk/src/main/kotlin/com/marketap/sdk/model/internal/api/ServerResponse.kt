package com.marketap.sdk.model.internal.api

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class ServerResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)