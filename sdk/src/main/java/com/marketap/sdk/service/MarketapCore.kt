package com.marketap.sdk.service


import com.marketap.sdk.service.inapp.InAppCallBack
import java.time.Instant

internal interface MarketapCore : InAppCallBack {
    fun track(
        name: String,
        properties: Map<String, Any>?,
        id: String?,
        timestamp: Instant?,
        then: (() -> Unit)? = null
    )

    fun identify(userId: String, properties: Map<String, Any>?, then: (() -> Unit)? = null)

    fun resetIdentity()
}