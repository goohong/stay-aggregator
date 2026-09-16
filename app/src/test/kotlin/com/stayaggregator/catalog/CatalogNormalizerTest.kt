package com.stayaggregator.catalog

import org.assertj.core.api.Assertions.assertThat
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
