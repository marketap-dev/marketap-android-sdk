package com.marketap.sdk.presentation

import android.app.Application
import com.marketap.sdk.domain.service.MarketapCoreService
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.external.MarketapClickHandler
import com.marketap.sdk.model.external.MarketapLogLevel
import com.marketap.sdk.utils.logger

internal object MarketapRegistry {
    var marketapCore: MarketapCoreService? = null
        get() {
            if (field == null) {
                logger.w {
                    "Marketap SDK is not initialized. Please call Marketap.initialize() in your Application class."
                }
            }
            return field
        }
    var isInitialized: Boolean = false
    var config: MarketapConfig? = null
    @Volatile var clientKey: String? = null
    var application: Application? = null
    var marketapClickHandler: MarketapClickHandler? = null
    var logLevel: MarketapLogLevel = MarketapLogLevel.INFO

    /** 초기화 전에 받은 setUtm 값. 초기화 때 첫 이벤트보다 먼저 넘긴다. */
    @Volatile var utmBeforeInit: Map<String, String>? = null

    @Volatile var clickIdBeforeInit: Pair<String, Long>? = null
}