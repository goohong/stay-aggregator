package com.stayaggregator.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 금액의 불변식을 본다 (ADR-0042, ADR-0027 의 요금 표).
 */
class MoneyTest {

    @Test
    fun `0원은 만들어진다`() {
        val money = Money(0, "KRW")

        assertThat(money.amount).isZero()
    }

    @Test
    fun `음수 금액은 만들 수 없다`() {
        assertThatThrownBy { Money(-1, "KRW") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("금액이 음수다")
    }

    @Test
    fun `통화가 비어 있으면 만들 수 없다`() {
        assertThatThrownBy { Money(1000, "") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ISO 4217 형식이 아니다")
    }

    @Test
    fun `통화가 세 글자 대문자가 아니면 만들 수 없다`() {
        assertThatThrownBy { Money(1000, "krw") }.hasMessageContaining("ISO 4217 형식이 아니다")
        assertThatThrownBy { Money(1000, "KRWX") }.hasMessageContaining("ISO 4217 형식이 아니다")
    }

    @Test
    fun `거부할 때 금액과 통화 중 무엇이 틀렸는지 알려 준다`() {
        // 받는 쪽이 사유 문장이 아니라 이 값으로 격리 기록의 문제 값을 정한다 (ADR-0067)
        assertThatThrownBy { Money(-1, "KRW") }
            .isInstanceOfSatisfying(Money.Rejected::class.java) { assertThat(it.field).isEqualTo(Money.Field.AMOUNT) }
        assertThatThrownBy { Money(1000, "krw") }
            .isInstanceOfSatisfying(Money.Rejected::class.java) { assertThat(it.field).isEqualTo(Money.Field.CURRENCY) }
    }

    @Test
    fun `같은 통화끼리 더하면 액수가 더해진다`() {
        val sum = Money(121_000, "KRW") + Money(154_000, "KRW") + Money(121_000, "KRW")

        assertThat(sum).isEqualTo(Money(396_000, "KRW"))
    }

    @Test
    fun `통화가 다르면 더할 수 없다`() {
        assertThatThrownBy { Money(1000, "KRW") + Money(1, "USD") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("통화가 다른 금액은 더할 수 없다")
    }
}
