package com.marketap.sdk

import com.marketap.sdk.service.MarketapSDK

object Marketap {
    private var marketapSDK = lazy { MarketapSDK() }

    @JvmStatic
    fun getInstance(): MarketapSDK {
        return marketapSDK.value
    }

    val marketap: MarketapSDK
        get() = getInstance()
}