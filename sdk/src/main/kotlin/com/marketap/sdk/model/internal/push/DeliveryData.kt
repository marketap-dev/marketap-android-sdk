package com.marketap.sdk.model.internal.push

import java.io.Serializable

data class DeliveryData(
    val projectId: String,
    val userId: String?,
    val deviceId: String,
    val campaignId: String,
    val messageId: String,
    val serverProperties: String?,
) : Serializable
