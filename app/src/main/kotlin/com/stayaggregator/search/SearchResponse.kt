package com.stayaggregator.search

import java.util.UUID

/**
 * 검색 API 의 응답. 요구된 최소 정보에 공급사별 상태와 건수를 더했다 (ADR-0046, ADR-0050).
 *
 * 내부 결과 객체를 그대로 내보내지 않고 여기서 구조를 고정한다. 내부 타입이 바뀌어도 응답 계약이 따라 바뀌지 않게 하려는 것이다.
 * 요금은 세금 포함 기간 총액 하나이고, 1박 얼마는 받는 쪽이 총액과 박수로 계산한다 (ADR-0042).
 */
data class SearchResponse(
    val checkIn: String,
    val checkOut: String,
    val nights: Int,
    val adults: Int,
    val children: Int,
    val suppliers: List<SupplierStatusResponse>,
    val roomTypes: List<RoomTypeResponse>,
) {
    data class SupplierStatusResponse(
        val supplier: String,
        /** `SUCCEEDED` 또는 `FAILED`. 실패는 이 공급사 결과를 아예 만들 수 없었다는 뜻이다 */
        val status: SupplierStatus,
        /** 실패했을 때만 값이 있다 */
        val failureReason: String?,
        /** 이 공급사에서 내보낸 객실 타입 수 */
        val roomTypeCount: Int,
        /** 스펙과 달라 응답에서 뺀 객실 타입 수 */
        val outOfSpecCount: Int,
        /** 호출하지 못한 chunk 수. 0 이 아니면 이 공급사의 일부 숙소는 이 응답에 없다 */
        val failedChunks: Int,
    )

    data class RoomTypeResponse(
        val hotelId: UUID,
        val hotelName: String,
        val roomTypeId: UUID,
        val roomTypeName: String,
        val maxOccupancy: Int,
        val supplier: String,
        /** 요청 기간 전체에 예약할 수 있는 객실 수. 0 이면 예약 불가 */
        val availableRooms: Int,
        val rate: RateResponse,
    )

    data class RateResponse(
        /** 세금 포함 기간 전체 총액. 통화의 최소 단위 정수 */
        val totalAmount: Long,
        /** ISO 4217 */
        val currency: String,
        val breakfastIncluded: Boolean,
    )

    companion object {
        fun from(result: SearchResult, checkIn: String, checkOut: String, nights: Int, adults: Int, children: Int) =
            SearchResponse(
                checkIn = checkIn,
                checkOut = checkOut,
                nights = nights,
                adults = adults,
                children = children,
                suppliers = result.suppliers.map { supplier ->
                    when (supplier) {
                        is SupplierResult.Succeeded -> SupplierStatusResponse(
                            supplier = supplier.supplierId,
                            status = supplier.status,
                            failureReason = null,
                            roomTypeCount = supplier.available.size,
                            outOfSpecCount = supplier.outOfSpecCount,
                            failedChunks = supplier.failedChunks,
                        )
                        is SupplierResult.Failed -> SupplierStatusResponse(
                            supplier = supplier.supplierId,
                            status = supplier.status,
                            failureReason = supplier.reason,
                            roomTypeCount = 0,
                            outOfSpecCount = 0,
                            failedChunks = supplier.failedChunks,
                        )
                    }
                },
                roomTypes = result.suppliers.flatMap { supplier ->
                    supplier.roomTypes.map { room ->
                        RoomTypeResponse(
                            hotelId = room.internalHotelId,
                            hotelName = room.hotelName,
                            roomTypeId = room.internalRoomTypeId,
                            roomTypeName = room.roomTypeName,
                            maxOccupancy = room.maxOccupancy,
                            supplier = supplier.supplierId,
                            availableRooms = room.availableRooms,
                            rate = RateResponse(
                                totalAmount = room.rate.total.amount,
                                currency = room.rate.total.currency,
                                breakfastIncluded = room.rate.conditions.breakfastIncluded,
                            ),
                        )
                    }
                },
            )
    }
}
