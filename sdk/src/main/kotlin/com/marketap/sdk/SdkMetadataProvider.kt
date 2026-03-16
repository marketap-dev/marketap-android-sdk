package com.marketap.sdk

import android.content.Context
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.external.MarketapIntegrationInfo
import com.marketap.sdk.utils.adapter

internal object SdkMetadataProvider {
    private const val KEY_INTEGRATION_INFO = "marketap_integration_info"

    val nativeIntegrationInfo = MarketapIntegrationInfo(
        sdkType = BuildConfig.MARKETAP_SDK_TYPE,
        sdkVersion = BuildConfig.MARKETAP_SDK_VERSION
    )

    val nativeLibraryVersion: String
        get() = BuildConfig.MARKETAP_SDK_VERSION

    fun createNativeConfig(projectId: String): MarketapConfig {
        return createConfig(projectId, nativeIntegrationInfo)
    }

    fun createConfig(projectId: String, integrationInfo: MarketapIntegrationInfo): MarketapConfig {
        return MarketapConfig(
            projectId = projectId,
            sdkType = integrationInfo.sdkType,
            sdkVersion = integrationInfo.sdkVersion
        )
    }

    fun saveIntegrationInfo(context: Context, config: MarketapConfig) {
        saveIntegrationInfo(
            SharedPreferenceInternalStorage(context),
            MarketapIntegrationInfo(
                sdkType = config.sdkType,
                sdkVersion = config.sdkVersion
            )
        )
    }

    fun saveIntegrationInfo(storage: InternalStorage, integrationInfo: MarketapIntegrationInfo) {
        storage.setItem(KEY_INTEGRATION_INFO, integrationInfo, adapter())
    }

    fun loadIntegrationInfo(context: Context): MarketapIntegrationInfo? {
        return loadIntegrationInfo(SharedPreferenceInternalStorage(context))
    }

    fun loadIntegrationInfo(storage: InternalStorage): MarketapIntegrationInfo? {
        return storage.getItem(KEY_INTEGRATION_INFO, adapter())
    }
}
