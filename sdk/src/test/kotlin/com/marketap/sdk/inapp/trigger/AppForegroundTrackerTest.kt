package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.service.inapp.AppForegroundTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForegroundTrackerTest {
    private val scheduler = FakeScheduler()
    private val tracker = AppForegroundTracker(scheduler, backgroundDebounceMs = 700)
    private var foregroundCalls = 0

    init {
        tracker.addForegroundListener { foregroundCalls++ }
    }

    private val screenA = Any()
    private val screenB = Any()

    @Test
    fun `resume 전에는 포그라운드가 아니다`() {
        assertFalse(tracker.isForeground)
    }

    @Test
    fun `resume 되면 포그라운드가 되고 복귀 리스너를 한 번 부른다`() {
        tracker.screenResumed(screenA)
        tracker.screenResumed(screenB)
        assertTrue(tracker.isForeground)
        assertEquals(1, foregroundCalls)
    }

    @Test
    fun `마지막 화면이 멈추고 디바운스가 지나면 백그라운드`() {
        tracker.screenResumed(screenA)
        tracker.screenPaused(screenA)
        tracker.screenStopped(screenA)
        scheduler.advanceBy(699)
        assertTrue(tracker.isForeground)
        scheduler.advanceBy(1)
        assertFalse(tracker.isForeground)
    }

    @Test
    fun `화면 회전·전환 중 디바운스 안에 resume 되면 포그라운드 유지, 리스너 재호출 없음`() {
        tracker.screenResumed(screenA)
        tracker.screenPaused(screenA)
        tracker.screenStopped(screenA)
        scheduler.advanceBy(300)
        tracker.screenResumed(screenB)
        scheduler.advanceBy(1000)
        assertTrue(tracker.isForeground)
        assertEquals(1, foregroundCalls)
    }

    @Test
    fun `다른 화면이 resume 된 채로 한 화면만 멈추면 포그라운드 유지`() {
        tracker.screenResumed(screenA)
        tracker.screenResumed(screenB)
        tracker.screenStopped(screenA)
        scheduler.advanceBy(1000)
        assertTrue(tracker.isForeground)
    }

    @Test
    fun `백그라운드 뒤 다시 resume 되면 복귀 리스너를 다시 부른다`() {
        tracker.screenResumed(screenA)
        tracker.screenStopped(screenA)
        scheduler.advanceBy(700)
        tracker.screenResumed(screenA)
        assertTrue(tracker.isForeground)
        assertEquals(2, foregroundCalls)
    }

    @Test
    fun `resume 여부를 화면별로 알려준다`() {
        tracker.screenResumed(screenA)
        assertTrue(tracker.isResumed(screenA))
        tracker.screenPaused(screenA)
        assertFalse(tracker.isResumed(screenA))
    }

    @Test
    fun `콜백 등록 전에 resume 된 화면이 있으면 처음부터 띄울 수 있다`() {
        val seeded = AppForegroundTracker(scheduler, probeResumedScreen = { screenA })
        assertTrue(seeded.isForeground)
        assertTrue(seeded.hasResumedScreen())
        assertTrue(seeded.isResumed(screenA))
        seeded.screenPaused(screenA)
        assertFalse(seeded.hasResumedScreen())
    }

    @Test
    fun `콜백을 받기 전에는 래퍼가 나중에 넘긴 화면도 본다`() {
        var probed: Any? = null
        val lazy = AppForegroundTracker(scheduler, probeResumedScreen = { probed })
        assertFalse(lazy.hasResumedScreen())
        probed = screenA
        assertTrue(lazy.hasResumedScreen())
        lazy.screenResumed(screenB)
        lazy.screenPaused(screenB)
        assertFalse(lazy.hasResumedScreen())
    }

    @Test
    fun `재초기화하면 이전 tracker 의 resume 상태를 이어받는다`() {
        tracker.screenResumed(screenA)
        val next = AppForegroundTracker(FakeScheduler(), previous = tracker)
        assertTrue(next.isForeground)
        assertTrue(next.hasResumedScreen())
        assertTrue(next.isResumed(screenA))
    }

    @Test
    fun `디바운스 중에는 포그라운드로 보지만 띄울 화면은 없다`() {
        tracker.screenResumed(screenA)
        tracker.screenPaused(screenA)
        tracker.screenStopped(screenA)
        assertTrue(tracker.isForeground)
        assertFalse(tracker.hasResumedScreen())
    }
}
