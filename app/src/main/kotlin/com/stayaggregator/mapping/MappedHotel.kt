package com.stayaggregator.mapping

import java.util.UUID

/**
 * 검색이 매핑에서 읽어 가는 형태. 한 공급사의 숙소 하나와 그 객실 타입들이다.
 *
 * `missing_since` 가 비어 있는 것만 온다. 지금 공급사 목록에 있는 것만 검색 대상이다 (ADR-0037).
 * 이름과 최대 수용 인원은 여기 것을 쓴다. 재고·요금 응답에도 오지만 받지 않는다 (ADR-0012).
 */
data class MappedHotel(
    val internalHotelId: UUID,
    val supplierHotelCode: String,
    val name: String,
    val roomTypes: List<MappedRoomType>,
)

data class MappedRoomType(
    val internalRoomTypeId: UUID,
    val roomTypeCode: String,
    val name: String,
    val maxOccupancy: Int,
)
