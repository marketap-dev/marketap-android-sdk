package com.marketap.sdk.domain.service.inapp

import java.util.Collections
import java.util.WeakHashMap

/**
 * resume된 화면 집합으로 "지금 띄울 수 있는가"([hasResumedScreen])를 판정한다.
 * [isForeground]는 마지막 화면이 멈춘 뒤 [backgroundDebounceMs] 안에 다시 resume되면(회전·화면 전환) 바뀌지 않아,
 * 포그라운드 복귀 알림이 중복되지 않게 하는 데만 쓴다.
 *
 * 생명주기 콜백을 등록하기 전에 이미 resume된 화면은 콜백으로 알 수 없으므로, 콜백을 하나도 받기 전에는
 * [probeResumedScreen]으로 판정한다. [previous]가 있으면(재초기화) 그 상태를 이어받는다.
 * 화면 콜백은 메인 스레드에서 오고, 포그라운드 상태 전환은 [scheduler]에서 처리한다.
 */
internal class AppForegroundTracker(
    private val scheduler: InAppScheduler,
    private val backgroundDebounceMs: Long = 700L,
    private val probeResumedScreen: () -> Any? = { null },
    previous: AppForegroundTracker? = null,
) {
    private val resumed: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
    private val listeners = mutableListOf<() -> Unit>()
    private var pendingBackground: Cancellable? = null

    @Volatile
    private var observedCallback = false

    @Volatile
    var isForeground: Boolean = false
        private set

    init {
        if (previous != null) {
            resumed.addAll(previous.resumedSnapshot())
            observedCallback = previous.observedCallback
            isForeground = resumed.isNotEmpty()
        } else {
            probeResumedScreen()?.let {
                resumed.add(it)
                isForeground = true
            }
        }
    }

    fun addForegroundListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun hasResumedScreen(): Boolean =
        resumed.isNotEmpty() || (!observedCallback && probeResumedScreen() != null)

    fun isResumed(screen: Any): Boolean =
        resumed.contains(screen) || (!observedCallback && probeResumedScreen() === screen)

    fun screenResumed(screen: Any) {
        observedCallback = true
        resumed.add(screen)
        scheduler.execute {
            pendingBackground?.cancel()
            pendingBackground = null
            if (!isForeground) {
                isForeground = true
                synchronized(listeners) { listeners.toList() }.forEach { it() }
            }
        }
    }

    fun screenPaused(screen: Any) {
        observedCallback = true
        resumed.remove(screen)
    }

    fun screenStopped(screen: Any) {
        observedCallback = true
        resumed.remove(screen)
        scheduler.execute {
            if (resumed.isNotEmpty() || !isForeground) return@execute
            pendingBackground?.cancel()
            pendingBackground = scheduler.schedule(backgroundDebounceMs) {
                pendingBackground = null
                if (resumed.isEmpty()) isForeground = false
            }
        }
    }

    private fun resumedSnapshot(): List<Any> = synchronized(resumed) { resumed.toList() }
}
