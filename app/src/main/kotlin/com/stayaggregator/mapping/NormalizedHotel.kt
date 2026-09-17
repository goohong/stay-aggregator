package com.stayaggregator.mapping

/**
 * 목록 동기화가 판정을 마치고 매핑에 반영해 달라고 넘기는 형태.
 *
 * 값 검증을 생성자가 한다 (ADR-0041). 조건을 어긴 객체는 만들어지지 않으므로,
 * 이 타입을 받는 쪽은 값이 쓸 만한지 다시 보지 않는다.
 * 어긴 값을 어떻게 처리할지는 만드는 쪽이 정한다. 목록 동기화는 그 항목을 빼고 사유를 남긴다 (ADR-0039).
 */
data class NormalizedHotel(
    val code: String,
    val name: String,
    val roomTypes: List<NormalizedRoomType>,
) {
    init {
        require(code.isNotBlank()) { "숙소 코드가 없다" }
        require(name.isNotBlank()) { "숙소명이 없다" }
        // 팔 수 있는 객실 타입이 하나도 없는 숙소는 검색에 걸려도 내놓을 것이 없다 (ADR-0041)
        require(roomTypes.isNotEmpty()) { "팔 수 있는 객실 타입이 없다" }
    }
}

data class NormalizedRoomType(
    val code: String,
    val name: String,
    val maxOccupancy: Int,
) {
    init {
        require(code.isNotBlank()) { "객실 타입 코드가 없다" }
        require(name.isNotBlank()) { "객실 타입명이 없다" }
        // 한 명도 묵을 수 없는 객실 타입은 팔 수 없다. 재고 수를 다룰 때와 같은 기준이다 (ADR-0027, ADR-0039)
        require(maxOccupancy >= 1) { "최대 수용 인원이 1 미만이다" }
    }
}
