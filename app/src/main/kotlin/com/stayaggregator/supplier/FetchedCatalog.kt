package com.stayaggregator.supplier

/**
 * 어댑터가 공급사 응답에서 꺼낸 값. 아직 검증하지 않은 상태다 (ADR-0031).
 *
 * 값이 없을 수 있어 모두 null 을 허용한다. 스펙상 반드시 오는 값이라도 마찬가지다.
 * 목록도 그렇다. **없는 목록과 빈 목록을 구분해서 넘긴다.** 공급사가 "없다"고 말한 것과
 * 아무 말도 하지 않은 것은 다른 상황이고, 그 차이에 따라 검증 결과가 다르다 (ADR-0041).
 *
 * 무엇을 쓰고 무엇을 뺄지는 목록 동기화 쪽의 정규화가 ADR-0027 의 질문들로 정한다 (ADR-0030).
 *
 * 어느 공급사가 줬는지는 싣지 않는다. 그것은 어댑터가 알고, consumer 는 어댑터와 이 값을 함께 든다 (ADR-0049).
 */
data class FetchedCatalog(
    /** 없으면 응답을 스펙대로 읽지 못한 것이다 (ADR-0041) */
    val hotels: List<FetchedHotel>?,
)

data class FetchedHotel(
    /** 공급사 안에서 유일한 숙소 코드 */
    val code: String?,
    val name: String?,
    /** 없는 것과 빈 것을 같게 본다. 팔 수 있는 객실 타입이 없는 숙소이므로 그 숙소를 뺀다 (ADR-0041) */
    val roomTypes: List<FetchedRoomType>?,
)

data class FetchedRoomType(
    /** 그 숙소 안에서만 유일한 객실 타입 코드 */
    val code: String?,
    val name: String?,
    /** 객실 1실의 최대 수용 인원. 성인과 아동을 합한 수 */
    val maxOccupancy: Int?,
)
