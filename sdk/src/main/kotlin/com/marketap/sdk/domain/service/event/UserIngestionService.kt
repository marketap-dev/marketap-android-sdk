package com.marketap.sdk.domain.service.event

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.utils.logger

internal class UserIngestionService(
    private val clientStateManager: ClientStateManager,
    private val deviceManager: DeviceManager,
    private val marketapBackend: MarketapBackend,
) {
    @Volatile
    private var lastSentDeviceReq: DeviceReq? = null

    fun identify(userId: String, userProperties: Map<String, Any>?) {
        clientStateManager.setUserId(userId)
        try {
            marketapBackend.updateProfile(
                clientStateManager.getProjectId(),
                UpdateProfileRequest(
                    userId,
                    (userProperties ?: emptyMap()),
                    deviceManager.getDevice().toReq()
                )
            )
        } catch (t: Throwable) {
            logger.e(t) { "Failed to identify user: $userId" }
        }
    }

    fun setUserProperties(userProperties: Map<String, Any>) {
        val currentUserId = clientStateManager.getUserId() ?: return

        try {
            marketapBackend.updateProfile(
                clientStateManager.getProjectId(),
                UpdateProfileRequest(
                    currentUserId,
                    userProperties,
                    deviceManager.getDevice().toReq()
                )
            )
        } catch (t: Throwable) {
            logger.e(t) { "Failed to set user properties" }
        }
    }

    fun resetIdentity() {
        clientStateManager.setUserId(null)
        try {
            val device = deviceManager.getDevice().toReq(true)
            marketapBackend.updateDevice(clientStateManager.getProjectId(), device)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to reset identity" }
        }
    }

    fun pushDevice() {
        try {
            val deviceReq = deviceManager.getDevice().toReq()
            if (deviceReq == lastSentDeviceReq) {
                logger.d { "Device info unchanged, skipping update" }
                return
            }
            marketapBackend.updateDevice(
                clientStateManager.getProjectId(),
                deviceReq
            )
            lastSentDeviceReq = deviceReq
        } catch (t: Throwable) {
            logger.e(t) { "Failed to push device" }
        }
    }
}
