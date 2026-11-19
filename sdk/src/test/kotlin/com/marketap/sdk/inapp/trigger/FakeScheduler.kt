package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.service.inapp.Cancellable
import com.marketap.sdk.domain.service.inapp.InAppScheduler

/** 작업은 바로 실행하고, 지연 작업은 [advanceBy]로 가상 시간을 움직일 때 실행한다. */
internal class FakeScheduler(var now: Long = 1_000_000L) : InAppScheduler {
    private class Timer(val at: Long, val seq: Long, val task: () -> Unit) {
        var cancelled = false
    }

    private val timers = mutableListOf<Timer>()
    private var seq = 0L

    val pendingCount: Int get() = timers.count { !it.cancelled }

    var paused = false
    private val queued = ArrayDeque<() -> Unit>()

    override fun execute(task: () -> Unit) {
        if (paused) queued.addLast(task) else task()
    }

    fun runQueued() {
        paused = false
        while (queued.isNotEmpty()) queued.removeFirst()()
    }

    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        val timer = Timer(now + delayMs.coerceAtLeast(0), seq++, task)
        timers.add(timer)
        return Cancellable { timer.cancelled = true }
    }

    fun cancelAll() = timers.clear()

    fun advanceBy(ms: Long) {
        val target = now + ms
        while (true) {
            val next = timers.filter { !it.cancelled && it.at <= target }.minWithOrNull(compareBy({ it.at }, { it.seq })) ?: break
            timers.remove(next)
            now = maxOf(now, next.at)
            next.task()
        }
        now = target
    }
}
