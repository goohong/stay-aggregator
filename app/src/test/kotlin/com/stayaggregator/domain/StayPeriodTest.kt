package com.stayaggregator.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 숙박 구간의 불변식과 날짜 목록 규칙을 본다 (ADR-0043).
 */
class StayPeriodTest {

    private val oct5 = LocalDate.of(2026, 10, 5)
    private val oct8 = LocalDate.of(2026, 10, 8)

    @Test
    fun `날짜 목록에 체크아웃일은 들어가지 않는다`() {
        val period = StayPeriod(checkIn = oct5, checkOut = oct8)

        assertThat(period.nightDates()).containsExactly(
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 7),
        )
    }

    @Test
    fun `박수는 날짜 목록의 크기와 같다`() {
        val period = StayPeriod(checkIn = oct5, checkOut = oct8)

        assertThat(period.nights).isEqualTo(3)
        assertThat(period.nights).isEqualTo(period.nightDates().size)
    }

    @Test
    fun `1박이면 날짜는 체크인일 하나다`() {
        val period = StayPeriod(checkIn = oct5, checkOut = oct5.plusDays(1))

        assertThat(period.nightDates()).containsExactly(oct5)
    }

    @Test
    fun `체크아웃일이 체크인일과 같으면 만들 수 없다`() {
        assertThatThrownBy { StayPeriod(checkIn = oct5, checkOut = oct5) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("체크아웃일이 체크인일보다 뒤가 아니다")
    }

    @Test
    fun `체크아웃일이 체크인일보다 앞이면 만들 수 없다`() {
        assertThatThrownBy { StayPeriod(checkIn = oct8, checkOut = oct5) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("체크아웃일이 체크인일보다 뒤가 아니다")
    }
}
