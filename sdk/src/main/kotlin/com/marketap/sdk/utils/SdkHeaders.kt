package com.marketap.sdk.utils

import com.marketap.sdk.SdkMetadataProvider
import com.marketap.sdk.model.MarketapConfig

internal fun sdkHeaders(source: String, config: MarketapConfig?): Map<String, String> {
    val prefix = "X-Marketap-SDK-"
    val headers = mutableMapOf(
        prefix + "Platform" to "android",
        prefix + "Version" to SdkMetadataProvider.nativeLibraryVersion,
        prefix + "Source" to source
    )
    val wrapper = when (config?.sdkType) {
        "flutter" -> "flutter"
        "react_native" -> "react-native"
        else -> null
    }
    if (wrapper != null) {
        headers[prefix + "Wrapper"] = wrapper
        config?.sdkVersion?.takeIf { it != "unknown" && it.matches(Regex("[0-9A-Za-z.+-]{1,64}")) }?.let {
            headers[prefix + "Wrapper-Version"] = it
        }
    }
    return headers
}
