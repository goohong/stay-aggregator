package com.stayaggregator.mapping

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 값 검증이 생성자에 있는지 본다 (ADR-0041).
 *
 * 정규화를 거치지 않고 직접 만들어도 조건을 어긴 객체가 생기지 않아야 한다.
 * 정규화 테스트는 "뺀 것과 사유"를 보고, 여기서는 "만들 수 없다"를 본다.
 */
class NormalizedHotelTest {

    private val roomType = NormalizedRoomType("DLX", "디럭스", 2)

    @Test
    fun `값이 온전하면 만들어진다`() {
        val hotel = NormalizedHotel("A-1", "강변 호텔", listOf(roomType))

        assertThat(hotel.roomTypes).containsExactly(roomType)
    }

    @Test
    fun `숙소 코드가 비어 있으면 만들 수 없다`() {
        assertThatThrownBy { NormalizedHotel(" ", "강변 호텔", listOf(roomType)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("숙소 코드가 없다")
    }

    @Test
    fun `숙소명이 비어 있으면 만들 수 없다`() {
        assertThatThrownBy { NormalizedHotel("A-1", "", listOf(roomType)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("숙소명이 없다")
    }

    @Test
    fun `팔 수 있는 객실 타입이 없으면 만들 수 없다`() {
        assertThatThrownBy { NormalizedHotel("A-1", "강변 호텔", emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("팔 수 있는 객실 타입이 없다")
    }

    @Test
    fun `객실 타입의 최대 수용 인원이 1 미만이면 만들 수 없다`() {
        assertThatThrownBy { NormalizedRoomType("DLX", "디럭스", 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("최대 수용 인원이 1 미만이다")
    }

    @Test
    fun `객실 타입 코드나 이름이 비어 있으면 만들 수 없다`() {
        assertThatThrownBy { NormalizedRoomType("", "디럭스", 2) }
            .hasMessage("객실 타입 코드가 없다")
        assertThatThrownBy { NormalizedRoomType("DLX", " ", 2) }
            .hasMessage("객실 타입명이 없다")
    }
}
