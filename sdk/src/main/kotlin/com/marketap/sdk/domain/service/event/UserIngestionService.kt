package com.marketap.sdk.domain.service.event

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.utils.logger

internal class UserIngestionService(
    private val clientStateManager: ClientStateManager,
    private val deviceManager: DeviceManager,
    private val marketapBackend: MarketapBackend,
) {

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
            marketapBackend.updateDevice(
                clientStateManager.getProjectId(),
                deviceManager.getDevice().toReq()
            )
        } catch (t: Throwable) {
            logger.e(t) { "Failed to push device" }
        }
    }
}
