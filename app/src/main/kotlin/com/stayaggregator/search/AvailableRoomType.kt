package com.stayaggregator.search

import java.util.UUID

/**
 * 판정을 마친 검색 결과 항목 하나. 한 공급사의 숙소 하나의 객실 타입 하나다.
 *
 * 응답에 최소한 담아야 하는 것이 여기 있다. 내부 식별자와 이름은 매핑 저장본에서 왔고 (ADR-0012),
 * 예약 가능 객실 수는 날짜별 잔여 수의 최솟값이며 0 이어도 뺀 것이 아니다 (ADR-0023, ADR-0026).
 * 요금은 세금 포함 기간 총액 하나에 판매 조건이 붙은 것이다 (ADR-0042, ADR-0044).
 *
 * 어느 공급사인지는 여기 없다. consumer 가 어댑터와 함께 든다 (ADR-0049).
 */
data class AvailableRoomType(
    val internalHotelId: UUID,
    val hotelName: String,
    val internalRoomTypeId: UUID,
    val roomTypeName: String,
    /** 객실 1실 기준 */
    val maxOccupancy: Int,
    /** 요청 기간 전체에 예약할 수 있는 객실 수. 0 이면 예약 불가 */
    val availableRooms: Int,
    val rate: Rate,
) {
    init {
        require(availableRooms >= 0) { "예약 가능 객실 수가 음수다" }
    }
}
