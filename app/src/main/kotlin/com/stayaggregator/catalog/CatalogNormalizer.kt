package com.stayaggregator.catalog

import com.stayaggregator.mapping.NormalizedHotel
import com.stayaggregator.mapping.NormalizedRoomType
import com.stayaggregator.quarantine.ExcludedValue
import com.stayaggregator.supplier.FetchedCatalog
import com.stayaggregator.supplier.FetchedHotel
import com.stayaggregator.supplier.FetchedRoomType
import com.stayaggregator.supplier.SupplierResponseException
import org.springframework.stereotype.Component

/**
 * 어댑터가 가져온 값([FetchedCatalog])에서 쓸 것과 뺄 것을 가른다.
 *
 * 값이 쓸 만한지는 [NormalizedHotel]·[NormalizedRoomType] 이 판정한다 (ADR-0041).
 * 여기서는 만들어 보고, 못 만든 것을 사유와 함께 모으는 일만 한다.
 *
 * 목록을 담는 필드가 아예 없으면 응답을 스펙대로 읽지 못한 것이라, 항목 문제가 아니라 그 공급사 실패로 본다
 * (ADR-0027 의 첫 질문, ADR-0041). 이 판정은 공급사마다 다르지 않아 어댑터 밖 한곳에 둔다 (ADR-0031).
 *
 * 같은 코드가 두 번 왔는지는 항목 하나만 봐서는 알 수 없어 여기서 본다. 객체가 자기 불변식으로 지킬 수 없는 유일한 조건이다.
 *
 * 어느 공급사의 응답인지는 모른다. 판정 규칙이 공급사 공통이라 알 필요가 없고, 그 이름은 consumer 가 든다 (ADR-0049).
 */
@Component
class CatalogNormalizer {

    fun normalize(fetched: FetchedCatalog): NormalizedCatalog {
        val hotels = fetched.hotels
            ?: throw SupplierResponseException("응답에 숙소 목록이 없다")

        val excluded = mutableListOf<ExcludedItem>()
        val normalized = mutableListOf<NormalizedHotel>()

        hotels.groupBy { it.code }.forEach { (code, sameCode) ->
            // 코드가 없는 항목들은 서로 같은 숙소로 볼 수 없다. 하나씩 본다
            if (code.isNullOrBlank()) {
                sameCode.forEach { hotel -> normalizeHotel(code, hotel, excluded)?.let { normalized += it } }
                return@forEach
            }
            // 같은 코드로 값이 다르게 오면 어느 쪽인지 정할 수 없어 뺀다. 값이 같으면 하나만 쓴다
            if (sameCode.distinct().size > 1) {
                excluded += ExcludedItem(hotelCode = code, roomTypeCode = null, value = ExcludedValue.HOTEL_CODE, reason = "같은 숙소 코드가 다른 값으로 두 번 왔다", source = sameCode)
                return@forEach
            }
            normalizeHotel(code, sameCode.first(), excluded)?.let { normalized += it }
        }
        return NormalizedCatalog(hotels = normalized, excluded = excluded)
    }

    private fun normalizeHotel(
        code: String?,
        hotel: FetchedHotel,
        excluded: MutableList<ExcludedItem>,
    ): NormalizedHotel? {
        val roomTypes = normalizeRoomTypes(code, hotel, excluded)
        return build(code, null, hotel, excluded) { NormalizedHotel.of(code, hotel.name, roomTypes) }
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
            if (code.isNullOrBlank()) {
                sameCode.forEach { roomType -> normalizeRoomType(hotelCode, code, roomType, excluded)?.let { roomTypes += it } }
                return@forEach
            }
            if (sameCode.distinct().size > 1) {
                excluded += ExcludedItem(hotelCode = hotelCode, roomTypeCode = code, value = ExcludedValue.ROOM_TYPE_CODE, reason = "같은 객실 타입 코드가 다른 값으로 두 번 왔다", source = sameCode)
                return@forEach
            }
            normalizeRoomType(hotelCode, code, sameCode.first(), excluded)?.let { roomTypes += it }
        }
        return roomTypes
    }

    private fun normalizeRoomType(
        hotelCode: String?,
        code: String?,
        roomType: FetchedRoomType,
        excluded: MutableList<ExcludedItem>,
    ): NormalizedRoomType? =
        build(hotelCode, code, roomType, excluded) { NormalizedRoomType.of(roomType.code, roomType.name, roomType.maxOccupancy) }

    /**
     * 만들어 보고, 거부되면 그 사유를 모은다.
     *
     * **람다에는 만드는 호출만 둔다.** 다른 계산을 넣으면 거기서 난 같은 종류의 예외가 항목 사유로 삼켜져,
     * 우리 쪽 결함이 "값을 쓸 수 없다"로 기록된다. 만드는 쪽도 `require` 로만 거부한다.
     *
     * 공급사가 값을 빠뜨리는 빈도는 우리가 모른다(ADR-0037). 그래서 드문 일로도 흔한 일로도 보지 않고,
     * 불변식을 만드는 객체 한 곳에 두는 쪽을 택했다(ADR-0041).
     */
    private fun <T> build(
        hotelCode: String?,
        roomTypeCode: String?,
        source: Any,
        excluded: MutableList<ExcludedItem>,
        create: () -> T,
    ): T? =
        try {
            create()
        } catch (e: IllegalArgumentException) {
            val reason = e.message ?: "값을 쓸 수 없다"
            excluded += ExcludedItem(hotelCode, roomTypeCode, valueOf(reason), reason, source)
            null
        }

    /**
     * 값을 담는 객체가 거부하며 낸 사유 문장에서 문제가 된 값을 정한다 (ADR-0054).
     *
     * 사유 문장은 [NormalizedHotel]·[NormalizedRoomType] 생성자에 있는 것이고 여기서 전부 다룬다.
     * 새 문장이 생기면 [ExcludedValue.OTHER] 로 떨어지고, 테스트가 그것을 잡는다.
     */
    private fun valueOf(reason: String): ExcludedValue =
        when (reason) {
            "숙소 코드가 없다" -> ExcludedValue.HOTEL_CODE
            "숙소명이 없다" -> ExcludedValue.HOTEL_NAME
            "팔 수 있는 객실 타입이 없다" -> ExcludedValue.STRUCTURE
            "객실 타입 코드가 없다" -> ExcludedValue.ROOM_TYPE_CODE
            "객실 타입명이 없다" -> ExcludedValue.ROOM_TYPE_NAME
            "최대 수용 인원이 없다", "최대 수용 인원이 1 미만이다" -> ExcludedValue.MAX_OCCUPANCY
            else -> ExcludedValue.OTHER
        }
}

/** 이번 동기화에서 쓸 것과 뺀 것 */
data class NormalizedCatalog(
    val hotels: List<NormalizedHotel>,
    val excluded: List<ExcludedItem>,
)

/**
 * 값을 정할 수 없어 뺀 항목. 결과값과 로그로 남기고, 같은 문제끼리 묶어 격리 기록에도 남긴다 (ADR-0039, ADR-0055).
 */
data class ExcludedItem(
    val hotelCode: String?,
    val roomTypeCode: String?,
    /** 문제가 된 값. 같은 문제로 묶는 기준이다 (ADR-0054) */
    val value: ExcludedValue,
    val reason: String,
    /** 우리가 읽어 들인 그 항목. 격리 기록에 JSON 으로 남긴다 */
    val source: Any,
)
