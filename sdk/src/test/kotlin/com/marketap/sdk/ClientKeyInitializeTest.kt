package com.marketap.sdk

import android.app.Application
import com.marketap.sdk.client.FakeSharedPreferences
import com.marketap.sdk.domain.service.MarketapCoreService
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.presentation.Dependency
import com.marketap.sdk.presentation.MarketapRegistry
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClientKeyInitializeTest {
    private val prefs = FakeSharedPreferences()
    private val application = mockk<Application> {
        every { getSharedPreferences(any(), any()) } returns prefs
    }
    private val config = MarketapConfig("p", "android", "1")
    private val core = mockk<MarketapCoreService>()

    @Before
    fun setup() {
        MarketapRegistry.isInitialized = false
        mockkObject(Dependency)
        every { Dependency.initializeCore(config, application) } returns core
    }

    @After
    fun cleanup() {
        MarketapRegistry.isInitialized = false
        MarketapRegistry.config = null
        MarketapRegistry.clientKey = null
        MarketapRegistry.application = null
        MarketapRegistry.marketapCore = null
        unmockkObject(Dependency)
    }

    @Test
    fun `키만 바꾸면 core를 다시 만들지 않고 현재 키와 저장 키만 갱신한다`() {
        Marketap.initialize(application, config, " old_key ")
        Marketap.initialize(application, config, " new_key ")
        assertSame(core, MarketapRegistry.marketapCore)
        assertEquals("new_key", MarketapRegistry.clientKey)
        assertEquals("new_key", SdkMetadataProvider.loadClientKey(prefs.storage(), "p"))
        Marketap.initialize(application, config, null)
        assertNull(MarketapRegistry.clientKey)
        assertNull(SdkMetadataProvider.currentClientKey(prefs.storage(), "p"))
        verify(exactly = 1) { Dependency.initializeCore(any(), any()) }
    }
}
