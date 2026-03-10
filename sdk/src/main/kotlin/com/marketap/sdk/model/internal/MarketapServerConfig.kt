package com.marketap.sdk.model.internal

internal object MarketapServerConfig {
    @Volatile var useWebClickRouting: Boolean = false
    @Volatile var serverTimeOffset: Long = 0L
    @Volatile var lastFetchedAtMs: Long = 0L

    private const val CACHE_DURATION_MS = 60_000L

    fun isCacheValid(): Boolean {
        return lastFetchedAtMs > 0L &&
                System.currentTimeMillis() - lastFetchedAtMs < CACHE_DURATION_MS
    }
}
