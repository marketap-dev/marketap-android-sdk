package com.marketap.sdk

import android.content.Context
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.external.MarketapIntegrationInfo
import com.marketap.sdk.presentation.MarketapRegistry
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.stringAdapter

internal object SdkMetadataProvider {
    private const val KEY_INTEGRATION_INFO = "marketap_integration_info"
    private const val KEY_CLIENT_KEY_PREFIX = "marketap_client_key."

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

    fun normalizeClientKey(clientKey: String?): String? {
        val key = clientKey?.trim()?.takeIf { it.isNotEmpty() }
        if (key == null) {
            logger.d { "Initialized without a client key" }
            return null
        }
        if (key.matches(Regex("[A-Za-z0-9_-]{1,128}"))) return key
        logger.w { "Invalid client key format; sending requests without a client key" }
        return null
    }

    fun saveIntegrationInfo(context: Context, config: MarketapConfig, clientKey: String?) {
        val storage = SharedPreferenceInternalStorage(context)
        saveIntegrationInfo(
            storage,
            MarketapIntegrationInfo(
                sdkType = config.sdkType,
                sdkVersion = config.sdkVersion
            )
        )
        saveClientKey(storage, config.projectId, clientKey)
    }

    fun saveClientKey(storage: InternalStorage, projectId: String, clientKey: String?) {
        if (clientKey.isNullOrEmpty()) {
            storage.removeItem(KEY_CLIENT_KEY_PREFIX + projectId)
        } else {
            storage.setItem(KEY_CLIENT_KEY_PREFIX + projectId, clientKey, stringAdapter)
        }
    }

    fun currentClientKey(storage: InternalStorage, projectId: String?): String? {
        if (projectId.isNullOrEmpty()) return null
        if (MarketapRegistry.config?.projectId == projectId) {
            return MarketapRegistry.clientKey
        }
        return loadClientKey(storage, projectId)
    }

    fun loadClientKey(storage: InternalStorage, projectId: String): String? {
        return storage.getItem(KEY_CLIENT_KEY_PREFIX + projectId, stringAdapter)?.takeIf { it.isNotEmpty() }
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
