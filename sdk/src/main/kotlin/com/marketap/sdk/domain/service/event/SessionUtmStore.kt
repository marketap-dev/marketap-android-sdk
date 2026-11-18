package com.marketap.sdk.domain.service.event

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.utils.longAdapter
import com.marketap.sdk.utils.mapAdapter
import com.marketap.sdk.utils.stringAdapter
import java.net.URI
import java.net.URLDecoder

/**
 * 세션에서 마지막으로 들어온 UTM.
 *
 * 이벤트 속성의 `mkt_utm_*` 나 [setPending] 으로 UTM이 들어오면 그 세트로 통째로 교체하고,
 * 그 이벤트와 같은 세션의 이후 이벤트에 `mkt_session_utm_*` 로 붙인다. `mkt_utm_*` 는 건드리지 않는다.
 * 세션이 바뀌면 이전 UTM은 쓰지 않는다. 빈 값은 없는 것으로 본다.
 *
 * @param enabled 원격 정책 sessionUtm 이 중지면 false. 그동안은 저장도 부착도 하지 않는다.
 */
internal class SessionUtmStore(
    private val storage: InternalStorage,
    private val now: () -> Long = System::currentTimeMillis,
    private val enabled: () -> Boolean = { true },
) {
    /** 다음 이벤트에 넘길 UTM으로 보관한다. 이벤트를 보내지 않는다. */
    fun setPending(utm: Map<String, String>) {
        if (!enabled()) return
        val picked = pickUtm(utm)
        if (picked.isEmpty()) return
        storage.setItem(PENDING_KEY, picked, utmAdapter)
        storage.setItem(PENDING_AT_KEY, now(), longAdapter)
    }

    /** 세션을 여는 이벤트가 세션 시작 이벤트에 넘겨줄 세션 UTM. 보관 중인 UTM을 소비하지 않는다. */
    fun openingSessionUtm(properties: Map<String, Any>): Map<String, String> {
        if (!enabled()) return emptyMap()
        return toSessionUtm(pickUtm(properties).ifEmpty { readPending() })
    }

    fun apply(properties: Map<String, Any>, sessionId: String): Map<String, Any> {
        if (!enabled()) return properties
        val incoming = pickUtm(properties).ifEmpty { readPending() }
        if (incoming.isNotEmpty()) {
            storage.setItem(SESSION_KEY, sessionId, stringAdapter)
            storage.setItem(UTM_KEY, incoming, utmAdapter)
            clearPending()
            return properties + toSessionUtm(incoming)
        }

        return properties + stored(sessionId)
    }

    /** 이 세션에 저장된 세션 UTM. 저장하거나 보관 중인 UTM을 소비하지 않는다. */
    fun stored(sessionId: String): Map<String, String> {
        if (!enabled()) return emptyMap()
        if (storage.getItem(SESSION_KEY, stringAdapter) != sessionId) return emptyMap()
        return toSessionUtm(storage.getItem(UTM_KEY, utmAdapter)?.let { pickUtm(it) }.orEmpty())
    }

    private fun readPending(): Map<String, String> {
        val at = storage.getItem(PENDING_AT_KEY, longAdapter) ?: return emptyMap()
        if (now() - at > PENDING_TTL_MS) {
            clearPending()
            return emptyMap()
        }
        return storage.getItem(PENDING_KEY, utmAdapter)?.let { pickUtm(it) }.orEmpty()
    }

    private fun clearPending() {
        storage.removeItem(PENDING_KEY)
        storage.removeItem(PENDING_AT_KEY)
    }

    companion object {
        /** 이벤트가 일어난 페이지의 UTM 속성 키. 서버의 표준 이벤트 속성 이름과 같다. */
        val UTM_KEYS = listOf(
            "mkt_utm_source",
            "mkt_utm_medium",
            "mkt_utm_campaign",
            "mkt_utm_term",
            "mkt_utm_content",
        )

        /** 세션에서 마지막으로 들어온 UTM을 담는 속성 키. [UTM_KEYS] 와 같은 순서로 대응한다. */
        val SESSION_UTM_KEYS = listOf(
            "mkt_session_utm_source",
            "mkt_session_utm_medium",
            "mkt_session_utm_campaign",
            "mkt_session_utm_term",
            "mkt_session_utm_content",
        )

        /** 보관한 UTM을 다음 이벤트에 넘길 수 있는 시간. 세션 만료 시간과 같다. */
        private const val PENDING_TTL_MS = 30 * 60 * 1000L

        private const val SESSION_KEY = "marketap_session_utm_session_id"
        private const val UTM_KEY = "marketap_session_utm"
        private const val PENDING_KEY = "marketap_session_utm_pending"
        private const val PENDING_AT_KEY = "marketap_session_utm_pending_at"
        private val utmAdapter = mapAdapter<String, String>()

        fun pickUtm(source: Map<String, Any?>): Map<String, String> =
            UTM_KEYS.mapNotNull { key ->
                (source[key] as? String)?.takeIf { it.isNotEmpty() }?.let { key to it }
            }.toMap()

        fun toSessionUtm(utm: Map<String, String>): Map<String, String> =
            UTM_KEYS.zip(SESSION_UTM_KEYS).mapNotNull { (key, sessionKey) -> utm[key]?.let { sessionKey to it } }.toMap()

        /** 링크의 `utm_*` 쿼리 파라미터를 `mkt_utm_*` 키로 꺼낸다. 해석할 수 없는 링크면 빈 값이다. */
        fun utmFromUrl(url: String): Map<String, String> {
            val query = runCatching { URI(url.trim()).rawQuery }.getOrNull() ?: return emptyMap()
            val params = query.split('&').mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val name = runCatching { URLDecoder.decode(pair.substring(0, index), "UTF-8") }.getOrNull()
                val value = runCatching { URLDecoder.decode(pair.substring(index + 1), "UTF-8") }.getOrNull()
                if (name == null || value == null) null else name to value
            }
            return UTM_KEYS.mapNotNull { key ->
                val name = key.removePrefix("mkt_")
                params.firstOrNull { it.first == name }?.second?.takeIf { it.isNotEmpty() }?.let { key to it }
            }.toMap()
        }
    }
}
