package com.marketap.sdk.client.inapp.floating

/**
 * "유지" 화면 전환용 바 스냅샷 관리.
 * 떠나는 화면의 스냅샷은 그 화면이 다시 앞에 오거나 파괴될 때까지 남기고, 가장 최근 1장만 둔다.
 * 들어오는 화면의 스냅샷은 웹뷰가 그 화면에서 처음 그려질 때까지 둔다.
 */
internal class FloatingGhostLedger<K : Any, G : Any>(private val remove: (G) -> Unit) {

    private var leaving: Pair<K, G>? = null
    private var arriving: Pair<K, G>? = null

    fun putLeaving(screen: K, ghost: G) {
        leaving?.let { remove(it.second) }
        leaving = screen to ghost
    }

    fun putArriving(screen: K, ghost: G) {
        arriving?.let { remove(it.second) }
        arriving = screen to ghost
    }

    fun onFirstDraw(screen: K) {
        val current = arriving ?: return
        if (current.first !== screen) return
        remove(current.second)
        arriving = null
    }

    fun onScreenFront(screen: K) {
        val current = leaving ?: return
        if (current.first !== screen) return
        remove(current.second)
        leaving = null
    }

    fun onScreenGone(screen: K) {
        onScreenFront(screen)
        onFirstDraw(screen)
    }

    fun clear() {
        leaving?.let { remove(it.second) }
        arriving?.let { remove(it.second) }
        leaving = null
        arriving = null
    }

    val leavingScreen: K? get() = leaving?.first
    val arrivingScreen: K? get() = arriving?.first
}
