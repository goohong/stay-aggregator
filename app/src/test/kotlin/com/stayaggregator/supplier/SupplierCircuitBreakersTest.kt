package com.stayaggregator.supplier

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * 설정의 서킷 기준이 빠짐없이 서킷에 들어가는지 본다 (ADR-0056, ADR-0068).
 */
class SupplierCircuitBreakersTest {

    @Test
    fun `설정한 서킷 기준이 그대로 공급사 서킷에 들어간다`() {
        val properties = StayProperties(
            suppliers = emptyMap(),
            search = StayProperties.Search(
                timeout = Duration.ofSeconds(8),
                concurrencyPerSupplier = 4,
                retry = StayProperties.RetryPolicy(2, Duration.ofMillis(100), Duration.ofSeconds(1)),
                throttledRetry = StayProperties.RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(1_500)),
                circuitBreaker = StayProperties.CircuitBreaker(50f, 20, 8, Duration.ofSeconds(30), 4),
            ),
        )

        val config = SupplierCircuitBreakers(properties).of("a").circuitBreakerConfig

        assertThat(config.failureRateThreshold).isEqualTo(50f)
        assertThat(config.slidingWindowSize).isEqualTo(20)
        assertThat(config.minimumNumberOfCalls).isEqualTo(8)
        assertThat(config.waitIntervalFunctionInOpenState.apply(1)).isEqualTo(30_000L)
        assertThat(config.permittedNumberOfCallsInHalfOpenState).isEqualTo(4)
    }
}
