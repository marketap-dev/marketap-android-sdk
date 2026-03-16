package com.marketap.sdk.model.internal.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class SdkIntegrationStateReq(
    @Json(name = "handle_in_app_in_web_view")
    val handleInAppInWebView: Boolean? = null,

    @Json(name = "is_click_handler_customized")
    val isClickHandlerCustomized: Boolean? = null,

    @Json(name = "is_click_handler_set")
    val isClickHandlerSet: Boolean? = null,

    @Json(name = "is_web_sdk_initialized")
    val isWebSdkInitialized: Boolean? = null,
)
