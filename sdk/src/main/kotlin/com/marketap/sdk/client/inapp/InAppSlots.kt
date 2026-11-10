package com.marketap.sdk.client.inapp

import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot

/** 자리별 표시 권리. 반납은 차지한 본인(같은 인스턴스)만 할 수 있어 늦게 온 반납이 다음 메시지를 풀지 않는다. */
internal class InAppSlots<T : Any> {
    private val holders = mutableMapOf<InAppDisplaySlot, T>()

    @Synchronized
    fun claim(slot: InAppDisplaySlot, holder: T): Boolean {
        if (holders.containsKey(slot)) return false
        holders[slot] = holder
        return true
    }

    @Synchronized
    fun release(slot: InAppDisplaySlot, holder: T): Boolean {
        if (holders[slot] !== holder) return false
        holders.remove(slot)
        return true
    }

    @Synchronized
    fun current(slot: InAppDisplaySlot): T? = holders[slot]

    @Synchronized
    fun isOccupied(slot: InAppDisplaySlot): Boolean = holders.containsKey(slot)
}
