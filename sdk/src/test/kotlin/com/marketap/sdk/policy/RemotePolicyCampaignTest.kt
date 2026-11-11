package com.marketap.sdk.policy

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.service.inapp.CampaignFetchService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.InAppCampaign
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class RemotePolicyCampaignTest {
    @Test
    fun `인앱 중지 시 로컬 캠페인과 HTML 캐시도 사용하지 않는다`() {
        val storage = mockk<InternalStorage>()
        val backend = mockk<MarketapBackend>()
        val state = mockk<ClientStateManager>()
        val device = mockk<DeviceManager>()
        val fetch = CampaignFetchService(storage, backend, state, device) { false }
        var called = false
        fetch.useCampaigns {
            assertTrue(it.isEmpty())
            called = true
        }
        assertTrue(called)
        assertNull(fetch.resolveCampaignHtml(mockk<InAppCampaign>(), mockk()))
        verify { listOf(storage, backend, state, device) wasNot Called }
    }
}
