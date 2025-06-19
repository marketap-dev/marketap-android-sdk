package com.marketap.sdk.model.external

fun interface MarketapClickHandler {
    fun handleClick(click: MarketapClickEvent)
}