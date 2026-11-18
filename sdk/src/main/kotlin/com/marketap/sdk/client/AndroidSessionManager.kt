package com.marketap.sdk.client

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.utils.logger
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
            logger.d { "New session started: $sessionId" }
            onSessionStart(sessionId)
        }
        return sessionId
    }

    /** 진행 중인 세션의 id. 세션이 없거나 만료됐으면 null이고, 새 세션을 만들지 않는다. */
    fun activeSessionId(): String? {
        val sessionId = storage.getItem(SESSION_KEY, stringAdapter) ?: return null
        val activityTime = storage.getItem(LAST_ACTIVITY_KEY, longAdapter) ?: return null
        return sessionId.takeIf { activityTime + expirationTime >= System.currentTimeMillis() }
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
            if (activityTime == null) {
                logger.d { "Session expired, generating new session ID" }
            } else {
                logger.d { "No previous session found, generating new session ID" }
            }
            generateAndSaveSessionId() to true
        } else {
            oldSessionId to false
        }
    }
}