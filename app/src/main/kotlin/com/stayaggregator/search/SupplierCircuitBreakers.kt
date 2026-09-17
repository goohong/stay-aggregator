package com.stayaggregator.search

import com.stayaggregator.supplier.StayProperties
import com.stayaggregator.supplier.SupplierResponseException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 공급사마다 하나씩 서킷을 둔다 (ADR-0056).
 *
 * resilience4j 의 Spring Boot 4 모듈이 릴리스되지 않아 설정 파일 연동이 없다. 그래서 [StayProperties] 에서 기준을 읽어 직접 만든다 (ADR-0057).
 * 검색에서만 쓴다. 목록 동기화는 하루 한 번 도는 배경 작업이라 끊어서 아낄 시간이 없다 (ADR-0056).
 *
 * **실패로 세는 것은 공급사 실패뿐이다.** 공급사 실패가 아닌 오류(우리 쪽 결함)로 멀쩡한 공급사를 끊지 않는다.
 */
@Component
class SupplierCircuitBreakers(properties: StayProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val registry: CircuitBreakerRegistry = CircuitBreakerRegistry.of(config(properties.search.circuitBreaker))

    /** 상태 변화 로그를 이미 붙인 공급사. 같은 서킷에 두 번 붙지 않게 한다 */
    private val registeredForLogging = ConcurrentHashMap.newKeySet<String>()

    /** 그 공급사의 서킷. 처음 부를 때 만들고 그 뒤로는 같은 것을 준다 */
    fun of(supplierId: String): CircuitBreaker =
        registry.circuitBreaker(supplierId).also { breaker ->
            if (registeredForLogging.add(supplierId)) {
                breaker.eventPublisher.onStateTransition { event ->
                    log.warn("공급사 서킷 상태 변경 supplier={} {}", supplierId, event.stateTransition)
                }
            }
        }

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
            .recordException { it is SupplierResponseException }
            .build()
}
