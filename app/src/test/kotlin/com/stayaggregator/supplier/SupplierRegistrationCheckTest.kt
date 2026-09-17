package com.stayaggregator.supplier

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * 한 인터페이스만 구현한 공급사, 설정과 어댑터가 어긋난 공급사가 기동에서 드러나는지 (ADR-0072).
 */
class SupplierRegistrationCheckTest {

    private val catalogOnly = object : CatalogAdapter {
        override val supplierId = "c"
        override fun fetchCatalog(): Mono<FetchedCatalog> = Mono.empty()
    }
    private val both = object : SupplierAdapter {
        override val supplierId = "a"
        override fun fetchCatalog(): Mono<FetchedCatalog> = Mono.empty()
        override fun fetchAvailability(request: AvailabilityRequest): Mono<FetchedAvailability> = Mono.empty()
    }

    @Test
    fun `한 인터페이스만 구현한 공급사가 있으면 기동에 실패한다`() {
        assertThatThrownBy { SupplierRegistrationCheck(listOf(both, catalogOnly), listOf(both), properties("a", "c")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("c")
    }

    @Test
    fun `설정 블록만 있고 어댑터가 없는 공급사가 있으면 기동에 실패한다`() {
        assertThatThrownBy { SupplierRegistrationCheck(listOf(both), listOf(both), properties("a", "z")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("z")
    }

    @Test
    fun `어댑터와 설정이 맞으면 통과한다`() {
        SupplierRegistrationCheck(listOf(both), listOf(both), properties("a"))
    }

    private fun properties(vararg ids: String) = StayProperties(
        suppliers = ids.associateWith { StayProperties.Supplier("http://localhost", "k", Duration.ofSeconds(30), Duration.ofSeconds(2), Duration.ofMillis(500)) },
        search = StayProperties.Search(
            timeout = Duration.ofSeconds(8),
            concurrencyPerSupplier = 4,
            retry = StayProperties.RetryPolicy(2, Duration.ofMillis(100), Duration.ofSeconds(1)),
            throttledRetry = StayProperties.RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(1_500)),
            circuitBreaker = StayProperties.CircuitBreaker(50f, 20, 8, Duration.ofSeconds(30), 4),
        ),
    )
}
