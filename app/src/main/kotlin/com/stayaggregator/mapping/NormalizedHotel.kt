package com.stayaggregator.mapping

/**
 * 목록 동기화가 판정을 마치고 매핑에 반영해 달라고 넘기는 형태.
 *
 * 공급사 응답에서 값이 비어 있거나 정할 수 없던 항목은 여기까지 오지 않는다 (ADR-0027, ADR-0030).
 * 그래서 null 을 허용하지 않는다.
 */
data class NormalizedHotel(
    val code: String,
    val name: String,
    val roomTypes: List<NormalizedRoomType>,
)

data class NormalizedRoomType(
    val code: String,
    val name: String,
    val maxOccupancy: Int,
)
