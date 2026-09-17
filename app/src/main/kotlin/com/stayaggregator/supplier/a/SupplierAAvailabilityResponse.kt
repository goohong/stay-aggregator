package com.stayaggregator.supplier.a

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

/**
 * 공급사 A 의 재고·요금 응답. 스펙에 있는 필드를 모두 받는다 (ADR-0030).
 *
 * 항목 하나가 숙소 하나의 객실 타입 하나다. 요금은 날짜마다 1박 금액과 세금액이 따로 오고 재고도 같은 자리에 온다.
 * 규칙은 [SupplierAHotelsResponse] 와 같다. 모르는 필드는 무시하고, 값은 모두 null 을 허용하며, 쓸 만한지는 판정 단계가 본다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SupplierAAvailabilityResponse(
    /** 스펙상 반드시 온다 */
    val items: List<Item>?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val hotelCode: String?,
        /** 응답에는 매핑 저장본의 이름을 쓴다 (ADR-0012). 이것은 목록과 비교하는 데만 쓴다 (ADR-0062) */
        val hotelName: String?,
        val roomTypeCode: String?,
        /** 위와 같다 */
        val roomTypeName: String?,
        /** 위와 같다 */
        val maxOccupancy: Int?,
        val breakfastIncluded: Boolean?,
        /** ISO 4217 코드 */
        val currency: String?,
        /** 체크인일부터 체크아웃 전날까지 하루 하나 */
        val dailyRates: List<DailyRate>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DailyRate(
        val date: LocalDate?,
        val remainingRooms: Int?,
        /** 세금 제외 1박 금액. 통화의 최소 단위 정수 */
        val nightlyRate: Long?,
        /** 그날의 세금액 */
        val taxAmount: Long?,
    )
}
