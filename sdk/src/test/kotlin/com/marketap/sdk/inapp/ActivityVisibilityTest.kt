package com.marketap.sdk.inapp

import com.marketap.sdk.client.ActivityVisibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityVisibilityTest {

    private val visibility = ActivityVisibility<String>()

    private fun open(screen: String) {
        visibility.onStarted(screen)
        visibility.onResumed(screen)
    }

    @Test
    fun `다른 화면이 앞에 오면 아래 화면이 가려진 것으로 본다`() {
        open("A")
        visibility.onPaused("A")
        open("B")

        assertTrue(visibility.onStopped("A", isChangingConfigurations = false))
        assertTrue(visibility.isResumed("B"))
        assertFalse(visibility.isResumed("A"))
    }

    @Test
    fun `REG-17 회전으로 멈춘 것은 가려짐이 아니다`() {
        open("A")
        visibility.onPaused("A")
        open("A2")

        assertFalse(visibility.onStopped("A", isChangingConfigurations = true))
    }

    @Test
    fun `백그라운드로 가면 가려짐이 아니다`() {
        open("A")
        visibility.onPaused("A")

        assertFalse(visibility.onStopped("A", isChangingConfigurations = false))
    }

    @Test
    fun `반투명 팝업은 아래 화면을 pause 만 시켜 가려짐이 생기지 않고 resumed 도 아니다`() {
        open("A")
        visibility.onPaused("A")
        open("Popup")

        assertFalse(visibility.isResumed("A"))
        visibility.onPaused("Popup")
        visibility.onDestroyed("Popup")
        visibility.onResumed("A")
        assertTrue(visibility.isResumed("A"))
    }

    @Test
    fun `REG-27 반투명 팝업 아래에서 pause 된 화면도 started 로 본다`() {
        open("A")
        visibility.onPaused("A")
        open("Popup")

        assertTrue(visibility.isStarted("A"))
    }

    @Test
    fun `다른 화면에 덮여 멈춘 화면은 started 가 아니다`() {
        open("A")
        visibility.onPaused("A")
        open("B")
        visibility.onStopped("A", isChangingConfigurations = false)

        assertFalse(visibility.isStarted("A"))
    }
}
