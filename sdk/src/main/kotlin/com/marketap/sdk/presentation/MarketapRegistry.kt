package com.marketap.sdk.presentation

import android.app.Application
import com.marketap.sdk.domain.service.MarketapCoreService
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.external.MarketapClickHandler
import com.marketap.sdk.model.external.MarketapLogLevel

internal object MarketapRegistry {
    var marketapCore: MarketapCoreService? = null
    var config: MarketapConfig? = null
    var application: Application? = null
    var marketapClickHandler: MarketapClickHandler? = null
    var logLevel: MarketapLogLevel = MarketapLogLevel.NONE
}