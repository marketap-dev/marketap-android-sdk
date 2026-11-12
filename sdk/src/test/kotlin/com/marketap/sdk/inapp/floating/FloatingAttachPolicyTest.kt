package com.marketap.sdk.inapp.floating

import com.marketap.sdk.client.inapp.floating.DestroyAction
import com.marketap.sdk.client.inapp.floating.FloatingAttachPolicy
import com.marketap.sdk.client.inapp.floating.FloatingLayout
import com.marketap.sdk.client.inapp.floating.ResumeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingAttachPolicyTest {

    private fun resumed(
        isHost: Boolean = false,
        recreatingClass: String? = null,
        activityClass: String = "B",
    ) = FloatingAttachPolicy.onResumed(isHost, recreatingClass, activityClass)

    @Test
    fun `붙은 화면이 앞에 오면 그대로 둔다`() {
        assertEquals(ResumeAction.IGNORE, resumed(isHost = true))
        assertEquals(ResumeAction.IGNORE, resumed(isHost = true, recreatingClass = "B"))
    }

    @Test
    fun `다른 화면이 앞에 오면 옮긴다`() {
        assertEquals(ResumeAction.MOVE, resumed())
        assertEquals(ResumeAction.MOVE, resumed(recreatingClass = "A", activityClass = "B"))
    }

    @Test
    fun `회전으로 다시 만들어진 같은 화면에는 상태 그대로 붙인다`() {
        assertEquals(ResumeAction.RESTORE, resumed(recreatingClass = "B", activityClass = "B"))
    }

    @Test
    fun `붙지 않은 화면이 파괴되면 무시한다`() {
        assertEquals(DestroyAction.IGNORE, FloatingAttachPolicy.onDestroyed(isHost = false, isChangingConfigurations = false))
        assertEquals(DestroyAction.IGNORE, FloatingAttachPolicy.onDestroyed(isHost = false, isChangingConfigurations = true))
    }

    @Test
    fun `붙은 화면이 회전으로 파괴되면 떼어 두고 다시 만들어지길 기다린다`() {
        assertEquals(DestroyAction.DETACH_FOR_RECREATE, FloatingAttachPolicy.onDestroyed(isHost = true, isChangingConfigurations = true))
    }

    @Test
    fun `붙은 화면이 사라지면 떼어 두고 다음 화면을 기다린다`() {
        assertEquals(DestroyAction.DETACH_AND_WAIT, FloatingAttachPolicy.onDestroyed(isHost = true, isChangingConfigurations = false))
    }

    @Test
    fun `스냅샷은 접힌 바가 보이는 상태에서 다른 화면으로 옮길 때만 뜬다`() {
        val collapsed = FloatingLayout.parse("collapsed", "0,700,400,760", 400f, 400, 1f)
        val hidden = FloatingLayout.INITIAL

        assertTrue(FloatingAttachPolicy.shouldSnapshot(ResumeAction.MOVE, hadHost = true, expanded = false, layout = collapsed))
        assertFalse(FloatingAttachPolicy.shouldSnapshot(ResumeAction.MOVE, hadHost = true, expanded = true, layout = collapsed))
        assertFalse(FloatingAttachPolicy.shouldSnapshot(ResumeAction.MOVE, hadHost = false, expanded = false, layout = collapsed))
        assertFalse(FloatingAttachPolicy.shouldSnapshot(ResumeAction.MOVE, hadHost = true, expanded = false, layout = hidden))
        assertFalse(FloatingAttachPolicy.shouldSnapshot(ResumeAction.RESTORE, hadHost = true, expanded = false, layout = collapsed))
    }

    @Test
    fun `웹뷰에서 시작된 이 페이지에서만 바는 다른 화면이 앞에 와도 옮기지 않는다`() {
        assertEquals(ResumeAction.IGNORE, FloatingAttachPolicy.onResumed(false, null, "B", stayOnHost = true))
        assertEquals(ResumeAction.RESTORE, FloatingAttachPolicy.onResumed(false, "B", "B", stayOnHost = true))
    }

    @Test
    fun `웹뷰에서 시작된 이 페이지에서만 바는 붙은 화면이 회전 아닌 이유로 파괴되면 닫는다`() {
        assertEquals(DestroyAction.CLOSE, FloatingAttachPolicy.onDestroyed(isHost = true, isChangingConfigurations = false, stayOnHost = true))
        assertEquals(DestroyAction.DETACH_FOR_RECREATE, FloatingAttachPolicy.onDestroyed(isHost = true, isChangingConfigurations = true, stayOnHost = true))
        assertEquals(DestroyAction.IGNORE, FloatingAttachPolicy.onDestroyed(isHost = false, isChangingConfigurations = false, stayOnHost = true))
    }
}
