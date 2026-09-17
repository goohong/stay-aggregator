package com.stayaggregator.supplier

import com.stayaggregator.supplier.SupplierCallMetrics.Outcome
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 지표의 결과 태그가 결정한 다섯 값으로 구분되는지 본다 (ADR-0060).
 */
class SupplierCallMetricsTest {

    @Test
    fun `실패 종류마다 결과 태그가 다르다`() {
        assertThat(SupplierCallMetrics.outcomeOf(null)).isEqualTo(Outcome.SUCCESS)
        assertThat(SupplierCallMetrics.outcomeOf(SupplierResponseException("느림", timedOut = true))).isEqualTo(Outcome.TIMEOUT)
        assertThat(SupplierCallMetrics.outcomeOf(SupplierResponseException("503"))).isEqualTo(Outcome.SUPPLIER_FAILURE)
        assertThat(SupplierCallMetrics.outcomeOf(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("a"))))
            .isEqualTo(Outcome.CIRCUIT_OPEN)
    }

    @Test
    fun `공급사 실패가 아닌 내부 오류는 공급사 실패에 섞지 않는다`() {
        assertThat(SupplierCallMetrics.outcomeOf(IllegalStateException("변환 결함"))).isEqualTo(Outcome.INTERNAL_ERROR)
    }
}
