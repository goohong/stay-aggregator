package com.stayaggregator.catalog

import org.springframework.stereotype.Component

/**
 * 어댑터가 가져온 값([FetchedCatalog])에서 쓸 것과 뺄 것을 가른다.
 *
 * ADR-0027 의 두 번째·세 번째 질문을 여기서 답한다. 모든 공급사에 같은 규칙이므로 어댑터 밖 한곳에 둔다 (ADR-0031).
 * 첫 번째 질문(응답 전체를 읽을 수 있는가)은 공급사마다 실패를 알리는 방법이 달라 어댑터가 답한다.
 */
@Component
class CatalogNormalizer {

    fun normalize(fetched: FetchedCatalog): NormalizedCatalog {
        val excluded = mutableListOf<ExcludedItem>()
        val hotels = mutableListOf<NormalizedHotel>()

        // 같은 숙소 코드가 두 번 올 수 있다. 값이 같으면 하나만 쓰고, 다르면 어느 쪽인지 정할 수 없어 뺀다.
        fetched.hotels.groupBy { it.code }.forEach { (code, sameCode) ->
            val hotel = sameCode.first()
            when {
                code.isNullOrBlank() ->
                    excluded += ExcludedItem(hotelCode = code, roomTypeCode = null, reason = "숙소 코드가 없다")

                hotel.name.isNullOrBlank() ->
                    excluded += ExcludedItem(hotelCode = code, roomTypeCode = null, reason = "숙소명이 없다")

                sameCode.distinct().size > 1 ->
                    excluded += ExcludedItem(hotelCode = code, roomTypeCode = null, reason = "같은 숙소 코드가 다른 값으로 두 번 왔다")

                else -> hotels += NormalizedHotel(
                    code = code,
                    name = hotel.name,
                    roomTypes = normalizeRoomTypes(code, hotel.roomTypes, excluded),
                )
            }
        }
        return NormalizedCatalog(supplierId = fetched.supplierId, hotels = hotels, excluded = excluded)
    }

    private fun normalizeRoomTypes(
        hotelCode: String,
        fetched: List<FetchedRoomType>,
        excluded: MutableList<ExcludedItem>,
    ): List<NormalizedRoomType> {
        val roomTypes = mutableListOf<NormalizedRoomType>()
        fetched.groupBy { it.code }.forEach { (code, sameCode) ->
            val roomType = sameCode.first()
            val name = roomType.name
            val maxOccupancy = roomType.maxOccupancy
            when {
                code.isNullOrBlank() ->
                    excluded += ExcludedItem(hotelCode, code, "객실 타입 코드가 없다")

                name.isNullOrBlank() ->
                    excluded += ExcludedItem(hotelCode, code, "객실 타입명이 없다")

                maxOccupancy == null ->
                    excluded += ExcludedItem(hotelCode, code, "최대 수용 인원이 없다")

                // 한 명도 묵을 수 없는 객실 타입은 팔 수 없다. 재고 수를 다룰 때와 같은 기준이다 (ADR-0027, ADR-0039)
                maxOccupancy < 1 ->
                    excluded += ExcludedItem(hotelCode, code, "최대 수용 인원이 1 미만이다")

                sameCode.distinct().size > 1 ->
                    excluded += ExcludedItem(hotelCode, code, "같은 객실 타입 코드가 다른 값으로 두 번 왔다")

                else -> roomTypes += NormalizedRoomType(code, name, maxOccupancy)
            }
        }
        return roomTypes
    }
}

/** 이번 동기화에서 쓸 것과 뺀 것 */
data class NormalizedCatalog(
    val supplierId: String,
    val hotels: List<NormalizedHotel>,
    val excluded: List<ExcludedItem>,
)

data class NormalizedHotel(
    val code: String,
    val name: String,
    val roomTypes: List<NormalizedRoomType>,
)

data class NormalizedRoomType(
    val code: String,
    val name: String,
    val maxOccupancy: Int,
)

/**
 * 값을 정할 수 없어 뺀 항목. 지금은 결과값과 로그로만 남긴다 (ADR-0039).
 * 원본을 따로 보관하는 것은 기록 형태를 정할 때 함께 본다 (Q17·Q18).
 */
data class ExcludedItem(
    val hotelCode: String?,
    val roomTypeCode: String?,
    val reason: String,
)
