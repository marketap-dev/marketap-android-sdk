package com.marketap.sdk.model.internal.push

data class AndroidPushButton(
    val name: String,
    val action: String,
    val url: String?,
    val deepLink: String?,
)