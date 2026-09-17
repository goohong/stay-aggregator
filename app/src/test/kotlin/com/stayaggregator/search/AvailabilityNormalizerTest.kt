package com.stayaggregator.search

import com.stayaggregator.quarantine.ExcludedValue
import com.stayaggregator.mapping.MappedHotel
import com.stayaggregator.mapping.MappedRoomType
import com.stayaggregator.supplier.FetchedAvailability
import com.stayaggregator.supplier.FetchedAvailabilityItem
import com.stayaggregator.supplier.FetchedDailyInventory
import com.stayaggregator.supplier.FetchedDailyRate
import com.stayaggregator.supplier.FetchedPricing
import com.stayaggregator.supplier.StayPeriod
import com.stayaggregator.supplier.SupplierResponseException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/**
 * 재고·요금 정규화가 ADR-0027 의 두 표대로 가르는지, 뺀 것을 종류별로 세는지 본다.
 *
 * 세 줄짜리 표의 줄 하나가 테스트 하나다. 표에 없는 경우를 여기서 새로 정하지 않는다.
 */
class AvailabilityNormalizerTest {

    private val normalizer = AvailabilityNormalizer()

    private val oct5 = LocalDate.of(2026, 10, 5)
    private val oct6 = LocalDate.of(2026, 10, 6)
    private val oct7 = LocalDate.of(2026, 10, 7)
    private val threeNights = StayPeriod(oct5, LocalDate.of(2026, 10, 8))

    /** 테스트 응답에 나오는 숙소 코드를 모두 요청한 것으로 둔다 */
    private val requestedCodes = listOf("A-1", "A-2", "A-9")

    private val hotelId = UUID.randomUUID()
    private val roomTypeId = UUID.randomUUID()
    private val mapped = listOf(
        MappedHotel(hotelId, "A-1", "강변 호텔", listOf(MappedRoomType(roomTypeId, "DLX", "디럭스", 2))),
    )

    // ── 정상 ──

    @Test
    fun `날짜별 요금은 세금까지 더한 총액이 되고 재고는 최솟값이 된다`() {
        // 잔여 수 3/1/5 → 1 (ADR-0023 의 예시 값)
        val item = itemA(
            rates = listOf(rate(oct5, 110_000, 11_000), rate(oct6, 140_000, 14_000), rate(oct7, 110_000, 11_000)),
            inventory = listOf(inv(oct5, 3), inv(oct6, 1), inv(oct7, 5)),
        )

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.available).singleElement().satisfies({ room ->
            assertThat(room.internalHotelId).isEqualTo(hotelId)
            assertThat(room.internalRoomTypeId).isEqualTo(roomTypeId)
            assertThat(room.hotelName).isEqualTo("강변 호텔")
            assertThat(room.maxOccupancy).isEqualTo(2)
            assertThat(room.availableRooms).isEqualTo(1)
            assertThat(room.rate).isEqualTo(Rate(Money(396_000, "KRW"), RateConditions(breakfastIncluded = false)))
        })
        assertThat(result.excluded).isEmpty()
    }

    @Test
    fun `총액 요금은 그대로 총액이 되고 조식 조건이 붙는다`() {
        val item = itemB(totalPrice = 431_000, taxIncluded = true, breakfast = true, inventory = listOf(inv(oct5, 4), inv(oct6, 2), inv(oct7, 6)))

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.available).singleElement()
            .satisfies({ room -> assertThat(room.rate).isEqualTo(Rate(Money(431_000, "KRW"), RateConditions(breakfastIncluded = true))) })
    }

    @Test
    fun `하루라도 0 이면 예약 가능 객실 수는 0 이고 응답에서 빼지 않는다`() {
        // 잔여 수 2/0/4 → 0 (ADR-0023 의 예시 값), 0 이어도 뺀 것이 아니다 (ADR-0026)
        val item = itemA(rates = fullRates(), inventory = listOf(inv(oct5, 2), inv(oct6, 0), inv(oct7, 4)))

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.available).singleElement().satisfies({ room -> assertThat(room.availableRooms).isZero() })
    }

    // ── 매핑 (ADR-0015) ──

    @Test
    fun `매핑에 없는 객실 타입은 빼고 스펙 제외로 세지 않는다`() {
        val unknown = itemA(rates = fullRates(), inventory = fullInventory()).copy(roomTypeCode = "NEW")

        val result = normalizer.normalize(FetchedAvailability(listOf(unknown)), mapped, threeNights, requestedCodes)

        assertThat(result.available).isEmpty()
        assertThat(result.excluded).singleElement().satisfies({ ex ->
            assertThat(ex.kind).isEqualTo(ExclusionKind.UNMAPPED)
            assertThat(ex.roomTypeCode).isEqualTo("NEW")
        })
        assertThat(result.outOfSpecCount).isZero()
    }

    @Test
    fun `숙소 코드가 없는 항목은 매핑 누락이 아니라 스펙 제외로 센다`() {
        // 매핑에 없는 것은 다음 동기화에서 끝나지만 코드가 없는 것은 끝나지 않는다 (ADR-0046)
        val noCode = itemA(rates = fullRates(), inventory = fullInventory()).copy(hotelCode = null)

        assertOutOfSpec(noCode, "숙소 코드나 객실 타입 코드가 없다")
    }

    @Test
    fun `객실 타입 코드가 비어 있어도 스펙 제외로 센다`() {
        val blankCode = itemA(rates = fullRates(), inventory = fullInventory()).copy(roomTypeCode = " ")

        assertOutOfSpec(blankCode, "숙소 코드나 객실 타입 코드가 없다")
    }

    @Test
    fun `매핑에 없는 숙소도 같다`() {
        val unknown = itemA(rates = fullRates(), inventory = fullInventory()).copy(hotelCode = "A-9")

        val result = normalizer.normalize(FetchedAvailability(listOf(unknown)), mapped, threeNights, requestedCodes)

        assertThat(result.excluded).singleElement().satisfies({ ex -> assertThat(ex.kind).isEqualTo(ExclusionKind.UNMAPPED) })
    }

    @Test
    fun `요청하지 않은 숙소 코드가 오면 버리고 응답 건수에 넣지 않는다`() {
        val item = itemA(rates = fullRates(), inventory = fullInventory())

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedHotelCodes = listOf("A-7"))

        assertThat(result.available).isEmpty()
        assertThat(result.excluded).singleElement().satisfies({ ex -> assertThat(ex.kind).isEqualTo(ExclusionKind.IGNORED) })
        assertThat(result.outOfSpecCount).isZero()
    }

    @Test
    fun `같은 숙소 객실 타입이 같은 값으로 두 번 오면 하나만 내보낸다`() {
        val item = itemA(rates = fullRates(), inventory = fullInventory())

        val result = normalizer.normalize(FetchedAvailability(listOf(item, item)), mapped, threeNights, requestedCodes)

        assertThat(result.available).hasSize(1)
    }

    @Test
    fun `같은 숙소 객실 타입이 다른 값으로 두 번 오면 뺀다`() {
        val one = itemA(rates = fullRates(), inventory = fullInventory())
        val other = one.copy(breakfastIncluded = true)

        val result = normalizer.normalize(FetchedAvailability(listOf(one, other)), mapped, threeNights, requestedCodes)

        assertThat(result.available).isEmpty()
        assertThat(result.outOfSpecCount).isEqualTo(1)
    }

    // ── 이름 불일치 (ADR-0062) ──

    @Test
    fun `재고 응답의 이름이 목록과 다르면 항목은 내보내고 경고를 남긴다`() {
        val item = itemA(rates = fullRates(), inventory = fullInventory()).copy(hotelName = "강변 호텔 본관", roomTypeName = "디럭스")

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.available).hasSize(1)
        assertThat(result.outOfSpecCount).isZero()
        assertThat(result.warnings).singleElement().satisfies({ w ->
            assertThat(w.value).isEqualTo(com.stayaggregator.quarantine.ExcludedValue.HOTEL_NAME)
            assertThat(w.reason).contains("강변 호텔 본관")
        })
    }

    @Test
    fun `이름이 앞뒤 공백만 다르면 경고하지 않고 표기가 다르면 경고한다`() {
        val spaced = itemA(rates = fullRates(), inventory = fullInventory()).copy(hotelName = " 강변 호텔 ", roomTypeName = "디럭스 ")
        val suffixed = spaced.copy(roomTypeName = "디럭스 룸")

        assertThat(normalizer.normalize(FetchedAvailability(listOf(spaced)), mapped, threeNights, requestedCodes).warnings).isEmpty()
        assertThat(normalizer.normalize(FetchedAvailability(listOf(suffixed)), mapped, threeNights, requestedCodes).warnings).hasSize(1)
    }

    // ── 재고 표 (ADR-0027) ──

    @Test
    fun `요청하지 않은 날짜가 섞이면 그 날짜만 버린다`() {
        val item = itemA(rates = fullRates(), inventory = fullInventory() + inv(LocalDate.of(2026, 10, 8), 0))

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.available).singleElement().satisfies({ room -> assertThat(room.availableRooms).isEqualTo(2) })
    }

    @Test
    fun `요청 숙박일 하나가 빠지면 그 객실 타입을 뺀다`() {
        val item = itemA(rates = fullRates(), inventory = listOf(inv(oct5, 4), inv(oct7, 6)))

        assertOutOfSpec(item, "숙박일 2026-10-06 의 재고가 없다")
    }

    @Test
    fun `같은 날짜가 다른 값으로 두 번 오면 그 객실 타입을 뺀다`() {
        val item = itemA(rates = fullRates(), inventory = fullInventory() + inv(oct6, 5))

        assertOutOfSpec(item, "숙박일 2026-10-06 의 재고가 다른 값으로 두 번 왔다: [2, 5]")
    }

    @Test
    fun `같은 날짜가 같은 값으로 두 번 오면 그 값으로 계산한다`() {
        val item = itemA(rates = fullRates(), inventory = fullInventory() + inv(oct6, 2))

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.available).singleElement().satisfies({ room -> assertThat(room.availableRooms).isEqualTo(2) })
    }

    @Test
    fun `잔여 수가 음수면 그 객실 타입을 뺀다`() {
        val item = itemA(rates = fullRates(), inventory = listOf(inv(oct5, 4), inv(oct6, -1), inv(oct7, 6)))

        assertOutOfSpec(item, "숙박일 2026-10-06 의 잔여 수가 음수다: -1")
    }

    // ── 요금 표 (ADR-0027) ──

    @Test
    fun `금액이 음수면 그 객실 타입을 뺀다`() {
        val item = itemB(totalPrice = -1, taxIncluded = true, breakfast = true, inventory = fullInventory())

        assertOutOfSpec(item, "금액이 음수다")
    }

    @Test
    fun `금액이 0 이면 그대로 계산한다`() {
        val item = itemB(totalPrice = 0, taxIncluded = true, breakfast = true, inventory = fullInventory())

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.available).singleElement().satisfies({ room -> assertThat(room.rate.total).isEqualTo(Money(0, "KRW")) })
    }

    @Test
    fun `통화가 없으면 그 객실 타입을 뺀다`() {
        val item = itemB(totalPrice = 1000, taxIncluded = true, breakfast = true, inventory = fullInventory()).copy(currency = null)

        assertOutOfSpec(item, "통화가 없다")
    }

    @Test
    fun `통화 형식이 틀리면 요금이 아니라 통화 문제로 기록한다`() {
        // 금액 객체가 알려 준 필드로 가른다. 0원짜리 금액을 만들어 통화만 따로 검사하지 않는다 (ADR-0067)
        val item = itemB(totalPrice = 1000, taxIncluded = true, breakfast = true, inventory = fullInventory()).copy(currency = "krw")

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.excluded).singleElement().satisfies({ ex -> assertThat(ex.value).isEqualTo(ExcludedValue.CURRENCY) })
    }

    @Test
    fun `금액이 음수면 요금 문제로 기록한다`() {
        val item = itemB(totalPrice = -1, taxIncluded = true, breakfast = true, inventory = fullInventory())

        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.excluded).singleElement().satisfies({ ex -> assertThat(ex.value).isEqualTo(ExcludedValue.RATE) })
    }

    @Test
    fun `날짜별 요금에서 숙박일 하나가 빠지면 그 객실 타입을 뺀다`() {
        val item = itemA(rates = listOf(rate(oct5, 110_000, 11_000), rate(oct7, 110_000, 11_000)), inventory = fullInventory())

        assertOutOfSpec(item, "숙박일 2026-10-06 의 요금이 없다")
    }

    @Test
    fun `총액에 세금이 포함되지 않았으면 그 객실 타입을 뺀다`() {
        val item = itemB(totalPrice = 400_000, taxIncluded = false, breakfast = true, inventory = fullInventory())

        assertOutOfSpec(item, "총액에 세금이 포함되지 않았다")
    }

    // ── 응답 전체 (ADR-0041) ──

    @Test
    fun `항목 목록이 없으면 공급사 실패다`() {
        assertThatThrownBy { normalizer.normalize(FetchedAvailability(null), mapped, threeNights, requestedCodes) }
            .isInstanceOf(SupplierResponseException::class.java)
    }

    @Test
    fun `한 항목이 빠져도 다른 항목은 그대로 쓴다`() {
        val other = UUID.randomUUID()
        val mappedTwo = mapped + MappedHotel(UUID.randomUUID(), "A-2", "한옥", listOf(MappedRoomType(other, "ONDOL", "온돌", 2)))
        val bad = itemA(rates = fullRates(), inventory = listOf(inv(oct5, 4)))
        val good = itemA(rates = fullRates(), inventory = fullInventory()).copy(hotelCode = "A-2", roomTypeCode = "ONDOL")

        val result = normalizer.normalize(FetchedAvailability(listOf(bad, good)), mappedTwo, threeNights, requestedCodes)

        assertThat(result.available).extracting<UUID> { it.internalRoomTypeId }.containsExactly(other)
        assertThat(result.outOfSpecCount).isEqualTo(1)
    }

    // ── 도우미 ──

    private fun assertOutOfSpec(item: FetchedAvailabilityItem, reason: String) {
        val result = normalizer.normalize(FetchedAvailability(listOf(item)), mapped, threeNights, requestedCodes)

        assertThat(result.available).isEmpty()
        assertThat(result.excluded).singleElement().satisfies({ ex ->
            assertThat(ex.kind).isEqualTo(ExclusionKind.OUT_OF_SPEC)
            assertThat(ex.reason).isEqualTo(reason)
        })
        assertThat(result.outOfSpecCount).isEqualTo(1)
    }

    private fun itemA(rates: List<FetchedDailyRate>, inventory: List<FetchedDailyInventory>) =
        FetchedAvailabilityItem("A-1", "DLX", breakfastIncluded = false, currency = "KRW", pricing = FetchedPricing.Daily(rates), dailyInventory = inventory)

    private fun itemB(totalPrice: Long, taxIncluded: Boolean, breakfast: Boolean, inventory: List<FetchedDailyInventory>) =
        FetchedAvailabilityItem("A-1", "DLX", breakfastIncluded = breakfast, currency = "KRW", pricing = FetchedPricing.Total(totalPrice, taxIncluded), dailyInventory = inventory)

    private fun rate(date: LocalDate, nightly: Long, tax: Long) = FetchedDailyRate(date, nightly, tax)
    private fun inv(date: LocalDate, rooms: Int) = FetchedDailyInventory(date, rooms)
    private fun fullRates() = listOf(rate(oct5, 110_000, 11_000), rate(oct6, 140_000, 14_000), rate(oct7, 110_000, 11_000))
    private fun fullInventory() = listOf(inv(oct5, 4), inv(oct6, 2), inv(oct7, 6))
}
