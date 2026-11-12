package com.marketap.sdk.inapp.popup

import com.marketap.sdk.client.inapp.popup.PopupLifecycle
import com.marketap.sdk.client.inapp.popup.PopupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupStateTest {

    private class Screen(val name: String)

    private class Effects : PopupLifecycle.Effects<Screen> {
        val calls = mutableListOf<String>()
        val waitTimers = mutableListOf<Int>()
        var canAttach = true
        lateinit var lifecycle: PopupLifecycle<Screen>
        var tearDownSync = false

        override fun attach(screen: Screen): Boolean {
            if (!canAttach) return false
            calls.add("attach:${screen.name}")
            return true
        }

        override fun detach() {
            calls.add("detach")
        }

        override fun startWaitTimer(generation: Int) {
            waitTimers.add(generation)
            calls.add("wait")
        }

        override fun requestAnimatedClose() {
            calls.add("animate")
        }

        override fun startCloseWatchdog() {
            calls.add("watchdog")
        }

        override fun tearDown() {
            calls.add("tearDown")
            if (tearDownSync) lifecycle.onTornDown()
        }

        override fun recordImpression() {
            calls.add("impression")
        }

        override fun release() {
            calls.add("release")
        }

        fun count(call: String) = calls.count { it == call }
    }

    private val effects = Effects()
    private val lifecycle = PopupLifecycle(effects).also { effects.lifecycle = it }
    private val a = Screen("A")
    private val b = Screen("B")

    private fun attachedToA() {
        lifecycle.start(a)
        effects.calls.clear()
    }

    @Test
    fun `앞에 나와 있는 화면이 있으면 바로 붙고 없으면 다음 화면을 기다린다`() {
        lifecycle.start(null)
        assertEquals(PopupState.WAITING, lifecycle.state)
        assertEquals(emptyList<String>(), effects.calls)

        lifecycle.onScreenResumed(a, "A")
        assertEquals(PopupState.ATTACHED, lifecycle.state)
        assertEquals(listOf("attach:A"), effects.calls)
    }

    @Test
    fun `붙기 전에 닫기 조건이 오면 붙이지 않고 닫는다`() {
        lifecycle.start(null)
        lifecycle.close()
        lifecycle.onScreenResumed(a, "A")
        lifecycle.onTornDown()

        assertEquals(listOf("tearDown", "release"), effects.calls)
        assertEquals(PopupState.CLOSED, lifecycle.state)
    }

    @Test
    fun `붙은 화면이 파괴돼 기다리는 중에 닫기 조건이 오면 다시 붙지 않고 시한 만료도 무시한다`() {
        attachedToA()
        lifecycle.onScreenDestroyed(a, isChangingConfigurations = false)
        lifecycle.close()
        lifecycle.onTornDown()
        lifecycle.onScreenResumed(b, "B")
        lifecycle.onWaitExpired(effects.waitTimers.single())

        assertEquals(listOf("detach", "wait", "tearDown", "release"), effects.calls)
    }

    @Test
    fun `정리가 바로 끝나도 반납은 한 번이다`() {
        effects.tearDownSync = true
        attachedToA()
        lifecycle.close()
        lifecycle.close()
        lifecycle.onHideSignal()

        assertEquals(listOf("tearDown", "release"), effects.calls)
        assertEquals(PopupState.CLOSED, lifecycle.state)
    }

    @Test
    fun `닫는 중에는 클릭을 받지 않는다`() {
        attachedToA()
        assertTrue(lifecycle.acceptsClicks)

        lifecycle.back()

        assertEquals(PopupState.CLOSING, lifecycle.state)
        assertFalse(lifecycle.acceptsClicks)
        assertEquals(listOf("watchdog", "animate"), effects.calls)
    }

    @Test
    fun `닫는 중에도 정리가 끝나기 전까지는 붙은 채로 숨김 신호를 기다린다`() {
        attachedToA()
        lifecycle.back()
        lifecycle.onScreenResumed(b, "B")
        lifecycle.back()

        assertEquals(listOf("watchdog", "animate"), effects.calls)
    }

    @Test
    fun `숨김 신호와 감시 시한 만료가 겹쳐도 정리와 반납은 한 번이다`() {
        attachedToA()
        lifecycle.back()
        lifecycle.onHideSignal()
        lifecycle.onCloseWatchdog()
        lifecycle.onPageBroken()
        lifecycle.onTornDown()
        lifecycle.onTornDown()

        assertEquals(1, effects.count("tearDown"))
        assertEquals(1, effects.count("release"))
        assertEquals(PopupState.CLOSED, lifecycle.state)
    }

    @Test
    fun `두 뒤로가기 수단이 함께 신호를 보내도 닫기 요청은 한 번이다`() {
        attachedToA()
        lifecycle.back()
        lifecycle.back()

        assertEquals(listOf("watchdog", "animate"), effects.calls)
    }

    @Test
    fun `숨김 신호가 오지 않으면 감시 시한에 정리한다`() {
        attachedToA()
        lifecycle.back()
        lifecycle.onCloseWatchdog()

        assertEquals(listOf("watchdog", "animate", "tearDown"), effects.calls)
    }

    @Test
    fun `닫는 중에 붙은 화면이 파괴되면 바로 정리한다`() {
        attachedToA()
        lifecycle.back()
        lifecycle.onScreenDestroyed(a, isChangingConfigurations = false)

        assertEquals(listOf("watchdog", "animate", "tearDown"), effects.calls)
    }

    @Test
    fun `렌더 프로세스 종료나 첫 로드 실패는 바로 정리한다`() {
        attachedToA()
        lifecycle.onPageBroken()

        assertEquals(listOf("tearDown"), effects.calls)
        assertFalse(lifecycle.acceptsClicks)
    }

    @Test
    fun `붙은 화면이 사라지고 시한 안에 다음 화면이 오지 않으면 닫는다`() {
        attachedToA()
        lifecycle.onScreenDestroyed(a, isChangingConfigurations = false)
        assertEquals(PopupState.WAITING, lifecycle.state)

        lifecycle.onWaitExpired(effects.waitTimers.single())

        assertEquals(listOf("detach", "wait", "tearDown"), effects.calls)
    }

    @Test
    fun `회전으로 다시 만들어진 화면에 시한 안에 다시 붙고 지난 시한은 무시한다`() {
        attachedToA()
        val recreated = Screen("A")
        lifecycle.onScreenDestroyed(a, isChangingConfigurations = true)
        lifecycle.onScreenResumed(recreated, "A")
        lifecycle.onWaitExpired(effects.waitTimers.single())

        assertEquals(PopupState.ATTACHED, lifecycle.state)
        assertEquals(listOf("detach", "wait", "attach:A"), effects.calls)
    }

    @Test
    fun `다른 화면이 앞에 오면 옮겨 붙고 붙은 화면이 다시 앞에 와도 그대로 둔다`() {
        attachedToA()
        lifecycle.onScreenResumed(b, "B")
        lifecycle.onScreenResumed(b, "B")

        assertEquals(PopupState.ATTACHED, lifecycle.state)
        assertEquals(listOf("detach", "attach:B"), effects.calls)
    }

    @Test
    fun `붙어 있지 않은 화면의 파괴는 무시한다`() {
        attachedToA()
        lifecycle.onScreenDestroyed(b, isChangingConfigurations = false)

        assertEquals(PopupState.ATTACHED, lifecycle.state)
        assertEquals(emptyList<String>(), effects.calls)
    }

    @Test
    fun `옮겨 붙지 못하면 기다리며 시한을 건다`() {
        attachedToA()
        effects.canAttach = false
        lifecycle.onScreenResumed(b, "B")

        assertEquals(PopupState.WAITING, lifecycle.state)
        assertEquals(listOf("detach", "wait"), effects.calls)
    }

    @Test
    fun `노출은 붙어 있을 때 문서 로드가 끝나면 한 번 기록한다`() {
        attachedToA()
        lifecycle.onPageFinished()
        lifecycle.onScreenResumed(b, "B")
        lifecycle.onPageFinished()

        assertEquals(1, effects.count("impression"))
    }

    @Test
    fun `닫는 중에 문서 로드가 끝나면 노출을 기록하지 않는다`() {
        attachedToA()
        lifecycle.back()
        lifecycle.onPageFinished()

        assertEquals(0, effects.count("impression"))
    }

    @Test
    fun `화면을 기다리는 동안 문서 로드가 끝나면 다시 붙을 때 노출을 기록한다`() {
        attachedToA()
        lifecycle.onScreenDestroyed(a, isChangingConfigurations = true)
        lifecycle.onPageFinished()
        assertEquals(0, effects.count("impression"))

        lifecycle.onScreenResumed(Screen("A"), "A")

        assertEquals(1, effects.count("impression"))
    }
}
