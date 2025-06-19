package com.marketap.sdk.presentation

import com.marketap.sdk.model.external.MarketapClickHandler

internal object CustomHandlerStore {
    var clickHandler: MarketapClickHandler? = null
    fun useClickHandler(block: (MarketapClickHandler) -> Boolean): Boolean {
        val handler = clickHandler
        return if (handler != null) {
            block(handler)
        } else {
            false
        }
    }
}