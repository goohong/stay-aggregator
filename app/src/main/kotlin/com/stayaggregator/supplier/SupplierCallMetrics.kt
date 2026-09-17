package com.stayaggregator.supplier

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 공급사 호출을 공급사와 결과로 나눠 센다 (ADR-0060).
 *
 * 성공률·타임아웃 비율은 저장하지 않는다. 수집하는 쪽이 결과별 횟수로 나눠 계산한다.
 * 가짓수를 작게 두려고 결과는 몇 가지로만 나눈다. 더 잘게(HTTP 상태별) 보려면 로그의 원인 클래스 이름을 본다.
 *
 * 목록 동기화와 검색이 함께 쓰므로 둘이 모두 볼 수 있는 이 패키지에 둔다 (ADR-0040).
 */
@Component
class SupplierCallMetrics(private val registry: MeterRegistry) {

    /** 검색의 chunk 호출 하나. 재시도까지 거친 최종 결과다 */
    fun recordAvailability(supplierId: String, elapsed: Duration, error: Throwable?) =
        timer(AVAILABILITY, supplierId, outcomeOf(error)).record(elapsed)

    /** 목록 동기화의 공급사 하나 */
    fun recordCatalog(supplierId: String, elapsed: Duration, error: Throwable?) =
        timer(CATALOG, supplierId, outcomeOf(error)).record(elapsed)

    private fun timer(name: String, supplierId: String, outcome: Outcome): Timer =
        Timer.builder(name)
            .tag("supplier", supplierId)
            .tag("outcome", outcome.tag)
            .register(registry)

    enum class Outcome(val tag: String) {
        SUCCESS("success"),
        TIMEOUT("timeout"),
        SUPPLIER_FAILURE("supplier_failure"),
        CIRCUIT_OPEN("circuit_open"),

        /** 공급사 실패가 아닌 내부 오류. 공급사 성공률에 섞지 않으려고 따로 둔다 */
        INTERNAL_ERROR("internal_error"),
    }

    companion object {
        const val AVAILABILITY = "stay.supplier.availability"
        const val CATALOG = "stay.supplier.catalog"

        fun outcomeOf(error: Throwable?): Outcome =
            when {
                error == null -> Outcome.SUCCESS
                error is CallNotPermittedException -> Outcome.CIRCUIT_OPEN
                error is SupplierResponseException && error.timedOut -> Outcome.TIMEOUT
                error is SupplierResponseException -> Outcome.SUPPLIER_FAILURE
                else -> Outcome.INTERNAL_ERROR
            }
    }
}
