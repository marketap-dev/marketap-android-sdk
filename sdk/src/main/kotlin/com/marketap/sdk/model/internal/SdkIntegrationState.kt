package com.marketap.sdk.model.internal

import com.marketap.sdk.model.internal.api.SdkIntegrationStateReq
import com.marketap.sdk.utils.adapter

internal object SdkIntegrationState {
    @Volatile var handleInAppInWebView: Boolean? = null
    @Volatile var isClickHandlerCustomized: Boolean? = null
    @Volatile var isClickHandlerSet: Boolean? = null
    @Volatile var isWebSdkInitialized: Boolean? = null

    fun toJsonString(): String {
        return adapter<SdkIntegrationStateReq>().toJson(
            SdkIntegrationStateReq(handleInAppInWebView, isClickHandlerCustomized, isClickHandlerSet, isWebSdkInitialized)
        )
    }
}
