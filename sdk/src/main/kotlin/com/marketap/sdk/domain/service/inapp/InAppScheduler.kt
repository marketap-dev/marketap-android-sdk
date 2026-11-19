package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.utils.logger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal fun interface Cancellable {
    fun cancel()
}

/** 인앱 판정 상태(예약·횟수)를 건드리는 작업은 모두 여기서 하나씩 실행한다. */
internal interface InAppScheduler {
    fun execute(task: () -> Unit)
    fun schedule(delayMs: Long, task: () -> Unit): Cancellable
}

internal object InlineInAppScheduler : InAppScheduler {
    override fun execute(task: () -> Unit) = task()
    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable =
        throw UnsupportedOperationException("InlineInAppScheduler cannot schedule")
}

internal class SerialInAppScheduler(
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "marketap-inapp").apply { isDaemon = true }
    },
) : InAppScheduler {
    override fun execute(task: () -> Unit) {
        executor.execute { runSafely(task) }
    }

    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        val future = executor.schedule({ runSafely(task) }, delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        return Cancellable { future.cancel(false) }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun runSafely(task: () -> Unit) {
        try {
            task()
        } catch (t: Throwable) {
            logger.e(t) { "In-app task failed" }
        }
    }
}
