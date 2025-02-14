package com.marketap.sdk

import java.time.Instant

interface MarketapSDK {
    fun setDeviceId(gaid: String? = null, appSetId: String? = null)

    fun initialize(config: MarketapConfig)
    fun login(
        userId: String,
        userProperties: Map<String, Any>? = null,
        eventProperties: Map<String, Any>? = null
    )

    fun logout(properties: Map<String, Any>? = null)
    fun track(
        name: String,
        properties: Map<String, Any>? = null,
        id: String? = null,
        timestamp: Instant? = null
    )

    fun trackPurchase(revenue: Double, properties: Map<String, Any>? = null)
    fun trackRevenue(name: String, revenue: Double, properties: Map<String, Any>? = null)
    fun trackPageView(properties: Map<String, Any>? = null)
    fun identify(userId: String, properties: Map<String, Any>? = null)
    fun resetIdentity()
}