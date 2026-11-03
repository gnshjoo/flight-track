package org.shjoo.flighttrack.adapter.out.adsblol

import org.shjoo.flighttrack.domain.model.BoundingBox
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

internal const val MAX_TILES = 6

// 칸은 위도 5°(반높이 150 nm), 폭 최대 400 nm(반폭 200 nm)라 중심에서 모서리까지 약 250 nm 이다.
// 그래서 칸 중심의 250 nm 원 하나가 칸 전체를 덮는다.
private const val ROW_DEG = 5
private const val MAX_CELL_WIDTH_NM = 400.0
private const val NM_PER_DEG = 60.0

// 75° 너머 띠는 칸이 250 nm 원에 들어가지 않을 만큼 좁아지고 교통도 거의 없어 조회하지 않는다
private val ROWS = -15..14

/** 전 지구 고정 격자의 한 칸. 행 r 은 위도 [5r, 5r+5) 띠. 같은 화면이면 사용자와 무관하게 같은 칸이 나와 캐시 키로 쓴다. */
internal data class TileId(val row: Int, val col: Int) {
    fun circle(): Circle {
        val step = colStepDeg(row)
        return Circle(lat = row * ROW_DEG + ROW_DEG / 2.0, lon = -180.0 + (col + 0.5) * step, radiusNm = MAX_RADIUS_NM)
    }
}

internal data class TileSet(val tiles: List<TileId>, val partial: Boolean)

/** 행의 경도 간격(정수 도). 적도 쪽 가장자리에서도 폭이 400 nm 이하이고 360 을 나누어 떨어지게 고른다. */
internal fun colStepDeg(row: Int): Int {
    val equatorwardLat = if (row >= 0) row * ROW_DEG else (row + 1) * ROW_DEG
    val maxStep = MAX_CELL_WIDTH_NM / (NM_PER_DEG * cos(Math.toRadians(abs(equatorwardLat).toDouble())))
    return (maxStep.toInt() downTo 1).first { 360 % it == 0 }
}

internal fun tileAt(lat: Double, lon: Double): TileId {
    val row = floor(lat / ROW_DEG).toInt().coerceIn(ROWS)
    val step = colStepDeg(row)
    return TileId(row, floor((lon + 180) / step).toInt().coerceAtMost(360 / step - 1))
}

/** [box] 와 겹치는 칸을 박스 중심에 가까운 순으로 [MAX_TILES] 개까지. 잘렸거나 극지 띠를 건너뛰면 partial. */
internal fun tilesFor(box: BoundingBox): TileSet {
    val rowMin = floor(box.lamin / ROW_DEG).toInt()
    val rowMax = maxOf(rowMin, ceil(box.lamax / ROW_DEG).toInt() - 1)
    val rows = (rowMin..rowMax).filter { it in ROWS }
    val cells = rows.flatMap { row ->
        val step = colStepDeg(row)
        val lastCol = 360 / step - 1
        val colMin = floor((box.lomin + 180) / step).toInt().coerceAtMost(lastCol)
        val colMax = maxOf(colMin, ceil((box.lomax + 180) / step).toInt() - 1).coerceAtMost(lastCol)
        (colMin..colMax).map { TileId(row, it) }
    }
    val centerLat = (box.lamin + box.lamax) / 2
    val centerLon = (box.lomin + box.lomax) / 2
    val nearestFirst = cells.sortedBy { it.circle().let { c -> haversineNm(centerLat, centerLon, c.lat, c.lon) } }
    return TileSet(
        tiles = nearestFirst.take(MAX_TILES),
        partial = cells.size > MAX_TILES || rows.size < rowMax - rowMin + 1,
    )
}
