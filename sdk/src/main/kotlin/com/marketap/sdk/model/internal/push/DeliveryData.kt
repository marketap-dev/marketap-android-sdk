package com.marketap.sdk.model.internal.push

import java.io.Serializable

data class DeliveryData(
    val projectId: String,
    val userId: String?,
    // 콘솔 '테스트 발송'(targetMode=device)처럼 서버가 deviceId를 실어주지 않는 경로가 있다.
    // SDK는 자기 기기 정보를 직접 알고 있으므로 이 값이 없어도 이벤트 전송에 지장이 없다.
    val deviceId: String?,
    val campaignId: String,
    val messageId: String,
    val serverProperties: String?,
) : Serializable
