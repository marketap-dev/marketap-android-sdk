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

            // deviceId는 의도적으로 조건에서 제외한다. 콘솔 테스트 발송(targetMode=device)은
            // deviceId를 내려주지 않는데, 예전에는 그 탓에 deliveryData가 null이 되어
            // 노출/클릭 이벤트가 통째로 유실됐다. deviceId는 SDK가 로컬에서 채울 수 있다.
            val deliveryData =
                if (projectId != null && campaignId != null && messageId != null) {
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