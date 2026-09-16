package com.stayaggregator.supplier.a

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 공급사 A 의 숙소 목록 응답. 스펙에 있는 필드를 모두 받는다 (ADR-0030).
 *
 * `ignoreUnknown = true` 는 Jackson 3 의 기본 동작이기도 하지만, 기본값에 기대지 않고 여기에 적는다.
 * 공급사가 필드를 더 붙여 보내도 우리 출력값 계산에 쓰이지 않으므로 무시한다 (ADR-0027 의 두 번째 질문).
 *
 * 필드는 모두 null 을 허용한다. 스펙상 반드시 오는 값이라도 마찬가지이고, 값이 쓸 만한지는 정규화 단계가 본다 (ADR-0030).
 * 숙소 코드는 스펙의 응답 예시 표기(`hotelCode`)를 따른다. 설명 표의 대문자 표기는 받지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SupplierAHotelsResponse(
    /** 스펙상 반드시 온다 */
    val items: List<Hotel>?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Hotel(
        /** 스펙상 반드시 온다. 공급사 A 안에서 유일 */
        val hotelCode: String?,
        /** 스펙상 반드시 온다 */
        val hotelName: String?,
        /** 스펙상 반드시 온다. 목록 응답에만 있다 */
        val roomTypes: List<RoomType>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class RoomType(
        /** 스펙상 반드시 온다. 그 숙소 안에서만 유일 */
        val roomTypeCode: String?,
        /** 스펙상 반드시 온다 */
        val roomTypeName: String?,
        /** 스펙상 반드시 온다. 객실 1실의 최대 수용 인원 */
        val maxOccupancy: Int?,
    )
}
