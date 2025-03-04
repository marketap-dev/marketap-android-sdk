package com.marketap.sdk.domain.service

import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.UserIngestionService

internal class MarketapCoreService(
    private val eventIngestionService: EventIngestionService,
    private val userIngestionService: UserIngestionService
) {

    fun identify(userId: String, properties: Map<String, Any>?) {
        userIngestionService.identify(userId, properties ?: emptyMap())
    }

    fun resetIdentity() {
        userIngestionService.resetIdentity()
    }

    fun track(name: String, properties: Map<String, Any>?) {
        eventIngestionService.trackEvent(name, properties ?: emptyMap())
    }
}