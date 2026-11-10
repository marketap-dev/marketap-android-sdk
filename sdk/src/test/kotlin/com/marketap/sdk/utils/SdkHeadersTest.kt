package com.marketap.sdk.utils

import com.marketap.sdk.SdkMetadataProvider
import com.marketap.sdk.model.MarketapConfig
import org.junit.Assert.*
import org.junit.Test

class SdkHeadersTest {
    @Test
    fun `래퍼 타입을 변환하고 잘못된 래퍼 버전만 생략한다`() {
        listOf("flutter" to "flutter", "react_native" to "react-native", "android" to null, "other" to null).forEach { (type, expected) ->
            listOf("1.2.3+abc-4", "unknown", "", "a b", "x".repeat(65)).forEach { version ->
                val headers = sdkHeaders("app", MarketapConfig("p", type, version))
                assertEquals(expected, headers["X-Marketap-SDK-Wrapper"])
                assertEquals(if (expected != null && version == "1.2.3+abc-4") version else null, headers["X-Marketap-SDK-Wrapper-Version"])
                assertEquals("android", headers["X-Marketap-SDK-Platform"])
                assertEquals(SdkMetadataProvider.nativeLibraryVersion, headers["X-Marketap-SDK-Version"])
                assertEquals("app", headers["X-Marketap-SDK-Source"])
            }
        }
    }
}
