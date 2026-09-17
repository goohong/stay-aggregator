package com.stayaggregator.search

import com.stayaggregator.mapping.MappedHotel
import com.stayaggregator.mapping.MappedRoomType
import com.stayaggregator.supplier.FetchedAvailability
import com.stayaggregator.supplier.FetchedAvailabilityItem
import com.stayaggregator.supplier.FetchedDailyInventory
import com.stayaggregator.supplier.FetchedDailyRate
import com.stayaggregator.supplier.FetchedPricing
import com.stayaggregator.supplier.StayPeriod
import com.stayaggregator.supplier.SupplierResponseException
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 한 공급사의 재고·요금 응답에서 쓸 것과 뺄 것을 가른다. 판정 규칙은 공급사 공통이라 어댑터 밖 여기에 있다 (ADR-0031).
 *
 * 항목마다 세 가지를 본다.
 * 1. 식별자가 있고 매핑에 있는가.
 *    **식별자가 없는 것과 매핑에 없는 것은 다르다.** 매핑에 없는 것은 우리 동기화가 아직 따라잡지 못한 것이라 다음 주기에 끝나고,
 *    그래서 스펙 제외로 세지 않는다 (ADR-0015, ADR-0046). 식별자가 아예 없는 것은 공급사 응답의 스펙 위반이고 동기화로 끝나지 않아 세어야 한다
 * 2. 재고를 하나로 정할 수 있는가. ADR-0027 의 재고 표대로다. 정해지면 날짜별 최솟값이 예약 가능 객실 수다 (ADR-0023)
 * 3. 요금을 하나로 정할 수 있는가. ADR-0027 의 요금 표대로다. 날짜별 금액은 요청한 날짜가 다 있을 때만 더한다
 *
 * 값이 쓸 만한지는 값을 담는 객체([Money], [AvailableRoomType])가 판정하고, 여기서는 만들어 보고 못 만든 것을 사유와 함께 모은다 (ADR-0041).
 * 항목 목록 자체가 없으면 응답을 스펙대로 읽지 못한 것이라 공급사 실패다 (ADR-0027 의 첫 질문, ADR-0041).
 *
 * 어느 공급사인지 모른다. consumer 가 어댑터와 함께 든다 (ADR-0049).
 */
@Component
class AvailabilityNormalizer {

    fun normalize(fetched: FetchedAvailability, mapped: List<MappedHotel>, period: StayPeriod): NormalizedAvailability {
        val items = fetched.items ?: throw SupplierResponseException("응답에 항목 목록이 없다")
        val hotelsByCode = mapped.associateBy { it.supplierHotelCode }
        val nights = period.nightDates()

        val available = mutableListOf<AvailableRoomType>()
        val excluded = mutableListOf<ExcludedRoomType>()

        items.forEach { item ->
            if (item.hotelCode.isNullOrBlank() || item.roomTypeCode.isNullOrBlank()) {
                excluded += ExcludedRoomType(item.hotelCode, item.roomTypeCode, ExclusionKind.OUT_OF_SPEC, "숙소 코드나 객실 타입 코드가 없다")
                return@forEach
            }
            val hotel = hotelsByCode[item.hotelCode]
            val roomType = hotel?.roomTypes?.firstOrNull { it.roomTypeCode == item.roomTypeCode }
            if (hotel == null || roomType == null) {
                excluded += ExcludedRoomType(item.hotelCode, item.roomTypeCode, ExclusionKind.UNMAPPED, "매핑에 없다")
                return@forEach
            }
            try {
                available += AvailableRoomType(
                    internalHotelId = hotel.internalHotelId,
                    hotelName = hotel.name,
                    internalRoomTypeId = roomType.internalRoomTypeId,
                    roomTypeName = roomType.name,
                    maxOccupancy = roomType.maxOccupancy,
                    availableRooms = availableRooms(item.dailyInventory, nights),
                    rate = rate(item, nights),
                )
            } catch (e: IllegalArgumentException) {
                excluded += ExcludedRoomType(item.hotelCode, item.roomTypeCode, ExclusionKind.OUT_OF_SPEC, e.message ?: "값을 쓸 수 없다")
            }
        }
        return NormalizedAvailability(available, excluded)
    }

    /**
     * 날짜별 잔여 수를 요청한 날짜와 대조해 최솟값을 낸다 (ADR-0023, ADR-0027 의 재고 표).
     *
     * 요청하지 않은 날짜는 버린다. 요청한 날짜가 빠졌거나 같은 날짜가 다른 값으로 두 번 오면 하나로 정할 수 없어 거부한다.
     * 음수는 객실 수로 성립하지 않아 거부한다. 0 은 예약 불가일 뿐 거부가 아니다 (ADR-0026).
     */
    private fun availableRooms(inventory: List<FetchedDailyInventory>?, nights: List<LocalDate>): Int {
        require(inventory != null) { "날짜별 재고가 없다" }
        val byDate = inventory.filter { it.date in nights }.groupBy { it.date }
        return nights.minOf { night ->
            val onThatNight = byDate[night]
            require(!onThatNight.isNullOrEmpty()) { "숙박일 $night 의 재고가 없다" }
            val values = onThatNight.map { it.remainingRooms }.distinct()
            require(values.size == 1) { "숙박일 $night 의 재고가 다른 값으로 두 번 왔다: $values" }
            val rooms = values.single()
            require(rooms != null) { "숙박일 $night 의 잔여 수가 없다" }
            require(rooms >= 0) { "숙박일 $night 의 잔여 수가 음수다: $rooms" }
            rooms
        }
    }

    /** 공급사가 준 요금 모양에 따라 세금 포함 총액 하나를 만든다 (ADR-0042, ADR-0027 의 요금 표) */
    private fun rate(item: FetchedAvailabilityItem, nights: List<LocalDate>): Rate {
        require(item.breakfastIncluded != null) { "조식 포함 여부가 없다" }
        val currency = item.currency
        require(currency != null) { "통화가 없다" }
        val total = when (val pricing = item.pricing) {
            null -> throw IllegalArgumentException("요금이 없다")
            is FetchedPricing.Total -> total(pricing, currency)
            is FetchedPricing.Daily -> sumDaily(pricing.rates, currency, nights)
        }
        return Rate(total, RateConditions(breakfastIncluded = item.breakfastIncluded))
    }

    private fun total(pricing: FetchedPricing.Total, currency: String): Money {
        require(pricing.totalPrice != null) { "총액이 없다" }
        // 세금액이 따로 오지 않으므로 미포함이면 세금 포함 총액을 만들 수 없다 (ADR-0042)
        require(pricing.taxIncluded == true) { "총액에 세금이 포함되지 않았다" }
        return Money(pricing.totalPrice, currency)
    }

    /** 요청한 날짜가 다 있을 때만 더한다. 빠진 날이 있으면 총액이 아니다 */
    private fun sumDaily(rates: List<FetchedDailyRate>?, currency: String, nights: List<LocalDate>): Money {
        require(rates != null) { "날짜별 요금이 없다" }
        val byDate = rates.filter { it.date in nights }.groupBy { it.date }
        return nights.fold(Money(0, currency)) { sum, night ->
            val onThatNight = byDate[night]
            require(!onThatNight.isNullOrEmpty()) { "숙박일 $night 의 요금이 없다" }
            val values = onThatNight.distinct()
            require(values.size == 1) { "숙박일 $night 의 요금이 다른 값으로 두 번 왔다" }
            val day = values.single()
            require(day.nightlyRate != null) { "숙박일 $night 의 1박 금액이 없다" }
            require(day.taxAmount != null) { "숙박일 $night 의 세금액이 없다" }
            sum + Money(day.nightlyRate, currency) + Money(day.taxAmount, currency)
        }
    }
}

/** 한 공급사 응답에서 쓸 것과 뺀 것 */
data class NormalizedAvailability(
    val available: List<AvailableRoomType>,
    val excluded: List<ExcludedRoomType>,
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
    val reason: String,
)

enum class ExclusionKind {
    /**
     * 매핑에 없다. 공급사가 목록 동기화 이후에 추가한 상품이라 **다음 동기화에서 저절로 끝나는 상태**이고, 그래서 응답에 드러내지 않는다 (ADR-0015).
     * 식별자가 아예 없는 것은 여기가 아니라 [OUT_OF_SPEC] 이다. 그것은 동기화로 끝나지 않는다
     */
    UNMAPPED,

    /** 스펙과 달라 값을 하나로 정할 수 없다. 건수를 응답에 싣는다 (ADR-0027, ADR-0046) */
    OUT_OF_SPEC,
}
