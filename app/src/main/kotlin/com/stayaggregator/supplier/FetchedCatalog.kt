package com.stayaggregator.supplier

/**
 * 어댑터가 공급사 응답에서 꺼낸 값. 아직 판정하지 않은 상태다 (ADR-0031).
 *
 * 값이 없을 수 있어 모두 null 을 허용한다. 스펙상 반드시 오는 값이라도 마찬가지다.
 * 무엇을 쓰고 무엇을 뺄지는 목록 동기화 쪽의 정규화가 ADR-0027 의 두 번째·세 번째 질문으로 정한다 (ADR-0030).
 */
data class FetchedCatalog(
    val supplierId: String,
    val hotels: List<FetchedHotel>,
)

data class FetchedHotel(
    /** 공급사 안에서 유일한 숙소 코드 */
    val code: String?,
    val name: String?,
    val roomTypes: List<FetchedRoomType>,
)

data class FetchedRoomType(
    /** 그 숙소 안에서만 유일한 객실 타입 코드 */
    val code: String?,
    val name: String?,
    /** 객실 1실의 최대 수용 인원. 성인과 아동을 합한 수 */
    val maxOccupancy: Int?,
)
