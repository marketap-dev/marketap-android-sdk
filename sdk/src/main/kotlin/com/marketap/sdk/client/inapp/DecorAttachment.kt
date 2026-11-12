package com.marketap.sdk.client.inapp

import android.app.Activity
import android.content.MutableContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/**
 * 화면(Activity)의 decor view 에 화면 크기로 붙였다 떼는 View 하나와, 지금 붙은 화면을 추적한다. 메인 스레드 전용.
 * 붙은 동안 [MutableContextWrapper] 의 base 는 그 화면이고, 떼면 application context 로 바꿔 화면을 붙잡지 않는다.
 * @param z 붙일 때 줄 translationZ. 같은 decor 의 다른 SDK View 와의 앞뒤 순서를 정한다.
 * @param onDetach 화면에서 뗄 때마다(옮기기 직전 포함) 불린다.
 */
internal class DecorAttachment<V : View>(
    private val z: Float,
    private val onDetach: () -> Unit = {},
) {
    var view: V? = null
        private set
    private var contextWrapper: MutableContextWrapper? = null
    private var hostRef: WeakReference<Activity>? = null

    /** 붙은 화면이 회전 등으로 파괴돼 다시 만들어지길 기다리는 화면의 클래스 이름. */
    var recreatingClass: String? = null

    val host: Activity? get() = hostRef?.get()

    fun start(view: V, contextWrapper: MutableContextWrapper) {
        this.view = view
        this.contextWrapper = contextWrapper
    }

    fun attachTo(activity: Activity): Boolean {
        val box = view ?: return false
        val decor = activity.window?.decorView as? ViewGroup ?: return false
        detach()
        contextWrapper?.baseContext = activity
        decor.addView(
            box,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        box.bringToFront()
        box.translationZ = z
        hostRef = WeakReference(activity)
        return true
    }

    fun detach() {
        val box = view ?: return
        (box.parent as? ViewGroup)?.removeView(box)
        onDetach()
        hostRef?.get()?.let { contextWrapper?.baseContext = it.applicationContext }
        hostRef = null
    }

    fun clear() {
        detach()
        view = null
        contextWrapper = null
        recreatingClass = null
    }
}
