package com.marketap.sdk.client.inapp.popup

import android.view.View
import java.util.IdentityHashMap

/**
 * 팝업 아래 View 들의 importantForAccessibility 를 [hidden] 으로 바꾸고 화면별로 원래 값을 기억한다.
 * 되돌릴 때는 값이 아직 [hidden] 일 때만 되돌린다(그사이 앱이 바꾼 값은 둔다). 원래 [hidden] 이던 View 는 건드리지 않는다.
 */
internal class AccessibilityLedger<H : Any, V : Any>(
    private val read: (V) -> Int,
    private val write: (V, Int) -> Unit,
    private val hidden: Int = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
) {
    private val saved = IdentityHashMap<H, IdentityHashMap<V, Int>>()

    fun hide(host: H, view: V) {
        val views = saved.getOrPut(host) { IdentityHashMap() }
        if (views.containsKey(view)) return
        val original = read(view)
        if (original == hidden) return
        views[view] = original
        write(view, hidden)
    }

    fun restore(host: H) {
        val views = saved.remove(host) ?: return
        views.forEach { (view, original) -> if (read(view) == hidden) write(view, original) }
    }

    fun restoreAll() {
        saved.keys.toList().forEach(::restore)
    }
}
