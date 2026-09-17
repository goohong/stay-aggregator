package com.stayaggregator.search

/**
 * 요금. 금액과 판매 조건을 함께 가진 **팔리는 단위**다. 금액 자체가 아니다 (ADR-0044).
 *
 * 금액은 세금 포함 기간 전체 총액 하나다 (ADR-0042). 1박 얼마로 보여줄지는 보여주는 쪽이 총액과 박수로 계산한다.
 * 층은 여기까지다. 금액 · 판매 조건 · 그 둘을 가진 요금. 위에 하나를 더 두지 않는다 (ADR-0044).
 */
data class Rate(val total: Money, val conditions: RateConditions)

/**
 * 판매 조건. 그 요금으로 살 때 따라오는 것 (ADR-0044).
 *
 * 지금 담는 것은 조식 포함 여부 하나다. 조건이 늘면 여기에 더하고 금액·요금 타입은 열지 않는다.
 * 취소 조건은 공급사가 주지 않아 없다.
 */
data class RateConditions(val breakfastIncluded: Boolean)
