package com.stayaggregator.search

import com.stayaggregator.domain.Money
import com.stayaggregator.domain.Rejected
import com.stayaggregator.domain.Rate
import com.stayaggregator.domain.RateConditions
import com.stayaggregator.domain.AvailableRoomType
import com.stayaggregator.mapping.MappedHotel
import com.stayaggregator.mapping.MappedRoomType
import com.stayaggregator.quarantine.ExcludedValue
import com.stayaggregator.supplier.FetchedAvailability
import com.stayaggregator.supplier.FetchedAvailabilityItem
import com.stayaggregator.supplier.FetchedDailyInventory
import com.stayaggregator.supplier.FetchedDailyRate
import com.stayaggregator.supplier.FetchedPricing
import com.stayaggregator.domain.StayPeriod
import com.stayaggregator.supplier.SupplierFailure
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 한 공급사의 재고·요금 응답에서 쓸 것과 뺄 것을 가른다. 정규화 규칙은 공급사 공통이라 어댑터 밖 여기에 있다 (ADR-0031).
 *
 * 항목마다 세 가지를 본다.
 * 1. 식별자가 있고 매핑에 있는가.
 *    **식별자가 없는 것과 매핑에 없는 것은 다르다.** 매핑에 없는 것은 우리 동기화가 아직 반영하지 못한 것이라 다음 주기에 끝나고,
 *    그래서 스펙 제외로 세지 않는다 (ADR-0015, ADR-0046). 식별자가 아예 없는 것은 공급사 응답의 스펙 위반이고 동기화로 끝나지 않아 세어야 한다
 * 2. 재고를 하나로 정할 수 있는가. ADR-0027 의 재고 표대로다. 정해지면 날짜별 최솟값이 예약 가능 객실 수다 (ADR-0023)
 * 3. 요금을 하나로 정할 수 있는가. ADR-0027 의 요금 표대로다. 날짜별 금액은 요청한 날짜가 다 있을 때만 더한다
 *
 * 값이 유효한지는 값을 담는 객체([Money], [AvailableRoomType])가 검증하고, 여기서는 만들어 보고 못 만든 것을 사유와 함께 모은다 (ADR-0041).
 * 항목 목록 자체가 없으면 응답을 스펙대로 읽지 못한 것이라 공급사 실패다 (ADR-0027 의 첫 질문, ADR-0041).
 *
 * 어느 공급사인지 모른다. consumer 가 어댑터와 함께 든다 (ADR-0049).
 */
@Component
class AvailabilityNormalizer {

    /**
     * @param requestedHotelCodes 이번 chunk 에 넣은 숙소 코드. 응답이 여기 없는 숙소를 주면 이번 출력에 쓰이지 않으므로 버린다 (ADR-0027 두 번째 질문).
     *   이것을 보지 않으면 다른 chunk 의 숙소가 섞여 왔을 때 두 chunk 에서 같은 항목이 두 번 나간다
     */
    fun normalize(
        fetched: FetchedAvailability,
        mapped: List<MappedHotel>,
        period: StayPeriod,
        requestedHotelCodes: Collection<String>,
    ): NormalizedAvailability {
        val items = fetched.items ?: throw SupplierFailure.Unreadable("응답에 항목 목록이 없다")
        val hotelsByCode = mapped.associateBy { it.supplierHotelCode }
        val requested = requestedHotelCodes.toSet()
        val nights = period.nightDates()

        val available = mutableListOf<AvailableRoomType>()
        val excluded = mutableListOf<ExcludedRoomType>()
        val warnings = mutableListOf<NameMismatch>()

        // 같은 숙소·객실 타입이 두 번 오면 날짜 중복과 같은 규칙이다. 같은 값이면 하나만 쓰고, 다르면 어느 쪽인지 정할 수 없어 뺀다 (ADR-0027)
        val distinctItems = items.groupBy { it.hotelCode to it.roomTypeCode }.flatMap { (key, same) ->
            val (hotelCode, roomTypeCode) = key
            when {
                hotelCode.isNullOrBlank() || roomTypeCode.isNullOrBlank() -> same
                same.distinct().size == 1 -> listOf(same.first())
                else -> {
                    excluded += ExcludedRoomType(hotelCode, roomTypeCode, ExclusionKind.OUT_OF_SPEC, ExcludedValue.ROOM_TYPE_CODE, "같은 객실 타입이 다른 값으로 두 번 왔다", same.first())
                    emptyList()
                }
            }
        }

        distinctItems.forEach { item ->
            if (item.hotelCode.isNullOrBlank() || item.roomTypeCode.isNullOrBlank()) {
                val value = if (item.hotelCode.isNullOrBlank()) ExcludedValue.HOTEL_CODE else ExcludedValue.ROOM_TYPE_CODE
                excluded += ExcludedRoomType(item.hotelCode, item.roomTypeCode, ExclusionKind.OUT_OF_SPEC, value, "숙소 코드나 객실 타입 코드가 없다", item)
                return@forEach
            }
            if (item.hotelCode !in requested) {
                excluded += ExcludedRoomType(item.hotelCode, item.roomTypeCode, ExclusionKind.IGNORED, ExcludedValue.HOTEL_CODE, "요청하지 않은 숙소 코드가 왔다", item)
                return@forEach
            }
            val hotel = hotelsByCode[item.hotelCode]
            val roomType = hotel?.roomTypes?.firstOrNull { it.roomTypeCode == item.roomTypeCode }
            if (hotel == null || roomType == null) {
                excluded += ExcludedRoomType(item.hotelCode, item.roomTypeCode, ExclusionKind.UNMAPPED, ExcludedValue.MAPPING, "매핑에 없다", item)
                return@forEach
            }
            try {
                // 단계마다 문제가 된 값을 붙인다. 어느 단계에서 거부됐는지가 곧 무엇이 문제인지다 (ADR-0054)
                val rooms = step(ExcludedValue.INVENTORY) { availableRooms(item.dailyInventory, nights) }
                val rate = rate(item, nights)
                warnings += nameMismatches(item, hotel, roomType)
                available += step(ExcludedValue.INVENTORY) {
                    AvailableRoomType(
                        internalHotelId = hotel.internalHotelId,
                        hotelName = hotel.name,
                        internalRoomTypeId = roomType.internalRoomTypeId,
                        roomTypeName = roomType.name,
                        maxOccupancy = roomType.maxOccupancy,
                        availableRooms = rooms,
                        rate = rate,
                    )
                }
            } catch (e: StepRejected) {
                excluded += ExcludedRoomType(item.hotelCode, item.roomTypeCode, ExclusionKind.OUT_OF_SPEC, e.value, e.reason, item)
            }
        }
        return NormalizedAvailability(available, excluded, warnings)
    }

    /**
     * 재고 응답의 이름이 목록 이름과 다른지 본다. 다르면 목록이 오래된(stale) 신호다 (ADR-0014, ADR-0062).
     *
     * 앞뒤 공백만 무시한다. 대소문자나 "Room" 같은 표기를 정규화해 같다고 보는 것은 추정이라 하지 않는다.
     * 응답에 이름이 없으면 비교할 것이 없어 넘어간다. 이름은 응답에 쓰지 않으므로 없어도 항목을 빼지 않는다.
     */
    private fun nameMismatches(item: FetchedAvailabilityItem, hotel: MappedHotel, roomType: MappedRoomType): List<NameMismatch> =
        listOfNotNull(
            mismatch(item, ExcludedValue.HOTEL_NAME, received = item.hotelName, cataloged = hotel.name),
            mismatch(item, ExcludedValue.ROOM_TYPE_NAME, received = item.roomTypeName, cataloged = roomType.name),
        )

    private fun mismatch(item: FetchedAvailabilityItem, value: ExcludedValue, received: String?, cataloged: String): NameMismatch? =
        received?.trim()?.takeIf { it != cataloged.trim() }?.let {
            NameMismatch(item.hotelCode, item.roomTypeCode, value, "목록 이름 '$cataloged' 와 재고 응답 이름 '$it' 이 다르다", item)
        }

    /**
     * 날짜별 잔여 수를 요청한 날짜와 대조해 최솟값을 낸다 (ADR-0023, ADR-0027 의 재고 표).
     *
     * 요청하지 않은 날짜는 버린다. 요청한 날짜가 빠졌거나 같은 날짜가 다른 값으로 두 번 오면 하나로 정할 수 없어 거부한다.
     * 음수는 객실 수로 성립하지 않아 거부한다. 0 은 예약 불가일 뿐 거부가 아니다 (ADR-0026).
     */
    private fun availableRooms(inventory: List<FetchedDailyInventory>?, nights: List<LocalDate>): Int {
        if (inventory == null) refuse(ExcludedValue.INVENTORY, "날짜별 재고가 없다")
        val byDate = inventory.filter { it.date in nights }.groupBy { it.date }
        return nights.minOf { night ->
            val onThatNight = byDate[night]
            if (onThatNight.isNullOrEmpty()) refuse(ExcludedValue.INVENTORY, "숙박일 $night 의 재고가 없다")
            val values = onThatNight.map { it.remainingRooms }.distinct()
            if (values.size != 1) refuse(ExcludedValue.INVENTORY, "숙박일 $night 의 재고가 다른 값으로 두 번 왔다: $values")
            val rooms = values.single()
            if (rooms == null) refuse(ExcludedValue.INVENTORY, "숙박일 $night 의 잔여 수가 없다")
            if (rooms < 0) refuse(ExcludedValue.INVENTORY, "숙박일 $night 의 잔여 수가 음수다: $rooms")
            rooms
        }
    }

    /** 공급사가 준 요금 형식에 따라 세금 포함 총액 하나를 만든다 (ADR-0042, ADR-0027 의 요금 표) */
    private fun rate(item: FetchedAvailabilityItem, nights: List<LocalDate>): Rate {
        val breakfast = item.breakfastIncluded ?: refuse(ExcludedValue.SALE_CONDITIONS, "조식 포함 여부가 없다")
        val currency = item.currency ?: refuse(ExcludedValue.CURRENCY, "통화가 없다")
        val total = step(ExcludedValue.RATE) {
            when (val pricing = item.pricing) {
                null -> refuse(ExcludedValue.RATE, "요금이 없다")
                is FetchedPricing.Total -> total(pricing, currency)
                is FetchedPricing.Daily -> sumDaily(pricing.rates, currency, nights)
            }
        }
        return Rate(total, RateConditions(breakfastIncluded = breakfast))
    }

    /**
     * 만들어 보고, 거부되면 문제가 된 값을 붙여 올린다.
     *
     * 값이 유효한지는 여전히 값을 담는 객체가 검증한다 (ADR-0041). 여기서는 그 거부([Rejected])에 "무엇이 문제였나"를 붙일 뿐이다.
     * 값 객체의 거부가 아닌 예외는 잡지 않는다. 우리 결함이 "공급사 데이터 문제"로 격리 기록에 남지 않게 하려는 것이다 (ADR-0073).
     */
    private fun <T> step(value: ExcludedValue, block: () -> T): T =
        try {
            block()
        } catch (e: Money.Rejected) {
            // 금액 객체는 금액과 통화를 함께 검사한다. 어느 쪽이 틀렸는지는 객체가 알려 준 필드로 가린다 (ADR-0067)
            val rejected = when (e.field) {
                Money.Field.AMOUNT -> value
                Money.Field.CURRENCY -> ExcludedValue.CURRENCY
            }
            throw StepRejected(rejected, e.message ?: "값을 쓸 수 없다")
        } catch (e: Rejected) {
            // 값 객체의 거부만 항목 사유로 받는다. 그 밖의 예외는 우리 결함이라 삼키지 않는다 (ADR-0073)
            throw StepRejected(value, e.message ?: "값을 쓸 수 없다")
        }

    /** 한 항목을 뺄 때만 쓰는 신호. 정규화 밖으로 나가지 않는다 */
    private class StepRejected(val value: ExcludedValue, val reason: String) : RuntimeException(reason, null, false, false)

    /** 정규화 자신이 정한 거부. 값 객체의 거부와 같은 신호로 올린다 */
    private fun refuse(value: ExcludedValue, reason: String): Nothing = throw StepRejected(value, reason)

    private fun total(pricing: FetchedPricing.Total, currency: String): Money {
        if (pricing.totalPrice == null) refuse(ExcludedValue.RATE, "총액이 없다")
        // 세금액이 따로 오지 않으므로 미포함이면 세금 포함 총액을 만들 수 없다 (ADR-0042)
        if (pricing.taxIncluded != true) refuse(ExcludedValue.RATE, "총액에 세금이 포함되지 않았다")
        return Money(pricing.totalPrice, currency)
    }

    /** 요청한 날짜가 다 있을 때만 더한다. 빠진 날이 있으면 총액이 아니다 */
    private fun sumDaily(rates: List<FetchedDailyRate>?, currency: String, nights: List<LocalDate>): Money {
        if (rates == null) refuse(ExcludedValue.RATE, "날짜별 요금이 없다")
        val byDate = rates.filter { it.date in nights }.groupBy { it.date }
        return nights.fold(Money(0, currency)) { sum, night ->
            val onThatNight = byDate[night]
            if (onThatNight.isNullOrEmpty()) refuse(ExcludedValue.RATE, "숙박일 $night 의 요금이 없다")
            val values = onThatNight.distinct()
            if (values.size != 1) refuse(ExcludedValue.RATE, "숙박일 $night 의 요금이 다른 값으로 두 번 왔다")
            val day = values.single()
            if (day.nightlyRate == null) refuse(ExcludedValue.RATE, "숙박일 $night 의 1박 금액이 없다")
            if (day.taxAmount == null) refuse(ExcludedValue.RATE, "숙박일 $night 의 세금액이 없다")
            sum + Money(day.nightlyRate, currency) + Money(day.taxAmount, currency)
        }
    }
}

/** 한 공급사 응답에서 쓸 것과 뺀 것 */
data class NormalizedAvailability(
    val available: List<AvailableRoomType>,
    val excluded: List<ExcludedRoomType>,
    /** 항목은 내보냈지만 알아 둘 것. 응답 건수에 넣지 않는다 (ADR-0062) */
    val warnings: List<NameMismatch> = emptyList(),
) {
    /** 응답에 싣는 건수는 스펙과 달라 뺀 것만이다. 매핑에 없어 뺀 것은 세지 않는다 (ADR-0046) */
    val outOfSpecCount: Int
        get() = excluded.count { it.kind == ExclusionKind.OUT_OF_SPEC }
}

/**
 * 뺀 항목과 사유. 지금은 결과값과 로그로만 남긴다. 종류로 더 나누는 것은 Q17 에서 정한다.
 */
data class ExcludedRoomType(
    val hotelCode: String?,
    val roomTypeCode: String?,
    val kind: ExclusionKind,
    /** 문제가 된 값. 같은 문제로 그룹화하는 기준이다 (ADR-0054) */
    val value: ExcludedValue,
    val reason: String,
    /** 우리가 읽어 들인 그 항목. 격리 기록에 JSON 으로 남긴다 (ADR-0055) */
    val source: FetchedAvailabilityItem,
)

/** 목록과 재고 응답의 이름이 다르다. 항목은 빼지 않는다 (ADR-0062) */
data class NameMismatch(
    val hotelCode: String?,
    val roomTypeCode: String?,
    val value: ExcludedValue,
    val reason: String,
    val source: FetchedAvailabilityItem,
)

enum class ExclusionKind {
    /**
     * 매핑에 없다. 공급사가 목록 동기화 이후에 추가한 상품이라 **다음 동기화에서 저절로 끝나는 상태**이고, 그래서 응답에 드러내지 않는다 (ADR-0015).
     * 식별자가 아예 없는 것은 여기가 아니라 [OUT_OF_SPEC] 이다. 그것은 동기화로 끝나지 않는다
     */
    UNMAPPED,

    /** 스펙과 달라 값을 하나로 정할 수 없다. 건수를 응답에 싣는다 (ADR-0027, ADR-0046) */
    OUT_OF_SPEC,

    /**
     * 이번 출력에 쓰이지 않는 것이 섞여 와 버렸다. 예: 요청하지 않은 숙소 코드.
     * ADR-0027 의 두 번째 질문("출력값 계산에 쓰이는가")에 "아니요"라 버리고 기록만 한다. 결과가 줄어든 것이 아니라 응답 건수에 넣지 않는다
     */
    IGNORED,
}
