package com.marketap.sdk.model.internal


internal data class Screen(
    val width: Int,
    val height: Int,
    val colorDepth: Int? = null,  // 색상 깊이 [Web 전용]
    val pixelRatio: Float? = null // 디바이스 픽셀 비율 (예: 3.0 for iPhone X)
)