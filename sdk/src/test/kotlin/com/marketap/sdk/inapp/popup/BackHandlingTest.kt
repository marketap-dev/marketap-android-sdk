package com.marketap.sdk.inapp.popup

import com.marketap.sdk.client.inapp.popup.PopupBackMechanism.KEY_EVENT
import com.marketap.sdk.client.inapp.popup.PopupBackMechanism.ON_BACK_INVOKED
import com.marketap.sdk.client.inapp.popup.PopupBackMechanism.ON_BACK_PRESSED
import com.marketap.sdk.client.inapp.popup.popupBackMechanisms
import org.junit.Assert.assertEquals
import org.junit.Test

class BackHandlingTest {

    @Test
    fun `ComponentActivity 면 API 와 상관없이 OnBackPressedDispatcher 만 쓴다`() {
        assertEquals(setOf(ON_BACK_PRESSED), popupBackMechanisms(isComponentActivity = true, sdkInt = 21))
        assertEquals(setOf(ON_BACK_PRESSED), popupBackMechanisms(isComponentActivity = true, sdkInt = 35))
    }

    @Test
    fun `ComponentActivity 가 아니면 API 33 부터 OnBackInvokedDispatcher 와 키 이벤트를 함께 건다`() {
        assertEquals(setOf(ON_BACK_INVOKED, KEY_EVENT), popupBackMechanisms(isComponentActivity = false, sdkInt = 33))
        assertEquals(setOf(ON_BACK_INVOKED, KEY_EVENT), popupBackMechanisms(isComponentActivity = false, sdkInt = 35))
    }

    @Test
    fun `ComponentActivity 가 아니고 API 32 이하면 키 이벤트만 쓴다`() {
        assertEquals(setOf(KEY_EVENT), popupBackMechanisms(isComponentActivity = false, sdkInt = 32))
        assertEquals(setOf(KEY_EVENT), popupBackMechanisms(isComponentActivity = false, sdkInt = 21))
    }
}
