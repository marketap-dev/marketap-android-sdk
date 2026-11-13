package com.marketap.sdk.presentation

import android.app.Application
import com.marketap.sdk.client.AndroidDeviceManager
import com.marketap.sdk.client.AndroidSessionManager
import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.client.api.RetryMarketapBackend
import com.marketap.sdk.client.inapp.AndroidInAppView
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler
import com.marketap.sdk.domain.service.MarketapCoreService
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.SessionUtmStore
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.CampaignExposureService
import com.marketap.sdk.domain.service.inapp.CampaignFetchService
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.inapp.condition.ConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.PropertyConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.comparator.ValueComparatorImpl
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.SdkMetadataProvider
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.internal.SdkIntegrationState
import com.marketap.sdk.utils.ManifestUtils
import com.marketap.sdk.utils.logger

object Dependency {
    internal fun initializeCore(
        config: MarketapConfig,
        application: Application
    ): MarketapCoreService {
        val storage = SharedPreferenceInternalStorage(application).initialize(application)
        val marketapApi = MarketapApiImpl(clientKeyProvider = { projectId -> SdkMetadataProvider.currentClientKey(storage, projectId) }, config = config)
        val deviceManager = AndroidDeviceManager(storage, application, config)
        SdkIntegrationState.isClickHandlerCustomized = CustomHandlerStore.isCustomized()

        val pushApi = MarketapApiImpl(clientKeyProvider = { projectId -> SdkMetadataProvider.currentClientKey(storage, projectId) }, source = "android-push", config = config)
        val marketapBackend = RetryMarketapBackend(storage, marketapApi, deviceManager, sdkType = config.sdkType, sdkVersion = config.sdkVersion, pushApi = pushApi)
        listOf("collection", "inApp", "pushTracking").forEach { marketapBackend.remotePolicy.allowed(config.projectId, it) }
        val clientStateManager = ClientStateManager(config, storage)
        val sessionManager = AndroidSessionManager(storage)

        CurrentActivityHolder.applyToApplication(application)

        // Initialize handler
        MarketapNotificationOpenHandler(application)

        val inAppService = InAppService(
            CampaignExposureService(storage),
            ConditionCheckerImpl(PropertyConditionCheckerImpl(ValueComparatorImpl())),
            CampaignFetchService(storage, marketapBackend, clientStateManager, deviceManager) { marketapBackend.remotePolicy.currentlyAllowed(config.projectId, "inApp") },
            AndroidInAppView.getInstance()
        )

        val userIngestionService = UserIngestionService(
            clientStateManager,
            deviceManager,
            marketapBackend,
            storage,
        )

        val eventIngestionService = EventIngestionService(
            marketapBackend,
            inAppService,
            clientStateManager,
            sessionManager,
            deviceManager,
            userIngestionService,
            SessionUtmStore(storage),
        )

        val core = MarketapCoreService(
            eventIngestionService,
            userIngestionService,
            deviceManager,
            inAppService,
            marketapBackend,
            clientStateManager,
            sessionManager,
        )

        val deviceListener = DeviceListener(deviceManager, userIngestionService, application, core)
        deviceListener.init()
        marketapBackend.prefetchServerInfo(config.projectId)
        ManifestUtils.logSystemConstants(application)
        logger.i { "Marketap SDK successfully initialized with projectId: ${config.projectId}" }
        return core
    }
}