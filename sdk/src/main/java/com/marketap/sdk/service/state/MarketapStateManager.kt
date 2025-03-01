package com.marketap.sdk.service.state

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdClient
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.internal.MarketapState
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.service.state.inapp.InAppCampaignStateManager
import com.marketap.sdk.utils.getTypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class MarketapStateManager(
    private val storage: InternalStorage,
    private val inAppCampaignStateManager: InAppCampaignStateManager,
    private val deviceManager: DeviceManager,
    config: MarketapConfig,
    application: Application
) : StateManager {
    private val projectId: String = config.projectId

    init {
        storage.setItem("project_id", config.projectId)
        storage.setItem("debug", config.debug)
        deviceManager.updateDevice(projectId)
        fetchInApp(userId)

        try {
            if (FirebaseApp.getApps(application).isEmpty()) {
                FirebaseApp.initializeApp(application)
            }

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    deviceManager.setToken(task.result)
                    deviceManager.updateDevice(projectId)
                }
            }
        } catch (e: Exception) {
            Log.e("MarketapSDK", "Failed to fetch FCM token: ${e.message}")
        }

        fetchAndStoreGAID(application, projectId)
        fetchAppSetId(application, projectId)
    }

    private fun fetchInApp(userId: String?) {
        inAppCampaignStateManager.fetchInAppCampaigns(
            FetchCampaignReq(
                userId = userId,
                projectId = projectId,
                device = deviceManager.getDevice().toReq(),
            )
        )
    }

    private fun fetchAndStoreGAID(context: Context, projectId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
                val id = adInfo.id
                if (id != null && id != "00000000-0000-0000-0000-000000000000") {
                    deviceManager.setGoogleAdvertisingId(id, projectId)
                }
            } catch (e: Exception) {
                Log.e("MarketapSDK", "Failed to fetch GAID: ${e.message}")
            }
        }
    }

    private fun fetchAppSetId(context: Context, projectId: String) {
        val client: AppSetIdClient = AppSet.getClient(context)
        client.appSetIdInfo.addOnSuccessListener { appSetIdInfo ->
            val appSetId = appSetIdInfo.id
            deviceManager.setAppSetId(appSetId, projectId)
        }.addOnFailureListener {
            Log.e("MarketapSDK", "Failed to fetch AppSet ID: ${it.message}")
        }
    }

    private val userId: String?
        get() = storage.getItem("user_id", getTypeToken<String>())

    override fun setUserId(userId: String?) {
        if (this.userId == userId) {
            return
        } else {
            if (userId == null) {
                deviceManager.updateDevice(projectId, true)
            }

            storage.setItem("user_id", userId)
            fetchInApp(userId)
        }
    }

    override fun getState(): MarketapState {
        return MarketapState(
            userId,
            deviceManager.getDevice(),
            projectId,
        )
    }
}