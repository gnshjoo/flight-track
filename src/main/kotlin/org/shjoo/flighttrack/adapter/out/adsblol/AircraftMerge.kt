package org.shjoo.flighttrack.adapter.out.adsblol

import org.shjoo.flighttrack.domain.model.AircraftState

/**
 * icao24 기준 합집합. 같은 기체는 seenAt 이 가장 늦은 기록을 남기고 seenAt 이 없으면 가장 오래된 것으로 본다.
 * 같으면 앞 목록을 남기므로 adsb.lol 을 먼저 넘긴다. 피드 병합과 겹치는 타일 병합에 함께 쓴다.
 */
internal fun mergeFreshest(lists: List<List<AircraftState>>): List<AircraftState> {
    val byIcao = LinkedHashMap<String, AircraftState>()
    for (state in lists.flatten()) {
        val kept = byIcao[state.icao24]
        if (kept == null || (state.seenAt ?: Double.NEGATIVE_INFINITY) > (kept.seenAt ?: Double.NEGATIVE_INFINITY)) {
            byIcao[state.icao24] = state
        }
    }
    return byIcao.values.toList()
}
