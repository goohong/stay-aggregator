package com.stayaggregator.search

import com.stayaggregator.supplier.StayProperties
import com.stayaggregator.supplier.SupplierResponseException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 공급사마다 하나씩 서킷을 둔다 (ADR-0056).
 *
 * resilience4j 의 Spring Boot 4 모듈이 릴리스되지 않아 설정 파일 연동이 없다. 그래서 [StayProperties] 에서 기준을 읽어 직접 만든다 (ADR-0057).
 * 검색에서만 쓴다. 목록 동기화는 하루 한 번 도는 배경 작업이라 끊어서 아낄 시간이 없다 (ADR-0056).
 *
 * **실패로 세는 것은 공급사 실패뿐이다.** 공급사 실패가 아닌 오류(우리 쪽 결함)는 성공으로도 실패로도 세지 않는다.
 */
@Component
class SupplierCircuitBreakers(properties: StayProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val registry: CircuitBreakerRegistry = CircuitBreakerRegistry.of(config(properties.search.circuitBreaker)).also { registry ->
        // 서킷이 처음 만들어질 때 한 번만 상태 변화 로그를 붙인다. 레지스트리가 그 시점을 알려 준다
        registry.eventPublisher.onEntryAdded { event ->
            val breaker = event.addedEntry
            breaker.eventPublisher.onStateTransition { transition ->
                log.warn("공급사 서킷 상태 변경 supplier={} {}", breaker.name, transition.stateTransition)
            }
        }
    }

    /** 그 공급사의 서킷. 처음 부를 때 만들고 그 뒤로는 같은 것을 준다 */
    fun of(supplierId: String): CircuitBreaker = registry.circuitBreaker(supplierId)

    private fun config(c: StayProperties.CircuitBreaker): CircuitBreakerConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(c.failureRateThreshold)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(c.slidingWindowSize)
            .minimumNumberOfCalls(c.minimumNumberOfCalls)
            .waitDurationInOpenState(c.waitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(c.permittedNumberOfCallsInHalfOpenState)
            // 연 뒤 기다린 시간이 지나면 다음 검색이 올 때 반쯤 열린 상태로 넘어간다.
            // 자동 전환은 켜지 않는다. 켜면 서킷마다 시간을 재는 작업이 따로 돈다
            // 공급사 실패가 아닌 것은 **무시한다.** recordException 으로 거르면 거른 것이 성공으로 세어져 실패율이 희석된다.
            // 문서: "The Predicate must return false, if the exception should count as a success, unless the exception is explicitly ignored"
            .ignoreException { it !is SupplierResponseException }
            .build()
}
