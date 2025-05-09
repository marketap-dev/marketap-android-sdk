package com.marketap.sdk.client

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.utils.longAdapter
import com.marketap.sdk.utils.stringAdapter
import java.util.UUID

class AndroidSessionManager(
    private val storage: InternalStorage
) : SessionManager {
    private val SESSION_KEY = "marketap_session"
    private val LAST_ACTIVITY_KEY = "marketap_last_activity"
    private val expirationTime = 30 * 60 * 1000 // 30분

    override fun updateActivity() {
        storage.setItem(LAST_ACTIVITY_KEY, System.currentTimeMillis(), longAdapter)
    }

    override fun getSessionId(onSessionStart: (sessionId: String) -> Unit): String {
        val (sessionId, isNewSession) = getOrGenerateSessionId()
        if (isNewSession) {
            onSessionStart(sessionId)
        }
        return sessionId
    }

    private fun generateAndSaveSessionId(): String {
        val newSessionId = "${System.currentTimeMillis()}:${UUID.randomUUID()}"
        storage.setItem(SESSION_KEY, newSessionId, stringAdapter)
        storage.setItem(LAST_ACTIVITY_KEY, System.currentTimeMillis(), longAdapter)
        return newSessionId
    }

    private fun getOrGenerateSessionId(): Pair<String, Boolean> {
        val oldSessionId: String? = storage.getItem(SESSION_KEY, stringAdapter)
        val activityTime: Long? = storage.getItem(LAST_ACTIVITY_KEY, longAdapter)
        val currentTime = System.currentTimeMillis()

        return if (activityTime == null || oldSessionId == null || activityTime + expirationTime < currentTime) {
            generateAndSaveSessionId() to true
        } else {
            oldSessionId to false
        }
    }
}