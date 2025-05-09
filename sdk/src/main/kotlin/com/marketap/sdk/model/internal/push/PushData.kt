package com.marketap.sdk.model.internal.push

import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.listAdapter
import java.nio.charset.StandardCharsets


data class PushData(
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val buttons: List<AndroidPushButton>? = null,
    val notificationId: Int,
    val deliveryData: DeliveryData? = null,
    val deepLink: String? = null
) {
    companion object {
        private fun stringToLong(input: String): Long {
            val bytes = input.toByteArray(StandardCharsets.UTF_8)

            var hash = 1125899906842597L // FNV-1a 64bit 초기값 (큰 소수)
            for (b in bytes) {
                hash = (hash * 31) xor (b.toInt() and 0xff).toLong()
            }
            return hash
        }


        fun fromMap(data: Map<String, String>): PushData? {
            val title = data["title"] ?: return null
            val body = data["message"] ?: return null
            val imageUrl = data["imageUrl"]
            val buttons =
                data["buttons"]?.deserialize((listAdapter<AndroidPushButton>()))
                    ?: emptyList()
            val campaignId = data["campaignId"]
            val messageId = data["messageId"]
            val notificationId = data["notificationId"]?.toIntOrNull()
                ?: messageId?.let { stringToLong(it) }?.toInt()
                ?: System.currentTimeMillis().toInt()
            val projectId = data["projectId"]
            val deviceId = data["deviceId"]
            val userId = data["userId"]
            val deepLink = data["deepLink"]
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