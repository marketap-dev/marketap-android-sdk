package com.marketap.sdk.client.inapp.floating

internal data class FloatingArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

/**
 * 감시 스크립트가 보낸 메시지 상태.
 * [area] 는 접힌 동안 터치를 받을 영역(뷰 픽셀), [ready] 는 템플릿이 초기화돼 보여도 되는 상태.
 */
internal data class FloatingLayout(val expanded: Boolean, val area: FloatingArea?, val ready: Boolean) {

    fun capturesTouch(x: Float, y: Float): Boolean = expanded || area?.contains(x, y) == true

    companion object {
        val INITIAL = FloatingLayout(expanded = false, area = null, ready = false)

        fun parse(state: String, rect: String, viewportWidth: Float, viewWidth: Int, density: Float): FloatingLayout {
            val scale = if (viewportWidth > 0f && viewWidth > 0) viewWidth / viewportWidth else density
            val parts = rect.split(",").mapNotNull { it.trim().toFloatOrNull() }
            val area = if (parts.size == 4 && parts[2] > parts[0] && parts[3] > parts[1]) {
                FloatingArea(parts[0] * scale, parts[1] * scale, parts[2] * scale, parts[3] * scale)
            } else null
            return FloatingLayout(
                expanded = state == "expanded",
                area = area,
                ready = state.isNotEmpty() || area != null
            )
        }
    }
}
