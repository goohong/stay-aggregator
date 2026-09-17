package com.stayaggregator.supplier

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 요청 하나에 담는 숙소 코드가 공급사 상한을 넘지 않는지 요청 객체가 지키는지 본다 (ADR-0045, ADR-0041).
 */
class AvailabilityRequestTest {

    private val period = StayPeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6))
    private val guests = GuestCount(adults = 2, children = 0)

    @Test
    fun `숙소 코드 50개까지는 만들어진다`() {
        val request = AvailabilityRequest(codes(50), period, guests)

        assertThat(request.hotelCodes).hasSize(50)
    }

    @Test
    fun `숙소 코드가 50개를 넘으면 만들 수 없다`() {
        assertThatThrownBy { AvailabilityRequest(codes(51), period, guests) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("숙소 코드가 50개를 넘는다: 51")
    }

    @Test
    fun `숙소 코드가 없으면 만들 수 없다`() {
        assertThatThrownBy { AvailabilityRequest(emptyList(), period, guests) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("숙소 코드가 없다")
    }

    private fun codes(n: Int) = List(n) { "H-$it" }
}
