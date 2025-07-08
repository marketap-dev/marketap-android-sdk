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
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.CampaignExposureService
import com.marketap.sdk.domain.service.inapp.CampaignFetchService
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.inapp.condition.ConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.PropertyConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.comparator.ValueComparatorImpl
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.utils.ManifestUtils
import com.marketap.sdk.utils.logger

object Dependency {
    internal fun initializeCore(
        config: MarketapConfig,
        application: Application
    ): MarketapCoreService {
        val storage = SharedPreferenceInternalStorage(application).initialize(application)
        val marketapApi = MarketapApiImpl()
        val deviceManager = AndroidDeviceManager(storage)
        val marketapBackend = RetryMarketapBackend(storage, marketapApi, deviceManager)
        val clientStateManager = ClientStateManager(config, storage)
        val sessionManager = AndroidSessionManager(storage)

        CurrentActivityHolder.applyToApplication(application)

        // Initialize handler
        MarketapNotificationOpenHandler(application)

        val inAppService = InAppService(
            CampaignExposureService(storage),
            ConditionCheckerImpl(PropertyConditionCheckerImpl(ValueComparatorImpl())),
            CampaignFetchService(storage, marketapBackend, clientStateManager, deviceManager),
            AndroidInAppView.getInstance()
        )

        val eventIngestionService = EventIngestionService(
            marketapBackend,
            inAppService,
            clientStateManager,
            sessionManager,
            deviceManager
        )

        val userIngestionService = UserIngestionService(
            clientStateManager,
            deviceManager,
            marketapBackend,
        )

        val core = MarketapCoreService(
            eventIngestionService,
            userIngestionService,
            deviceManager,
        )

        val deviceListener = DeviceListener(deviceManager, userIngestionService, application, core)
        deviceListener.init()
        ManifestUtils.logSystemConstants(application)
        logger.i { "Marketap SDK successfully initialized with projectId: ${config.projectId}" }
        return core
    }
}