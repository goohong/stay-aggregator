package com.stayaggregator.catalog

import com.stayaggregator.supplier.FetchedCatalog
import com.stayaggregator.supplier.FetchedHotel
import com.stayaggregator.supplier.FetchedRoomType
import com.stayaggregator.supplier.SupplierResponseException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 정규화가 ADR-0027 의 두 번째·세 번째 질문대로 가르는지 확인한다.
 * 값을 정할 수 없는 항목은 빠지고, 뺀 사실이 결과값에 남는다 (ADR-0039).
 */
class CatalogNormalizerTest {

    private val normalizer = CatalogNormalizer()

    @Test
    fun `값이 온전한 숙소와 객실 타입은 그대로 쓴다`() {
        val fetched = catalog(
            hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2)),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).singleElement()
            .satisfies({ hotel ->
                assertThat(hotel.code).isEqualTo("A-1")
                assertThat(hotel.name).isEqualTo("강변 호텔")
                assertThat(hotel.roomTypes).singleElement()
                    .satisfies({ roomType ->
                        assertThat(roomType.code).isEqualTo("DLX")
                        assertThat(roomType.maxOccupancy).isEqualTo(2)
                    })
            })
        assertThat(normalized.excluded).isEmpty()
    }

    @Test
    fun `숙소 코드가 없으면 그 숙소를 빼고 뺀 사실을 남긴다`() {
        val fetched = catalog(
            hotel(null, "이름만 있는 숙소", roomType("DLX", "디럭스", 2)),
            hotel("A-2", "정상 숙소", roomType("STD", "스탠다드", 2)),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).extracting<String> { it.code }.containsExactly("A-2")
        assertThat(normalized.excluded).singleElement()
            .satisfies({ excluded -> assertThat(excluded.reason).contains("숙소 코드") })
    }

    @Test
    fun `최대 수용 인원이 없으면 그 객실 타입만 빼고 숙소는 남긴다`() {
        val fetched = catalog(
            hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", null), roomType("STD", "스탠다드", 2)),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).singleElement()
            .satisfies({ hotel -> assertThat(hotel.roomTypes).extracting<String> { it.code }.containsExactly("STD") })
        assertThat(normalized.excluded).singleElement()
            .satisfies({ excluded ->
                assertThat(excluded.hotelCode).isEqualTo("A-1")
                assertThat(excluded.roomTypeCode).isEqualTo("DLX")
            })
    }

    @Test
    fun `최대 수용 인원이 1 미만이면 그 객실 타입을 뺀다`() {
        val fetched = catalog(
            hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 0), roomType("STD", "스탠다드", 2)),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).singleElement()
            .satisfies({ hotel -> assertThat(hotel.roomTypes).extracting<String> { it.code }.containsExactly("STD") })
        assertThat(normalized.excluded).singleElement()
            .satisfies({ excluded -> assertThat(excluded.reason).contains("1 미만") })
    }

    @Test
    fun `숙소 목록을 담는 필드가 없으면 공급사 실패가 된다`() {
        // 빈 목록은 "없다"는 말이지만, 필드가 없는 것은 아무 말도 아니다 (ADR-0041)
        assertThatThrownBy { normalizer.normalize(FetchedCatalog("a", null)) }
            .isInstanceOf(SupplierResponseException::class.java)
            .hasMessageContaining("숙소 목록이 없다")
    }

    @Test
    fun `객실 타입 목록이 없는 숙소와 빈 숙소를 모두 뺀다`() {
        val fetched = catalog(
            FetchedHotel("A-1", "목록이 없는 숙소", null),
            FetchedHotel("A-2", "목록이 빈 숙소", emptyList()),
            hotel("A-3", "정상 숙소", roomType("DLX", "디럭스", 2)),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).extracting<String> { it.code }.containsExactly("A-3")
        assertThat(normalized.excluded).hasSize(2)
            .allSatisfy { excluded -> assertThat(excluded.reason).contains("팔 수 있는 객실 타입이 없다") }
    }

    @Test
    fun `객실 타입이 하나뿐이고 그것이 빠지면 숙소까지 빠진다`() {
        val fetched = catalog(
            hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", null)),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).isEmpty()
        // 순서는 구현 세부라 고정하지 않는다
        assertThat(normalized.excluded).extracting<String> { it.reason }
            .containsExactlyInAnyOrder("최대 수용 인원이 없다", "팔 수 있는 객실 타입이 없다")
    }

    @Test
    fun `최대 수용 인원이 없는 것과 1 미만인 것의 사유를 구분한다`() {
        // 없는 값을 0 으로 바꿔 넘기면 "없다"가 "1 미만이다"로 기록되어 로그가 원인을 잘못 짚게 한다 (ADR-0041)
        val fetched = catalog(
            hotel(
                "A-1", "강변 호텔",
                roomType("DLX", "디럭스", null),
                roomType("STD", "스탠다드", 0),
                roomType("TWN", "트윈", 2),
            ),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).singleElement()
            .satisfies({ hotel -> assertThat(hotel.roomTypes).extracting<String> { it.code }.containsExactly("TWN") })
        assertThat(normalized.excluded.map { it.roomTypeCode to it.reason })
            .containsExactlyInAnyOrder(
                "DLX" to "최대 수용 인원이 없다",
                "STD" to "최대 수용 인원이 1 미만이다",
            )
    }

    @Test
    fun `숙소 코드가 없는 숙소가 둘이면 같은 코드로 묶지 않는다`() {
        // 코드가 없는 두 숙소는 같은 코드가 아니다. 묶으면 사유가 "두 번 왔다"로 사실과 달라진다
        val fetched = catalog(
            hotel(null, "이름이 다른 숙소 1", roomType("DLX", "디럭스", 2)),
            hotel(null, "이름이 다른 숙소 2", roomType("STD", "스탠다드", 2)),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).isEmpty()
        assertThat(normalized.excluded).hasSize(2)
            .allSatisfy { excluded -> assertThat(excluded.reason).isEqualTo("숙소 코드가 없다") }
    }

    @Test
    fun `같은 숙소 코드가 같은 값으로 두 번 오면 하나만 쓴다`() {
        val fetched = catalog(
            hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2)),
            hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2)),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).hasSize(1)
        assertThat(normalized.excluded).isEmpty()
    }

    @Test
    fun `같은 숙소 코드가 다른 값으로 두 번 오면 어느 쪽인지 정할 수 없어 뺀다`() {
        val fetched = catalog(
            hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2)),
            hotel("A-1", "다른 이름 호텔", roomType("DLX", "디럭스", 2)),
        )

        val normalized = normalizer.normalize(fetched)

        assertThat(normalized.hotels).isEmpty()
        assertThat(normalized.excluded).singleElement()
            .satisfies({ excluded -> assertThat(excluded.reason).contains("두 번") })
    }

    private fun catalog(vararg hotels: FetchedHotel) = FetchedCatalog("a", hotels.toList())

    private fun hotel(code: String?, name: String?, vararg roomTypes: FetchedRoomType) =
        FetchedHotel(code, name, roomTypes.toList())

    private fun roomType(code: String?, name: String?, maxOccupancy: Int?) =
        FetchedRoomType(code, name, maxOccupancy)
}
