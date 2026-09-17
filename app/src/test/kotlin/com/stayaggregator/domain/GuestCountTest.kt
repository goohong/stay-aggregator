package com.stayaggregator.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 인원의 불변식을 본다 (ADR-0048). 막는 것은 값으로 성립하지 않는 것뿐이다.
 */
class GuestCountTest {

    @Test
    fun `성인 0 에 아동 1 이상이면 만들어진다`() {
        val guests = GuestCount(adults = 0, children = 2)

        assertThat(guests.total).isEqualTo(2)
    }

    @Test
    fun `둘 다 0 이면 만들 수 없다`() {
        assertThatThrownBy { GuestCount(adults = 0, children = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("인원이 0명이다")
    }

    @Test
    fun `성인이 음수면 만들 수 없다`() {
        assertThatThrownBy { GuestCount(adults = -1, children = 2) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("성인 인원이 음수다")
    }

    @Test
    fun `아동이 음수면 만들 수 없다`() {
        assertThatThrownBy { GuestCount(adults = 2, children = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("아동 인원이 음수다")
    }
}
