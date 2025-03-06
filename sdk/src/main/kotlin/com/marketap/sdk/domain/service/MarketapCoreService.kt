package com.marketap.sdk.domain.service

import android.util.Log
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.UserIngestionService

internal class MarketapCoreService(
    private val eventIngestionService: EventIngestionService,
    private val userIngestionService: UserIngestionService
) {

    fun identify(userId: String, properties: Map<String, Any>?) {
        try {
            userIngestionService.identify(userId, properties ?: emptyMap())
        } catch (e: Exception) {
            // Ignore
            Log.e("MarketapSDK", "Failed to identify user: ${e.message}")
        }
    }

    fun resetIdentity() {
        try {
            userIngestionService.resetIdentity()
        } catch (e: Exception) {
            // Ignore
            Log.e("MarketapSDK", "Failed to reset identity: ${e.message}")
        }
    }

    fun track(name: String, properties: Map<String, Any>?) {
        try {
            eventIngestionService.trackEvent(name, properties ?: emptyMap())
        } catch (e: Exception) {
            // Ignore
            Log.e("MarketapSDK", "Failed to track event: ${e.message}")
        }
    }
}