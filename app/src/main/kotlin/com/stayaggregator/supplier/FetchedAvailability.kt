package com.stayaggregator.supplier

import java.time.LocalDate

/**
 * 어댑터가 재고·요금 응답에서 꺼낸 값. 아직 검증하지 않은 상태다 (ADR-0030, ADR-0031).
 *
 * [FetchedCatalog] 와 같은 규칙이다. 값이 없을 수 있어 모두 null 을 허용하고, 없는 목록과 빈 목록을 구분해서 넘기며,
 * 어느 공급사가 줬는지는 싣지 않는다 (ADR-0049).
 *
 * **요금은 공급사가 준 형식 그대로 둘 중 하나로 온다.** 날짜별 금액을 주는 공급사와 총액을 주는 공급사가 있고,
 * 날짜별 금액을 총액으로 합치려면 요청한 날짜가 다 왔는지 먼저 봐야 한다. 그 검증은 공급사 공통 규칙이라 어댑터 밖에서 하므로
 * (ADR-0031, ADR-0027 의 요금 표), 어댑터는 더하지 않고 받은 대로 넘긴다. 정규화를 마친 요금은 `domain` 의 `Rate` 부터 같은 형식이다 (ADR-0042).
 */
data class FetchedAvailability(
    /** 없으면 응답을 스펙대로 읽지 못한 것이다 (ADR-0041) */
    val items: List<FetchedAvailabilityItem>?,
)

/** 항목 하나는 숙소 하나의 객실 타입 하나다. 응답의 이름과 최대 수용 인원은 매핑 저장본 것을 쓴다 (ADR-0012). 이름은 목록과 비교하려고만 받는다 (ADR-0062) */
data class FetchedAvailabilityItem(
    val hotelCode: String?,
    val roomTypeCode: String?,
    /** 응답에는 쓰지 않는다(ADR-0012). 목록 이름과 달라졌는지 비교하는 데만 쓴다 (ADR-0062) */
    val hotelName: String? = null,
    /** 위와 같다 */
    val roomTypeName: String? = null,
    val breakfastIncluded: Boolean?,
    val currency: String?,
    val pricing: FetchedPricing?,
    /** 날짜별 잔여 객실 수 */
    val dailyInventory: List<FetchedDailyInventory>?,
)

/** 공급사가 준 요금의 형식. 어느 쪽인지는 공급사가 정하고, 하나의 총액으로 만드는 일은 정규화가 한다 */
sealed interface FetchedPricing {
    /** 날짜마다 1박 금액과 세금액이 따로 온다. 총액은 요청한 날짜가 다 있을 때만 만들 수 있다 */
    data class Daily(val rates: List<FetchedDailyRate>?) : FetchedPricing

    /** 기간 총액 하나가 온다. 세금이 포함됐는지는 `taxIncluded` 가 말하고, 세금액 자체는 없다 */
    data class Total(val totalPrice: Long?, val taxIncluded: Boolean?) : FetchedPricing
}

data class FetchedDailyRate(
    val date: LocalDate?,
    val nightlyRate: Long?,
    val taxAmount: Long?,
)

data class FetchedDailyInventory(
    val date: LocalDate?,
    val remainingRooms: Int?,
)
