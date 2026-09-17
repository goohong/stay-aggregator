package com.stayaggregator.supplier

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * 설정끼리의 관계를 설정 객체가 지키는지 본다 (ADR-0041).
 */
class StayPropertiesTest {

    @Test
    fun `호출 하나의 타임아웃이 검색 시간 한계보다 길면 만들 수 없다`() {
        // 한계로 취소된 묶음은 서킷이 세지 않아, 이 관계가 깨지면 무응답 공급사에 서킷이 열리지 않는다 (ADR-0056)
        assertThatThrownBy { properties(availabilityTimeout = Duration.ofSeconds(8), budget = Duration.ofSeconds(8)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("검색 시간 한계")
    }

    @Test
    fun `재시도 최대 대기가 기준 대기보다 짧으면 만들 수 없다`() {
        assertThatThrownBy { search(min = Duration.ofSeconds(1), max = Duration.ofMillis(100)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("재시도 최대 대기가 기준 대기보다 짧다")
    }

    private fun properties(availabilityTimeout: Duration, budget: Duration) =
        StayProperties(
            suppliers = mapOf("a" to StayProperties.Supplier("http://localhost", "k", Duration.ofSeconds(30), availabilityTimeout)),
            search = search(budget = budget),
        )

    private fun search(budget: Duration = Duration.ofSeconds(8), min: Duration = Duration.ofMillis(100), max: Duration = Duration.ofSeconds(1)) =
        StayProperties.Search(
            budget = budget,
            concurrencyPerSupplier = 4,
            maxRetries = 2,
            retryMinBackoff = min,
            retryMaxBackoff = max,
            circuitBreaker = StayProperties.CircuitBreaker(50f, 20, 10, Duration.ofSeconds(30), 3),
        )
}
