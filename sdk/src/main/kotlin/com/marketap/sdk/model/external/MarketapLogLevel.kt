package com.marketap.sdk.model.external

import com.marketap.sdk.presentation.MarketapRegistry

enum class MarketapLogLevel(val value: Int) {
    NONE(0),
    ERROR(1),
    WARN(2),
    INFO(3),
    DEBUG(4),
    VERBOSE(5);

    fun isEnabled() =
        this.value <= MarketapRegistry.logLevel.value
}