package com.marketap.sdk.client

import java.util.Collections
import java.util.WeakHashMap

/** 시작된 화면과 resumed 화면을 기록해 "다른 화면이 앞에 와서 가려졌다"를 판정한다. */
internal class ActivityVisibility<T : Any> {
    private val started: MutableSet<T> = Collections.newSetFromMap(WeakHashMap())
    private val resumed: MutableSet<T> = Collections.newSetFromMap(WeakHashMap())

    @Synchronized
    fun onStarted(screen: T) {
        started.add(screen)
    }

    @Synchronized
    fun onResumed(screen: T) {
        started.add(screen)
        resumed.add(screen)
    }

    @Synchronized
    fun onPaused(screen: T) {
        resumed.remove(screen)
    }

    /**
     * 반투명 화면(SDK 팝업·권한 창)은 아래 화면을 stop 시키지 않으므로 여기에 오지 않는다.
     * @return 회전이 아니고 다른 화면이 시작돼 있으면 true. 시작된 화면이 없으면 백그라운드라 false.
     */
    @Synchronized
    fun onStopped(screen: T, isChangingConfigurations: Boolean): Boolean {
        started.remove(screen)
        resumed.remove(screen)
        return !isChangingConfigurations && started.isNotEmpty()
    }

    @Synchronized
    fun onDestroyed(screen: T) {
        started.remove(screen)
        resumed.remove(screen)
    }

    @Synchronized
    fun isResumed(screen: T): Boolean = screen in resumed

    /** 반투명 화면(SDK 팝업) 아래에서 pause 만 된 화면도 포함한다. */
    @Synchronized
    fun isStarted(screen: T): Boolean = screen in started
}
