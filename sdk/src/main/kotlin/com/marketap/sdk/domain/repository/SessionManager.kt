package com.marketap.sdk.domain.repository

interface SessionManager {
    fun updateActivity()
    fun getSessionId(onSessionStart: (sessionId: String) -> Unit): String
}