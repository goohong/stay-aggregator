package com.stayaggregator.domain

/**
 * 금액. 액수와 통화를 함께 담는다 (ADR-0042). 통화 없는 액수는 존재하지 않는다.
 *
 * 액수는 통화의 최소 단위 정수다. 두 공급사가 원 단위 정수로 주고, 소수점을 쓰는 통화가 오면 그때 다시 본다 (ADR-0042).
 *
 * **0 은 막지 않는다.** 0원 요금은 값으로 성립하고 스펙도 막지 않는다. 음수는 요금으로 성립하지 않아 막는다 (ADR-0027 의 요금 표).
 * 통화는 스펙이 ISO 4217 코드라고 하므로 세 글자 대문자인지만 본다. 실제 통화 목록과 대조하지는 않는다.
 */
data class Money(val amount: Long, val currency: String) {
    /** 검사한 값 중 무엇이 틀렸나. 거부를 받은 쪽이 문장이 아니라 이 값으로 원인을 가른다 (ADR-0067) */
    enum class Field { AMOUNT, CURRENCY }

    class Rejected(override val field: Field, message: String) : com.stayaggregator.domain.Rejected(field, message)

    init {
        if (amount < 0) throw Rejected(Field.AMOUNT, "금액이 음수다")
        if (!CURRENCY_CODE.matches(currency)) throw Rejected(Field.CURRENCY, "통화 코드가 ISO 4217 형식이 아니다: '$currency'")
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "통화가 다른 금액은 더할 수 없다: $currency, ${other.currency}" }
        return Money(amount + other.amount, currency)
    }

    companion object {
        private val CURRENCY_CODE = Regex("[A-Z]{3}")
    }
}
