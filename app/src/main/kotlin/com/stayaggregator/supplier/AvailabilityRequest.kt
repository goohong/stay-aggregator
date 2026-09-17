package com.stayaggregator.supplier

import com.stayaggregator.domain.StayPeriod
import com.stayaggregator.domain.GuestCount
/**
 * 재고·요금 조회 한 번에 넘기는 것. 숙소 코드 목록(chunk), 숙박 구간, 인원이다 (ADR-0047).
 *
 * 숙소 코드는 50개 이하여야 한다. 공급사가 넘으면 오류로 거절하기 때문에, 나누는 일은 호출하는 쪽이 하고
 * 여기서는 그 상한을 어기지 않았는지만 지킨다 (ADR-0045).
 */
data class AvailabilityRequest(
    val hotelCodes: List<String>,
    val period: StayPeriod,
    val guests: GuestCount,
) {
    init {
        require(hotelCodes.isNotEmpty()) { "숙소 코드가 없다" }
        require(hotelCodes.size <= MAX_HOTEL_CODES) { "숙소 코드가 ${MAX_HOTEL_CODES}개를 넘는다: ${hotelCodes.size}" }
    }

    companion object {
        /** 두 공급사 모두 이 수를 넘으면 거절한다. 우리 기준이 아니라 공급사의 제약이다 (ADR-0045) */
        const val MAX_HOTEL_CODES = 50
    }
}
