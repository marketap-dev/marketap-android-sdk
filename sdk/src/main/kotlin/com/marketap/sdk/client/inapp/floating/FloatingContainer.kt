package com.marketap.sdk.client.inapp.floating

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.ViewCompat

/** 접힌 동안 메시지 영역 밖에서 시작한 터치는 false 를 돌려 아래 화면이 받게 한다. */
internal class FloatingContainer(context: Context) : FrameLayout(context) {

    var layout: FloatingLayout = FloatingLayout.INITIAL
    private var capturing = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            capturing = layout.capturesTouch(event.x, event.y)
        }
        if (!capturing) return false
        return super.dispatchTouchEvent(event)
    }
}
