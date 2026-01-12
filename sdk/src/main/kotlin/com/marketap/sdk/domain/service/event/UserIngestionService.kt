package com.marketap.sdk.domain.service.event

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.UpdateProfileRequest

internal class UserIngestionService(
    private val clientStateManager: ClientStateManager,
    private val deviceManager: DeviceManager,
    private val marketapBackend: MarketapBackend,
) {

    fun identify(userId: String, userProperties: Map<String, Any>?) {
        clientStateManager.setUserId(userId)
        marketapBackend.updateProfile(
            clientStateManager.getProjectId(),
            UpdateProfileRequest(
                userId,
                (userProperties ?: emptyMap()),
                deviceManager.getDevice().toReq()
            )
        )
    }

    fun setUserProperties(userProperties: Map<String, Any>) {
        val currentUserId = clientStateManager.getUserId() ?: return

        marketapBackend.updateProfile(
            clientStateManager.getProjectId(),
            UpdateProfileRequest(
                currentUserId,
                userProperties,
                deviceManager.getDevice().toReq()
            )
        )
    }

    fun resetIdentity() {
        clientStateManager.setUserId(null)
        val device = deviceManager.getDevice().toReq(true)
        marketapBackend.updateDevice(clientStateManager.getProjectId(), device)
    }

    fun pushDevice() {
        marketapBackend.updateDevice(
            clientStateManager.getProjectId(),
            deviceManager.getDevice().toReq()
        )
    }
}