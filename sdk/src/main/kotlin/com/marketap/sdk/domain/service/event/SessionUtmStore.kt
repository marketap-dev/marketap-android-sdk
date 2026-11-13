package com.marketap.sdk.domain.service.event

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.utils.mapAdapter
import com.marketap.sdk.utils.stringAdapter

/**
 * 세션 동안 유지하는 UTM.
 *
 * 이벤트 속성에 UTM이 하나라도 있으면 그 세트로 통째로 교체하고 속성은 그대로 둔다.
 * 없으면 같은 세션에 저장된 UTM을 붙인다. 세션이 바뀌면 이전 UTM은 쓰지 않는다. 빈 값은 없는 것으로 본다.
 */
internal class SessionUtmStore(
    private val storage: InternalStorage,
) {
    fun apply(properties: Map<String, Any>, sessionId: String): Map<String, Any> {
        val incoming = pickUtm(properties)
        if (incoming.isNotEmpty()) {
            storage.setItem(SESSION_KEY, sessionId, stringAdapter)
            storage.setItem(UTM_KEY, incoming, utmAdapter)
            return properties
        }

        val storedSessionId = storage.getItem(SESSION_KEY, stringAdapter) ?: return properties
        if (storedSessionId != sessionId) return properties
        val stored = storage.getItem(UTM_KEY, utmAdapter)?.let { pickUtm(it) } ?: return properties
        if (stored.isEmpty()) return properties
        return properties + stored
    }

    companion object {
        /** 세션 동안 유지하는 UTM 속성 키. 서버의 표준 이벤트 속성 이름과 같다. */
        val UTM_KEYS = listOf(
            "mkt_utm_source",
            "mkt_utm_medium",
            "mkt_utm_campaign",
            "mkt_utm_term",
            "mkt_utm_content",
        )

        private const val SESSION_KEY = "marketap_session_utm_session_id"
        private const val UTM_KEY = "marketap_session_utm"
        private val utmAdapter = mapAdapter<String, String>()

        fun pickUtm(source: Map<String, Any?>): Map<String, String> =
            UTM_KEYS.mapNotNull { key ->
                (source[key] as? String)?.takeIf { it.isNotEmpty() }?.let { key to it }
            }.toMap()
    }
}
