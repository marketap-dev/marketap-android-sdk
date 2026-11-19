package com.marketap.sdk.domain.service.inapp

import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.service.inapp.condition.ConditionChecker
import com.marketap.sdk.domain.service.inapp.condition.matchesAbort
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import com.marketap.sdk.model.internal.slot
import com.marketap.sdk.utils.listAdapter
import com.marketap.sdk.utils.logger
import com.squareup.moshi.JsonClass
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * 지연 노출 예약. 캠페인 본문은 저장하지 않고 실행할 때 최신 목록에서 찾는다.
 * [abortConditions]는 앱을 다시 켠 직후 목록을 받기 전에도 취소 이벤트를 판정하려고 함께 저장한다.
 */
@JsonClass(generateAdapter = true)
internal data class DelayedReservation(
    val reservationId: String,
    val campaignId: String,
    val slot: InAppDisplaySlot,
    val triggerEvent: IngestEventRequest,
    val abortConditions: List<Condition>? = null,
    val dueAt: Long,
    val expireAt: Long,
)

internal enum class DelayedDelivery {
    /** 띄웠거나 버렸다. 예약을 지운다. */
    DONE,

    /** 지금은 판정할 수 없다. 만료 전까지 예약을 남긴다. */
    RETRY,
}

internal fun InAppCampaign.delayMillis(): Long? =
    triggerEventCondition.delaySeconds?.takeIf { it > 0 }?.let { it * 1000L }

/**
 * [clearPersisted]를 뺀 모든 메서드는 [scheduler] 안에서 불러야 한다.
 * 예약을 만든 이벤트의 출처 웹뷰는 메모리에만 약하게 들고 있다가 실행기에 넘긴다. 저장소에서 복원한 예약에는 출처가 없다.
 * 예약 시각이 되면 resume된 화면이 있고([canDisplay]) 같은 자리가 비어 있을 때만 실행기에 넘긴다.
 * 화면이 없으면 남겨 둔다. 앱이 아직 포그라운드로 판정되는 중(화면 전환·회전)이면 [transitionRetryMs] 뒤 다시 보고,
 * 백그라운드면 [sync](포그라운드 복귀·초기화)에서 다시 본다.
 */
internal class DelayedInAppCoordinator(
    private val storage: InternalStorage,
    private val scheduler: InAppScheduler,
    private val conditionChecker: ConditionChecker,
    private val canDisplay: () -> Boolean,
    private val isForeground: () -> Boolean,
    private val isSlotShowing: (InAppDisplaySlot) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val retryDelayMs: Long = 30_000L,
    private val transitionRetryMs: Long = 500L,
) {
    private val adapter = listAdapter<DelayedReservation>()
    private val timers = mutableMapOf<String, Cancellable>()
    private val origins = mutableMapOf<String, WeakReference<InAppOrigin>>()
    private var executor: ((DelayedReservation, InAppOrigin?) -> DelayedDelivery)? = null

    fun bindExecutor(executor: (DelayedReservation, InAppOrigin?) -> DelayedDelivery) {
        this.executor = executor
    }

    fun isScheduled(campaignId: String): Boolean = read().any { it.campaignId == campaignId }

    fun schedule(campaign: InAppCampaign, triggerEvent: IngestEventRequest, origin: InAppOrigin? = null): Boolean {
        val delayMs = campaign.delayMillis() ?: return false
        val reservations = read()
        if (reservations.any { it.campaignId == campaign.id }) return false
        val dueAt = now() + delayMs
        val reservation = DelayedReservation(
            reservationId = newId(),
            campaignId = campaign.id,
            slot = campaign.slot,
            triggerEvent = triggerEvent,
            abortConditions = campaign.triggerEventCondition.abortConditions,
            dueAt = dueAt,
            expireAt = dueAt + RESERVATION_TTL_MS,
        )
        write(reservations + reservation)
        origin?.let { origins[reservation.reservationId] = WeakReference(it) }
        arm(reservation, delayMs)
        return true
    }

    fun cancelMatching(event: IngestEventRequest) {
        val reservations = read()
        val (aborted, kept) = reservations.partition { conditionChecker.matchesAbort(it.abortConditions, event) }
        if (aborted.isEmpty()) return
        aborted.forEach { forget(it.reservationId) }
        write(kept)
    }

    fun clearAll() {
        timers.values.forEach { it.cancel() }
        timers.clear()
        origins.clear()
        clearPersisted()
    }

    fun clearPersisted() {
        storage.removeItem(STORAGE_KEY)
    }

    fun sync() {
        val reservations = read()
        val ids = reservations.map { it.reservationId }.toSet()
        (timers.keys + origins.keys).filter { it !in ids }.forEach(::forget)
        reservations.forEach { reservation ->
            if (reservation.dueAt <= now()) {
                tryDeliver(reservation.reservationId)
            } else {
                arm(reservation, reservation.dueAt - now())
            }
        }
    }

    private fun tryDeliver(reservationId: String) {
        val executor = executor ?: return
        cancelTimer(reservationId)
        val reservation = read().firstOrNull { it.reservationId == reservationId } ?: return
        if (reservation.dueAt > now()) {
            arm(reservation, reservation.dueAt - now())
            return
        }
        if (!canDisplay()) {
            if (isForeground()) arm(reservation, transitionRetryMs)
            return
        }
        if (isSlotShowing(reservation.slot)) {
            remove(reservationId)
            return
        }
        val result = try {
            executor(reservation, origins[reservationId]?.get())
        } catch (t: Throwable) {
            logger.e(t) { "Failed to execute delayed in-app campaign: ${reservation.campaignId}" }
            DelayedDelivery.DONE
        }
        when (result) {
            DelayedDelivery.DONE -> remove(reservationId)
            DelayedDelivery.RETRY -> arm(reservation, retryDelayMs)
        }
    }

    private fun arm(reservation: DelayedReservation, delayMs: Long) {
        if (timers.containsKey(reservation.reservationId)) return
        val id = reservation.reservationId
        timers[id] = scheduler.schedule(delayMs.coerceAtLeast(0)) {
            timers.remove(id)
            tryDeliver(id)
        }
    }

    private fun cancelTimer(reservationId: String) {
        timers.remove(reservationId)?.cancel()
    }

    private fun forget(reservationId: String) {
        cancelTimer(reservationId)
        origins.remove(reservationId)
    }

    private fun remove(reservationId: String) {
        forget(reservationId)
        write(read().filter { it.reservationId != reservationId })
    }

    private fun read(): List<DelayedReservation> {
        val stored = try {
            storage.getItem(STORAGE_KEY, adapter).orEmpty()
        } catch (e: Exception) {
            storage.removeItem(STORAGE_KEY)
            emptyList()
        }
        val currentTime = now()
        val (active, expired) = stored.partition { it.expireAt > currentTime }
        if (expired.isNotEmpty()) {
            expired.forEach { forget(it.reservationId) }
            write(active)
        }
        return active
    }

    private fun write(reservations: List<DelayedReservation>) {
        if (reservations.isEmpty()) {
            storage.removeItem(STORAGE_KEY)
        } else {
            storage.setItem(STORAGE_KEY, reservations, adapter)
        }
    }

    private companion object {
        const val STORAGE_KEY = "delayed_in_app_reservations"
        const val RESERVATION_TTL_MS = 10 * 60 * 1000L
    }
}
