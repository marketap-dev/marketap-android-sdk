package com.marketap.sdk.model.internal.push

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AndroidPushButton(
    val name: String,
    val action: String,
    val url: String?,
    val deepLink: String?,
)