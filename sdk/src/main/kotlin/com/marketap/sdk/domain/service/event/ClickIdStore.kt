package com.marketap.sdk.domain.service.event

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.utils.longAdapter
import com.marketap.sdk.utils.stringAdapter

internal class ClickIdStore(
    private val storage: InternalStorage,
    private val now: () -> Long = System::currentTimeMillis,
    private val enabled: () -> Boolean = { true },
) {
    @Synchronized
    fun setPending(clickId: String, receivedAt: Long = now()) {
        storage.setItem(PENDING_KEY, clickId, stringAdapter)
        storage.setItem(PENDING_AT_KEY, receivedAt, longAdapter)
    }

    @Synchronized
    fun attach(eventName: String, properties: Map<String, Any>): Pair<Map<String, Any>, String?> {
        if (eventName in MESSAGE_EVENTS) return properties to null
        val pending = readPending() ?: return properties to null
        if (!enabled()) return properties to null
        val existing = (properties[KEY] as? String)?.takeIf { it.isNotEmpty() }
        if (existing == null) return (properties + (KEY to pending)) to pending
        return properties to pending.takeIf { it == existing }
    }

    @Synchronized
    fun consume(clickId: String) {
        if (storage.getItem(PENDING_KEY, stringAdapter) == clickId) clearPending()
    }

    private fun readPending(): String? {
        val at = storage.getItem(PENDING_AT_KEY, longAdapter) ?: return null
        if (now() - at > PENDING_TTL_MS) {
            clearPending()
            return null
        }
        return storage.getItem(PENDING_KEY, stringAdapter)
    }

    private fun clearPending() {
        storage.removeItem(PENDING_KEY)
        storage.removeItem(PENDING_AT_KEY)
    }

    companion object {
        const val KEY = "mkt_clid"

        private val MESSAGE_EVENTS = setOf("mkt_delivery_message", "mkt_click_message", "mkt_push_impression")
        private const val PENDING_TTL_MS = 30 * 60 * 1000L
        private const val PENDING_KEY = "marketap_click_id_pending"
        private const val PENDING_AT_KEY = "marketap_click_id_pending_at"
        private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun clickIdFromUrl(url: String): String? =
            SessionUtmStore.queryParams(url).firstOrNull { it.first == KEY }?.second?.takeIf { UUID.matches(it) }
    }
}
