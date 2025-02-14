package com.marketap.sdk

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import java.time.Instant


internal class WebViewMarketapSDK : MarketapSDK {
    private var config: MarketapConfig? = null
    private lateinit var marketapManager: MarketapManager

    override fun setDeviceId(gaid: String?, appSetId: String?) {
        marketapManager.setDeviceInfo(gaid = gaid, appSetId = appSetId)
        setDevice()
    }


    override fun initialize(config: MarketapConfig) {
        if (this.config != null) return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("MarketapSDK", "FCM token: ${task.result}")
                val token = task.result
                marketapManager.setDeviceInfo(token = token)
                setDevice()
            }
        }
        this.config = config
        this.marketapManager = MarketapManager(config.activity)
        marketapManager.useMarketapCore(
            "initialize", """
            {
                "projectId": "${config.projectId}",
                "debug": ${config.debug}
            }
        """.trimIndent()
        )
        setDevice()
        this.track("mkt_session_start")
        Log.d("MarketapSDK", "Successfully initialized!")
    }

    private fun setDevice() {
        val device = marketapManager.getDeviceInfo()
        marketapManager.useMarketapCore("setDevice", device.toString())
    }

    override fun login(
        userId: String,
        userProperties: Map<String, Any>?,
        eventProperties: Map<String, Any>?
    ) {
        marketapManager.useMarketapCore(
            "login",
            "'$userId'",
            userProperties,
            eventProperties
        )
    }

    override fun logout(properties: Map<String, Any>?) {
        marketapManager.useMarketapCore("logout", properties)
    }

    override fun track(
        name: String,
        properties: Map<String, Any>?,
        id: String?,
        timestamp: Instant?
    ) {
        marketapManager.useMarketapCore(
            "track",
            "'$name'",
            properties,
            id?.let { "'$it'" },
            timestamp?.let { "'$it'" }
        )
    }

    override fun trackPurchase(revenue: Double, properties: Map<String, Any>?) {
        marketapManager.useMarketapCore(
            "trackPurchase",
            "'$revenue'",
            properties
        )
    }

    override fun trackRevenue(name: String, revenue: Double, properties: Map<String, Any>?) {
        marketapManager.useMarketapCore(
            "trackRevenue",
            "'$name'",
            "'$revenue'",
            properties
        )
    }

    override fun trackPageView(properties: Map<String, Any>?) {
        marketapManager.useMarketapCore("trackPageView", properties)
    }

    override fun identify(userId: String, properties: Map<String, Any>?) {
        marketapManager.useMarketapCore("identify", "'$userId'", properties)
    }

    override fun resetIdentity() {
        marketapManager.useMarketapCore("resetIdentity")
    }
}