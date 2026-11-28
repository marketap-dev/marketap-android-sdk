package com.marketap.sdk.presentation

import android.app.Application
import com.marketap.sdk.client.AndroidDeviceManager
import com.marketap.sdk.client.AndroidSessionManager
import com.marketap.sdk.client.AppForegroundLifecycle
import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.client.api.RetryMarketapBackend
import com.marketap.sdk.client.inapp.AndroidInAppView
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler
import com.marketap.sdk.domain.service.MarketapCoreService
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.ClickIdStore
import com.marketap.sdk.domain.service.event.SessionUtmStore
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.AppForegroundTracker
import com.marketap.sdk.domain.service.inapp.CampaignExposureService
import com.marketap.sdk.domain.service.inapp.CampaignFetchService
import com.marketap.sdk.domain.service.inapp.DelayedInAppCoordinator
import com.marketap.sdk.domain.service.inapp.InAppCallbackFactory
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.inapp.InAppTimerStore
import com.marketap.sdk.domain.service.inapp.SerialInAppScheduler
import com.marketap.sdk.domain.service.inapp.condition.ConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.EventFlowEvaluator
import com.marketap.sdk.domain.service.inapp.condition.OccurrenceConditionEvaluator
import com.marketap.sdk.domain.service.inapp.condition.PropertyConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.comparator.ValueComparatorImpl
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.SdkMetadataProvider
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.internal.SdkIntegrationState
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.utils.ManifestUtils
import com.marketap.sdk.utils.logger

object Dependency {
    private var previousInApp: Triple<AppForegroundLifecycle, SerialInAppScheduler, AppForegroundTracker>? = null

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

        val userIngestionService = UserIngestionService(
            clientStateManager,
            deviceManager,
            marketapBackend,
            storage,
        )

        lateinit var eventIngestionService: EventIngestionService
        val callbackFactory = InAppCallbackFactory(
            backend = marketapBackend,
            projectId = clientStateManager::getProjectId,
            userId = clientStateManager::getUserId,
            device = { deviceManager.getDevice().toReq() },
            sessionId = { eventIngestionService.currentSessionId() },
            trackEvent = { name, properties, identity -> eventIngestionService.trackInAppEvent(name, properties, identity) },
            setUserProperties = userIngestionService::setUserProperties,
            withSessionUtm = { properties, sessionId -> eventIngestionService.withSessionUtm(properties, sessionId) },
        )

        val previous = previousInApp
        previous?.let { (lifecycle, scheduler, _) ->
            application.unregisterActivityLifecycleCallbacks(lifecycle)
            scheduler.shutdown()
        }
        val inAppScheduler = SerialInAppScheduler()
        val foregroundTracker = AppForegroundTracker(
            inAppScheduler,
            probeResumedScreen = AppForegroundLifecycle::probeResumedScreen,
            previous = previous?.third,
        )
        val foregroundLifecycle = AppForegroundLifecycle(foregroundTracker)
        application.registerActivityLifecycleCallbacks(foregroundLifecycle)
        previousInApp = Triple(foregroundLifecycle, inAppScheduler, foregroundTracker)
        val canDisplay = { foregroundTracker.hasResumedScreen() && CurrentActivityHolder.getScreen() != null }
        val conditionChecker = ConditionCheckerImpl(PropertyConditionCheckerImpl(ValueComparatorImpl()))
        val inAppView = AndroidInAppView.getInstance()

        val campaignFetchService = CampaignFetchService(storage, marketapBackend, clientStateManager, deviceManager) {
            marketapBackend.remotePolicy.currentlyAllowed(config.projectId, "inApp")
        }
        val inAppService = InAppService(
            CampaignExposureService(storage) {
                CampaignExposureService.retentionFor(campaignFetchService.getGlobalFrequencyCaps())
            },
            conditionChecker,
            campaignFetchService,
            inAppView,
            callbackFactory::create,
            OccurrenceConditionEvaluator(storage),
            DelayedInAppCoordinator(
                storage,
                inAppScheduler,
                conditionChecker,
                canDisplay,
                foregroundTracker::isForeground,
                inAppView::isShowing,
            ),
            inAppScheduler,
            canDisplay = canDisplay,
            timerStore = InAppTimerStore(storage),
            eventFlowEvaluator = EventFlowEvaluator(storage, conditionChecker),
        )

        eventIngestionService = EventIngestionService(
            marketapBackend,
            inAppService,
            clientStateManager,
            sessionManager,
            deviceManager,
            userIngestionService,
            SessionUtmStore(storage, enabled = { marketapBackend.remotePolicy.currentlyAllowed(config.projectId, "sessionUtm") }),
            ClickIdStore(storage, enabled = { marketapBackend.remotePolicy.currentlyAllowed(config.projectId, "collection") }),
        )
        foregroundTracker.addForegroundListener(inAppService::restoreDelayed)
        inAppService.restoreDelayed()

        val core = MarketapCoreService(
            eventIngestionService,
            userIngestionService,
            deviceManager,
            inAppService,
            marketapBackend,
            clientStateManager,
            sessionManager,
        )

        MarketapRegistry.utmBeforeInit?.let {
            eventIngestionService.setPendingUtm(it)
            MarketapRegistry.utmBeforeInit = null
        }
        synchronized(MarketapRegistry) {
            MarketapRegistry.clickIdBeforeInit?.let { (clickId, receivedAt) ->
                eventIngestionService.setPendingClickId(clickId, receivedAt)
                MarketapRegistry.clickIdBeforeInit = null
            }
        }

        val deviceListener = DeviceListener(deviceManager, userIngestionService, application, core)
        deviceListener.init()
        marketapBackend.prefetchServerInfo(config.projectId)
        ManifestUtils.logSystemConstants(application)
        logger.i { "Marketap SDK successfully initialized with projectId: ${config.projectId}" }
        return core
    }
}