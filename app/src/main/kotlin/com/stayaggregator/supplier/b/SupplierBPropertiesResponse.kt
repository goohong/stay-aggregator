package com.stayaggregator.supplier.b

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * 공급사 B 의 숙소 목록 응답. 스펙에 있는 필드를 모두 받는다 (ADR-0030).
 *
 * B 는 장애 상황에서도 HTTP 200 을 주고 본문의 `resultCode` 로만 실패를 알린다.
 * 그래서 이 값을 보지 않으면 장애를 정상 응답으로 처리하게 된다.
 *
 * `ignoreUnknown = true` 는 Jackson 3 의 기본 동작이기도 하지만 기본값에 기대지 않고 여기에 적는다 (ADR-0030).
 * 필드는 모두 null 을 허용하고, 값이 유효한지는 정규화 단계가 본다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SupplierBPropertiesResponse(
    /** 스펙상 반드시 온다. `0000` 이 성공 */
    val resultCode: String?,
    /** 스펙상 반드시 온다 */
    val resultMessage: String?,
    /** 성공일 때만 값이 있고 실패하면 null 이다 */
    val data: Data?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Data(
        /** 스펙상 반드시 온다 */
        val items: List<Property>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Property(
        /** 스펙상 반드시 온다. 공급사 B 안에서 유일 */
        val propertyId: String?,
        /** 스펙상 반드시 온다 */
        val propertyName: String?,
        /** 스펙상 반드시 온다. 목록 응답에만 있다 */
        val rooms: List<Room>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Room(
        /** 스펙상 반드시 온다. 이름과 달리 물리 객실이 아니라 객실 타입이고, 그 숙소 안에서만 유일 */
        val roomId: String?,
        /** 스펙상 반드시 온다 */
        val roomName: String?,
        /** 스펙상 반드시 온다. 객실 1실의 최대 수용 인원 */
        val maxOccupancy: Int?,
    )
}
