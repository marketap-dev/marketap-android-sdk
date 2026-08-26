package com.marketap.sdk.model.internal.push

import java.io.Serializable

data class DeliveryData(
    val projectId: String,
    val userId: String?,
    // 서버가 이 값을 실어주지 않아도 이벤트를 보낼 수 있도록 nullable.
    // SDK가 자기 기기 정보를 직접 알고 있어(PushTracker.resolveDevice) 폴백으로만 쓰인다.
    val deviceId: String?,
    val campaignId: String,
    val messageId: String,
    val serverProperties: String?,
) : Serializable
