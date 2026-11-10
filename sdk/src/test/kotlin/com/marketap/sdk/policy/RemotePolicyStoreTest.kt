package com.marketap.sdk.policy

import com.marketap.sdk.SdkMetadataProvider
import com.marketap.sdk.client.FakeSharedPreferences
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.client.policy.RemotePolicyStore
import com.marketap.sdk.client.policy.readSdkPolicy
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.utils.PairEntry
import com.marketap.sdk.utils.moshi
import com.marketap.sdk.utils.pairAdapter
import com.marketap.sdk.utils.stringAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RemotePolicyStoreTest {
    private val project = UUID.randomUUID().toString()
    private val storage = PolicyMemoryStorage()
    private var time = 1794387600000L
    private val policy = RemotePolicyStore(storage, { time })
    private fun parse(json: String) = moshi.adapter(Any::class.java).fromJson(json)
    private fun accept(json: String) = policy.accept(project, parse(json))
    private fun allowed(block: String = "collection") = policy.allowed(project, block)
    private fun saved() = storage.getItem("marketap_remote_policy.$project", stringAdapter)

    @Test
    fun `지원하지 않는 버전과 역전 또는 같은 revision은 현재 상태를 유지한다`() {
        accept("""{"version":1,"revision":2,"collection":{"mode":"stop"}}""")
        val before = saved()
        repeat(2) { accept("""{"version":2,"revision":3,"collection":{"mode":"allow"}}""") }
        accept("""{"version":1,"revision":1,"collection":{"mode":"allow"}}""")
        accept("""{"version":1,"revision":2,"collection":{"mode":"allow"}}""")
        assertFalse(allowed())
        assertEquals(before, saved())
    }

    @Test
    fun `새 정책은 전체 교체하고 모르는 필드를 무시하며 잘못된 블록만 유지한다`() {
        accept("""{"version":1,"revision":1,"collection":{"mode":"stop"},"inApp":{"mode":"stop"},"pushTracking":{"mode":"stop"}}""")
        accept("""{"version":1,"revision":2,"collection":{"mode":"future"},"inApp":{"mode":"allow","future":1},"future":{"mode":"stop"}}""")
        assertFalse(allowed())
        assertTrue(allowed("inApp"))
        assertTrue(allowed("pushTracking"))
        accept("""{"version":1,"revision":3}""")
        assertTrue(allowed())
    }

    @Test
    fun `만료 시각이 지나면 allow로 돌아간다`() {
        time = 1794387599000L
        accept("""{"version":1,"revision":1,"collection":{"mode":"stop","expiresAt":"2026-11-11T09:00:00Z"}}""")
        assertFalse(allowed())
        assertFalse(allowed())
        time += 1000L
        assertTrue(allowed())
        assertTrue(allowed())
    }

    @Test
    fun `정책 없음 응답은 저장값을 바꾸지 않고 저장된 만료 시각만 계속 적용한다`() {
        policy.accept(project, null)
        assertTrue(allowed())
        assertNull(saved())
        time = 1794387599000L
        accept("""{"version":1,"revision":8,"collection":{"mode":"stop","expiresAt":"2026-11-11T09:00:00Z"},"pushTracking":{"mode":"stop"}}""")
        val before = saved()
        policy.accept(project, null)
        assertFalse(allowed())
        assertFalse(allowed("pushTracking"))
        assertEquals(before, saved())
        time += 1000L
        policy.accept(project, null)
        assertTrue(allowed())
        assertFalse(allowed("pushTracking"))
        assertEquals(before, saved())
        assertFalse(RemotePolicyStore(storage, { time }).allowed(project, "pushTracking"))
    }

    @Test
    fun `정책의 실제 JSON 바이트가 16KB를 넘으면 공백도 포함해 무시한다`() {
        accept("""{"version":1,"revision":1,"collection":{"mode":"stop"}}""")
        val before = saved()
        val raw = """{"data":{"sdkPolicy":{"version":1,"revision":2,"collection":{"mode":"allow"},"padding":"${"a".repeat(16384)}"}}}"""
        policy.accept(project, readSdkPolicy(raw))
        val whitespace = """{"data":{"sdkPolicy":{${" ".repeat(16384)}"version":1,"revision":3}}}"""
        policy.accept(project, readSdkPolicy(whitespace))
        assertEquals(before, saved())
        assertFalse(allowed())
    }

    @Test
    fun `갱신 주기는 기본 300초이고 60초에서 3600초 사이로 제한한다`() {
        listOf(null to 300L, 1L to 60L, 9999L to 3600L).forEachIndexed { index, (seconds, expected) ->
            val refresh = seconds?.let { ",\"refreshAfterSec\":$it" } ?: ""
            accept("""{"version":1,"revision":${index + 1}$refresh}""")
            assertFalse(policy.refreshDue(project))
            time += expected * 1000 - 1
            assertFalse(policy.refreshDue(project))
            time += 1
            assertTrue(policy.refreshDue(project))
        }
    }

    @Test
    fun `server-info 조회에 실패하면 60초 뒤에 다시 갱신한다`() {
        policy.retryLater(project)
        time += 59_999L
        assertFalse(policy.refreshDue(project))
        time += 1L
        assertTrue(policy.refreshDue(project))
    }

    @Test
    fun `SDK 버전이나 래퍼가 바뀌면 저장 정책을 지우고 같은 revision도 다시 받는다`() {
        val stop = """{"version":1,"revision":5,"collection":{"mode":"stop"},"pushTracking":{"mode":"stop"}}"""
        val flutter = RemotePolicyStore(storage, { time }, sdkType = "flutter")
        flutter.accept(project, parse(stop))
        assertFalse(flutter.allowed(project, "collection"))

        assertTrue(RemotePolicyStore(storage, { time }, sdkType = "react_native", readOnly = true).allowed(project, "pushTracking"))
        assertNotNull(saved())

        val updated = RemotePolicyStore(storage, { time }, sdkType = "react_native")
        assertTrue(updated.allowed(project, "collection"))
        assertNull(saved())
        assertTrue(RemotePolicyStore(storage, { time }, sdkType = "flutter", readOnly = true).allowed(project, "pushTracking"))

        updated.accept(project, parse(stop))
        assertFalse(updated.allowed(project, "collection"))

        storage.setItem("marketap_remote_policy.$project", saved()!!.replace(SdkMetadataProvider.nativeLibraryVersion, "0.0.0-old"), stringAdapter)
        assertTrue(RemotePolicyStore(storage, { time }, sdkType = "react_native").allowed(project, "collection"))
        assertNull(saved())
    }

    @Test
    fun `깨진 큐 항목과 대기 프로필이 있어도 stop 전환은 예외 없이 정리한다`() {
        val prefs = FakeSharedPreferences()
        val real = prefs.storage()
        val adapter = pairAdapter<String, IngestEventRequest>()
        fun event() = IngestEventRequest(null, "event", null, DeviceReq("d"), null)
        prefs.values["events"] = setOf("not json")
        real.queueItem("events", PairEntry(project, event()), adapter)
        real.queueItem("events", PairEntry("other", event()), adapter)
        prefs.values["pending_user_profile"] = "{broken"
        RemotePolicyStore(real, { time }).accept(project, parse("""{"version":1,"revision":1,"collection":{"mode":"stop"}}"""))
        assertEquals(listOf("other"), real.popItems("events", adapter, 10).map { it.first })
        assertNull(prefs.values["pending_user_profile"])
    }

    @Test
    fun `정책은 프로젝트별로 저장되고 재생성 후에도 읽는다`() {
        accept("""{"version":1,"revision":1,"pushTracking":{"mode":"stop"}}""")
        val restarted = RemotePolicyStore(storage, { time })
        assertFalse(restarted.allowed(project, "pushTracking"))
        assertTrue(restarted.allowed("other", "pushTracking"))
    }

    @Test
    fun `stop 전환은 해당 프로젝트의 미전송 이벤트와 사용자 프로필만 삭제하고 디바이스는 남긴다`() {
        val eventAdapter = pairAdapter<String, IngestEventRequest>()
        fun event() = IngestEventRequest(null, "event", null, DeviceReq("d"), null)
        storage.queueItem("events", PairEntry(project, event()), eventAdapter)
        storage.queueItem("events", PairEntry("other", event()), eventAdapter)
        storage.setItem("pending_user_profile", PairEntry(project, UpdateProfileRequest("u", emptyMap(), DeviceReq("d"))), pairAdapter())
        storage.setItem("pending_device_profile", PairEntry(project, DeviceReq("d")), pairAdapter())
        accept("""{"version":1,"revision":1,"collection":{"mode":"stop"}}""")
        assertEquals(listOf("other"), storage.getItemList("events", eventAdapter).map { it.first })
        assertNull(storage.getItem("pending_user_profile", pairAdapter<String, UpdateProfileRequest>()))
        assertNotNull(storage.getItem("pending_device_profile", pairAdapter<String, DeviceReq>()))
    }

    @Test
    fun `구버전 응답과 unknown 필드는 기존 필드 파싱을 방해하지 않는다`() {
        assertNull(readSdkPolicy("""{"data":{"serverTimeOffset":12,"useWebClickRouting":true}}"""))
        assertNull(readSdkPolicy("not json"))
        accept("""{"version":1,"revision":1.5,"collection":{"mode":"stop"}}""")
        assertTrue(allowed())
        assertNull(saved())
    }

    @Test
    fun `UTC 오프셋과 소수초가 있는 만료 시각도 적용한다`() {
        time = 1794387600124L
        accept("""{"version":1,"revision":1,"collection":{"mode":"stop","expiresAt":"2026-11-11T09:00:00.123456+00:00"}}""")
        assertTrue(allowed())
    }

    @Test
    fun `인앱 중지는 모든 사용자 캐시와 체크섬과 조회 시간을 지운다`() {
        val prefs = FakeSharedPreferences()
        val real = prefs.storage()
        listOf("u", "null", "other").forEach { user ->
            listOf("last_campaigns", "campaign_checksum", "campaign_cached_at").forEach {
                prefs.values["$it:$user"] = "cached"
            }
        }
        prefs.values["unrelated"] = "keep"
        RemotePolicyStore(real).accept(project, parse("""{"version":1,"revision":1,"inApp":{"mode":"stop"}}"""))
        assertEquals(mapOf("unrelated" to "keep"), prefs.values.filterKeys { !it.startsWith("marketap_remote_policy.") })
    }

    @Test
    fun `일반 큐 읽기는 깨진 항목을 조용히 버리지 않는다`() {
        val prefs = FakeSharedPreferences()
        val real = prefs.storage()
        prefs.values["events"] = setOf("not json")
        assertThrows(IllegalArgumentException::class.java) {
            real.popItems("events", pairAdapter<String, IngestEventRequest>(), 10)
        }
        assertEquals(setOf("not json"), prefs.values["events"])
    }

}
