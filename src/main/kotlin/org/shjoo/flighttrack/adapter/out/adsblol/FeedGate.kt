package org.shjoo.flighttrack.adapter.out.adsblol

import kotlinx.coroutines.delay

/**
 * 피드 하나로 나가는 호출의 시작 간격을 [spacingMs] 이상으로 벌린다. 피드마다 하나씩 두어
 * 사용자 수와 무관하게 피드별 호출률 상한이 고정된다.
 */
internal class FeedGate(
    private val spacingMs: Long = 1_200,
    private val maxWaitMs: Long = 7_500,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private var nextFreeAt = Long.MIN_VALUE

    /** 다음 슬롯을 예약하고 기다릴 ms 를 돌려준다. 대기가 [maxWaitMs] 를 넘으면 예약 없이 null. */
    @Synchronized
    fun reserve(nowMs: Long): Long? {
        val slot = maxOf(nowMs, nextFreeAt)
        val waitMs = slot - nowMs
        if (waitMs > maxWaitMs) return null
        nextFreeAt = slot + spacingMs
        return waitMs
    }

    /** 슬롯을 기다린 뒤 [call] 을 실행한다. 슬롯을 못 얻으면 호출하지 않고 null. */
    suspend fun <T> paced(call: suspend () -> T): T? {
        val waitMs = reserve(clockMs()) ?: return null
        if (waitMs > 0) delay(waitMs)
        return call()
    }
}
