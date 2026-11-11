package com.marketap.sdk

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.presentation.MarketapRegistry
import com.squareup.moshi.JsonAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ClientKeyStorageTest {

    private class MapStorage : InternalStorage {
        val items = mutableMapOf<String, String>()
        override fun <T> setItem(key: String, value: T?, adapter: JsonAdapter<T>) {
            if (value == null) items.remove(key) else items[key] = adapter.toJson(value)
        }
        override fun <T> getItem(key: String, adapter: JsonAdapter<T>): T? = items[key]?.let { adapter.fromJson(it) }
        override fun <T> cacheAndGetItem(key: String, value: () -> T, adapter: JsonAdapter<T>, invalidationTime: Long): T? = value()
        override fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>) = throw UnsupportedOperationException()
        override fun <T> getItemList(key: String, adapter: JsonAdapter<T>): List<T> = throw UnsupportedOperationException()
        override fun <T> popItems(key: String, adapter: JsonAdapter<T>, count: Int): List<T> = throw UnsupportedOperationException()
        override fun removeItem(key: String) { items.remove(key) }
    }

    @Test
    fun `현재 프로젝트는 null을 포함한 레지스트리 키를 즉시 반영한다`() {
        val storage = MapStorage()
        try {
            MarketapRegistry.config = MarketapConfig("A", "android", "1")
            MarketapRegistry.clientKey = null
            assertNull(SdkMetadataProvider.currentClientKey(storage, "A"))
            SdkMetadataProvider.saveClientKey(storage, "A", "stored")
            assertNull(SdkMetadataProvider.currentClientKey(storage, "A"))
            MarketapRegistry.clientKey = "ck_1"
            assertEquals("ck_1", SdkMetadataProvider.currentClientKey(storage, "A"))
            MarketapRegistry.clientKey = "ck_2"
            assertEquals("ck_2", SdkMetadataProvider.currentClientKey(storage, "A"))
        } finally {
            MarketapRegistry.clientKey = null
            MarketapRegistry.config = null
        }
    }

    @Test
    fun `프로젝트를 바꿔 재초기화해도 이전 프로젝트 요청에는 그 프로젝트의 키가 쓰인다`() {
        val storage = MapStorage()
        try {
            SdkMetadataProvider.saveClientKey(storage, "A", "key_a")
            MarketapRegistry.config = MarketapConfig("A", "android", "1")
            MarketapRegistry.clientKey = "key_a"

            SdkMetadataProvider.saveClientKey(storage, "B", "key_b")
            MarketapRegistry.config = MarketapConfig("B", "android", "1")
            MarketapRegistry.clientKey = "key_b"

            assertEquals("key_a", SdkMetadataProvider.currentClientKey(storage, "A"))
            assertEquals("key_b", SdkMetadataProvider.currentClientKey(storage, "B"))
            assertNull(SdkMetadataProvider.currentClientKey(storage, "C"))
            assertNull(SdkMetadataProvider.currentClientKey(storage, ""))
        } finally {
            MarketapRegistry.clientKey = null
            MarketapRegistry.config = null
        }
    }

    @Test
    fun `키 없이 초기화하면 해당 프로젝트 키만 지워진다`() {
        val storage = MapStorage()
        SdkMetadataProvider.saveClientKey(storage, "A", "key_a")
        SdkMetadataProvider.saveClientKey(storage, "B", "key_b")
        SdkMetadataProvider.saveClientKey(storage, "A", null)
        assertNull(SdkMetadataProvider.loadClientKey(storage, "A"))
        assertEquals("key_b", SdkMetadataProvider.loadClientKey(storage, "B"))
    }

    @Test
    fun `저장한 키를 다시 읽는다`() {
        val storage = MapStorage()
        SdkMetadataProvider.saveClientKey(storage, "A", "ck_abc")
        assertEquals("ck_abc", SdkMetadataProvider.loadClientKey(storage, "A"))
    }

    @Test
    fun `null로 저장하면 이전 키가 지워진다`() {
        val storage = MapStorage()
        SdkMetadataProvider.saveClientKey(storage, "A", "ck_abc")
        SdkMetadataProvider.saveClientKey(storage, "A", null)
        assertNull(SdkMetadataProvider.loadClientKey(storage, "A"))
    }

    @Test
    fun `공백 키는 null로 정규화된다`() {
        assertNull(SdkMetadataProvider.normalizeClientKey("  "))
        assertNull(SdkMetadataProvider.normalizeClientKey(null))
        assertEquals("k", SdkMetadataProvider.normalizeClientKey("k"))
    }

    @Test
    fun `키 앞뒤 공백은 지우고 허용 문자 밖의 키는 버린다`() {
        assertEquals("ck_aB-9", SdkMetadataProvider.normalizeClientKey("  ck_aB-9 \n"))
        listOf("ck bad", "ck\nbad", "ck_bad키", "ck.bad").forEach {
            assertNull(SdkMetadataProvider.normalizeClientKey(it))
        }
    }

    @Test
    fun `128자까지만 허용하고 초과하면 버린다`() {
        assertEquals("a".repeat(128), SdkMetadataProvider.normalizeClientKey("a".repeat(128)))
        assertNull(SdkMetadataProvider.normalizeClientKey("a".repeat(129)))
    }
}
