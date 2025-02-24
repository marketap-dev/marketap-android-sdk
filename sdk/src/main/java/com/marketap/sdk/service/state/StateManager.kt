package com.marketap.sdk.service.state

import com.marketap.sdk.model.internal.MarketapState

internal interface StateManager {
    fun setUserId(userId: String?)
    fun getState(): MarketapState
}