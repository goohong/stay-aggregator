package com.stayaggregator.catalog

import com.stayaggregator.domain.NormalizedHotel
import com.stayaggregator.domain.NormalizedRoomType
import com.stayaggregator.quarantine.ExcludedValue
import com.stayaggregator.supplier.FetchedCatalog
import com.stayaggregator.supplier.FetchedHotel
import com.stayaggregator.supplier.FetchedRoomType
import com.stayaggregator.supplier.SupplierResponseException
import org.springframework.stereotype.Component

/**
 * 어댑터가 가져온 값([FetchedCatalog])에서 쓸 것과 뺄 것을 가른다.
 *
 * 값이 유효한지는 [NormalizedHotel]·[NormalizedRoomType] 이 검증한다 (ADR-0041).
 * 여기서는 만들어 보고, 못 만든 것을 사유와 함께 모으는 일만 한다.
 *
 * 목록을 담는 필드가 아예 없으면 응답을 스펙대로 읽지 못한 것이라, 항목 문제가 아니라 그 공급사 실패로 본다
 * (ADR-0027 의 첫 질문, ADR-0041). 이 분류는 공급사마다 다르지 않아 어댑터 밖 한곳에 둔다 (ADR-0031).
 *
 * 같은 코드가 두 번 왔는지는 항목 하나만 봐서는 알 수 없어 여기서 본다. 객체가 자기 불변식으로 지킬 수 없는 유일한 조건이다.
 *
 * 어느 공급사의 응답인지는 모른다. 검증 규칙이 공급사 공통이라 알 필요가 없고, 그 이름은 consumer 가 든다 (ADR-0049).
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
     * 문제가 된 값은 값을 담는 객체가 거부하며 알려 준 필드로 정한다 (ADR-0054, ADR-0067).
     * `when` 이 필드를 빠짐없이 다루므로, 객체에 검사가 늘어 필드가 생기면 여기가 컴파일되지 않아 드러난다.
     *
     * **그 객체의 거부만 받는다.** 다른 예외는 내부 오류라 항목 사유로 삼키지 않고 올려 보낸다.
     */
    private fun <T> build(
        hotelCode: String?,
        roomTypeCode: String?,
        source: Any,
        excluded: MutableList<ExcludedItem>,
        create: () -> T,
    ): T? {
        val (value, reason) = try {
            return create()
        } catch (e: NormalizedHotel.Rejected) {
            valueOf(e.field) to e.message
        } catch (e: NormalizedRoomType.Rejected) {
            valueOf(e.field) to e.message
        }
        excluded += ExcludedItem(hotelCode, roomTypeCode, value, reason ?: "값을 쓸 수 없다", source)
        return null
    }

    private fun valueOf(field: NormalizedHotel.Field): ExcludedValue =
        when (field) {
            NormalizedHotel.Field.CODE -> ExcludedValue.HOTEL_CODE
            NormalizedHotel.Field.NAME -> ExcludedValue.HOTEL_NAME
            NormalizedHotel.Field.ROOM_TYPES -> ExcludedValue.STRUCTURE
        }

    private fun valueOf(field: NormalizedRoomType.Field): ExcludedValue =
        when (field) {
            NormalizedRoomType.Field.CODE -> ExcludedValue.ROOM_TYPE_CODE
            NormalizedRoomType.Field.NAME -> ExcludedValue.ROOM_TYPE_NAME
            NormalizedRoomType.Field.MAX_OCCUPANCY -> ExcludedValue.MAX_OCCUPANCY
        }
}

/** 이번 동기화에서 쓸 것과 뺀 것 */
data class NormalizedCatalog(
    val hotels: List<NormalizedHotel>,
    val excluded: List<ExcludedItem>,
)

/**
 * 값을 정할 수 없어 뺀 항목. 결과값과 로그로 남기고, 같은 문제끼리 그룹화해 격리 기록에도 남긴다 (ADR-0039, ADR-0055).
 */
data class ExcludedItem(
    val hotelCode: String?,
    val roomTypeCode: String?,
    /** 문제가 된 값. 같은 문제로 그룹화하는 기준이다 (ADR-0054) */
    val value: ExcludedValue,
    val reason: String,
    /** 우리가 읽어 들인 그 항목. 격리 기록에 JSON 으로 남긴다 */
    val source: Any,
)
