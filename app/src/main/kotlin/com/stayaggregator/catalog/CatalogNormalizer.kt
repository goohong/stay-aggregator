package com.stayaggregator.catalog

import com.stayaggregator.mapping.NormalizedHotel
import com.stayaggregator.mapping.NormalizedRoomType
import com.stayaggregator.supplier.FetchedCatalog
import com.stayaggregator.supplier.FetchedHotel
import com.stayaggregator.supplier.FetchedRoomType
import com.stayaggregator.supplier.SupplierResponseException
import org.springframework.stereotype.Component

/**
 * 어댑터가 가져온 값([FetchedCatalog])에서 쓸 것과 뺄 것을 가른다.
 *
 * 값이 쓸 만한지는 [NormalizedHotel]·[NormalizedRoomType] 의 생성자가 판정한다 (ADR-0041).
 * 여기서는 만들어 보고, 못 만든 것을 사유와 함께 모으는 일만 한다.
 *
 * 목록을 담는 필드가 아예 없으면 응답을 스펙대로 읽지 못한 것이라, 항목 문제가 아니라 그 공급사 실패로 본다
 * (ADR-0027 의 첫 질문, ADR-0041). 이 판정은 공급사마다 다르지 않아 어댑터 밖 한곳에 둔다 (ADR-0031).
 */
@Component
class CatalogNormalizer {

    fun normalize(fetched: FetchedCatalog): NormalizedCatalog {
        val hotels = fetched.hotels
            ?: throw SupplierResponseException("공급사 ${fetched.supplierId} 응답에 숙소 목록이 없다")

        val excluded = mutableListOf<ExcludedItem>()
        val normalized = mutableListOf<NormalizedHotel>()

        // 같은 숙소 코드가 두 번 올 수 있다. 값이 같으면 하나만 쓰고, 다르면 어느 쪽인지 정할 수 없어 뺀다.
        hotels.groupBy { it.code }.forEach { (code, sameCode) ->
            if (sameCode.distinct().size > 1) {
                excluded += ExcludedItem(code, null, "같은 숙소 코드가 다른 값으로 두 번 왔다")
                return@forEach
            }
            val hotel = sameCode.first()
            val roomTypes = normalizeRoomTypes(code, hotel, excluded)
            build(code, null, excluded) { NormalizedHotel(code.orEmpty(), hotel.name.orEmpty(), roomTypes) }
                ?.let { normalized += it }
        }
        return NormalizedCatalog(supplierId = fetched.supplierId, hotels = normalized, excluded = excluded)
    }

    private fun normalizeRoomTypes(
        hotelCode: String?,
        hotel: FetchedHotel,
        excluded: MutableList<ExcludedItem>,
    ): List<NormalizedRoomType> {
        // 객실 타입 목록이 없는 것과 빈 것을 같게 본다. 둘 다 팔 수 있는 객실 타입이 없는 숙소다 (ADR-0041).
        val fetched = hotel.roomTypes ?: return emptyList()

        val roomTypes = mutableListOf<NormalizedRoomType>()
        fetched.groupBy { it.code }.forEach { (code, sameCode) ->
            if (sameCode.distinct().size > 1) {
                excluded += ExcludedItem(hotelCode, code, "같은 객실 타입 코드가 다른 값으로 두 번 왔다")
                return@forEach
            }
            val roomType = sameCode.first()
            build(hotelCode, code, excluded) { normalizedRoomType(roomType) }?.let { roomTypes += it }
        }
        return roomTypes
    }

    private fun normalizedRoomType(roomType: FetchedRoomType) =
        NormalizedRoomType(
            code = roomType.code.orEmpty(),
            name = roomType.name.orEmpty(),
            // 없는 것과 0 을 같게 본다. 둘 다 한 명도 묵을 수 없다는 뜻이 되어 생성자가 거른다
            maxOccupancy = roomType.maxOccupancy ?: 0,
        )

    /**
     * 만들어 보고, 생성자가 거부하면 그 사유를 모은다.
     *
     * 공급사가 값을 빠뜨리는 빈도는 우리가 모른다(ADR-0037). 그래서 드문 일로도 흔한 일로도 보지 않고,
     * 불변식을 생성자 한 곳에 두는 쪽을 택했다(ADR-0041). 사유 문장도 그 생성자에 있다.
     */
    private fun <T> build(
        hotelCode: String?,
        roomTypeCode: String?,
        excluded: MutableList<ExcludedItem>,
        create: () -> T,
    ): T? =
        try {
            create()
        } catch (e: IllegalArgumentException) {
            excluded += ExcludedItem(hotelCode, roomTypeCode, e.message ?: "값을 쓸 수 없다")
            null
        }
}

/** 이번 동기화에서 쓸 것과 뺀 것 */
data class NormalizedCatalog(
    val supplierId: String,
    val hotels: List<NormalizedHotel>,
    val excluded: List<ExcludedItem>,
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
