package com.marketap.sdk.domain.service.state

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.utils.getTypeToken

class ClientStateManager(
    private val marketapConfig: MarketapConfig,
    private val internalStorage: InternalStorage,
) {
    fun getProjectId(): String {
        return marketapConfig.projectId
    }

    fun getUserId(): String? {
        return internalStorage.getItem("user_id", getTypeToken<String>())
    }

    fun setUserId(userId: String?) {
        internalStorage.setItem("user_id", userId)
    }
}