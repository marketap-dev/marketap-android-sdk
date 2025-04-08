package com.marketap.sdk.model.internal.push

import com.marketap.sdk.utils.deserialize

data class PushData(
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val buttons: List<AndroidPushButton>? = null,
    val notificationId: Int = System.currentTimeMillis().toInt(),
    val deliveryData: DeliveryData? = null,
    val deepLink: String? = null
) {
    companion object {
        fun fromMap(data: Map<String, String>): PushData? {
            val title = data["title"] ?: return null
            val body = data["message"] ?: return null
            val imageUrl = data["imageUrl"]
            val buttons =
                data["buttons"]?.deserialize<List<AndroidPushButton>>()
            val campaignId = data["campaignId"]
            val notificationId = System.currentTimeMillis().toInt()
            val projectId = data["projectId"]
            val deviceId = data["deviceId"]
            val userId = data["userId"]
            val deepLink = data["deepLink"]
            val messageId = data["messageId"]
            val serverProperties = data["serverProperties"]

            val deliveryData =
                if (projectId != null && deviceId != null && campaignId != null && messageId != null) {
                    DeliveryData(
                        projectId,
                        userId,
                        deviceId,
                        campaignId,
                        messageId,
                        serverProperties
                    )
                } else {
                    null
                }

            return PushData(
                title,
                body,
                imageUrl,
                buttons,
                notificationId,
                deliveryData,
                deepLink
            )
        }
    }
}