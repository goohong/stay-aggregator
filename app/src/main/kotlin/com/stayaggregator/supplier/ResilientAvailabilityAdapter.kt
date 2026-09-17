package com.stayaggregator.supplier

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.time.Duration

/**
 * 재고·요금 어댑터 하나를 감싸 재시도·서킷·지표를 붙인다 (Decorator, ADR-0071).
 *
 * 같은 [AvailabilityAdapter] 를 구현하므로 consumer 는 감싸진 것을 받았다는 사실을 모른다. 검색이든 앞으로의 예약 대행이든
 * 공급사 호출을 둘러싼 정책은 여기 한 곳이다. 전에는 검색 서비스 안에 있어 두 번째 consumer 가 복사해야 했다.
 *
 * 순서는 안에서 밖으로 재시도 → 서킷 → 지표다.
 * - 재시도는 **공급사 호출까지만**이다. 응답을 우리 형태로 바꾸다 난 오류는 재시도해도 같고, 일시적인 실패로 표시되지도 않는다 (ADR-0051)
 * - 서킷은 재시도 바깥이라 순간적인 실패는 재시도가 먼저 흡수하고, 다 실패한 호출만 센다 (ADR-0056).
 *   호출이 취소될 때(검색 전체 타임아웃) 받은 허가를 돌려주는 일도 이 연산자가 한다 (ADR-0057)
 * - 지표는 서킷과 같은 단위(재시도까지 거친 최종 결과)로 센다. 취소된 호출은 끝나지 않아 세지 않는다 (ADR-0060)
 *
 * 서킷이 열려 거절된 것은 resilience4j 의 예외가 아니라 [SupplierFailure.CircuitOpen] 으로 바꾼다. consumer 가 라이브러리 타입을 모르게 한다.
 */
class ResilientAvailabilityAdapter(
    private val delegate: AvailabilityAdapter,
    private val breaker: CircuitBreaker,
    private val retry: StayProperties.RetryPolicy,
    private val throttledRetry: StayProperties.RetryPolicy,
    private val metrics: SupplierCallMetrics,
) : AvailabilityAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    override val supplierId: String get() = delegate.supplierId

    override fun fetchAvailability(request: AvailabilityRequest): Mono<FetchedAvailability> =
        Mono.defer {
            val started = System.nanoTime()
            delegate.fetchAvailability(request)
                .retryWhen(retryTransient(request))
                .transformDeferred(CircuitBreakerOperator.of(breaker))
                .onErrorMap(CallNotPermittedException::class.java) { e ->
                    SupplierFailure.CircuitOpen("공급사 $supplierId 의 서킷이 열려 있어 호출하지 않았다", e)
                }
                .doOnSuccess { metrics.recordAvailability(supplierId, elapsedSince(started), null) }
                .doOnError { metrics.recordAvailability(supplierId, elapsedSince(started), it) }
        }

    /**
     * 재시도 가능한 실패만 재시도한다 (ADR-0051). 요청 한도 초과는 더 오래 기다리고 덜 재시도한다 (ADR-0068).
     *
     * 기다리는 시간은 실패 종류마다 Reactor 의 `Retry.backoff` 가 정한다. 지수 백오프에 무작위를 섞는 것도 그것이 한다.
     * 여기서 더하는 것은 **횟수 한도**뿐이다. 한 호출이 지금까지 만난 실패 종류 중 가장 적은 재시도 횟수를 한도로 한다.
     * 503 뒤에 429 가 와도 최악의 시간이 종류별 최악 중 큰 값을 넘지 않아, 설정 객체가 검사한 관계식이 그대로 성립한다 (ADR-0068 의 (A)).
     *
     * 다 써도 실패하면 **원래 실패를 그대로** 올린다. 다른 예외로 감싸면 consumer 가 보는 예외가 달라져 ADR-0027 이 정한 "한 가지 실패"가 깨진다.
     */
    private fun retryTransient(request: AvailabilityRequest): Retry {
        val general = backoff(retry, request)
        val throttled = backoff(throttledRetry, request)
        return Retry.from { signals ->
            // Reactor 가 구독마다 이 함수를 다시 부르므로 한도는 호출마다 새로 시작한다
            var limit = Long.MAX_VALUE
            signals.concatMap { signal ->
                val failure = signal.failure()
                if (failure !is SupplierFailure || !failure.transient) {
                    return@concatMap Mono.error<Long>(failure)
                }
                val spec = if (failure is SupplierFailure.Throttled) throttled else general
                limit = minOf(limit, spec.maxAttempts)
                if (signal.totalRetries() >= limit) Mono.error(failure) else spec.generateCompanion(Flux.just(signal.copy()))
            }
        }
    }

    private fun backoff(policy: StayProperties.RetryPolicy, request: AvailabilityRequest): RetryBackoffSpec =
        Retry.backoff(policy.maxRetries.toLong(), policy.minBackoff)
            .maxBackoff(policy.maxBackoff)
            // 설정이 검색 전체 타임아웃과의 관계를 이 비율로 계산한다 (StayProperties.RetryPolicy.worstCase)
            .jitter(StayProperties.RetryPolicy.JITTER_FACTOR)
            .doBeforeRetry { signal ->
                log.info(
                    "공급사 호출 재시도 supplier={} 숙소={}개 {}번째 최소백오프={} 이유={}",
                    supplierId,
                    request.hotelCodes.size,
                    signal.totalRetries() + 1,
                    policy.minBackoff,
                    signal.failure().message,
                )
            }
            .onRetryExhaustedThrow { _, signal -> signal.failure() }

    private fun elapsedSince(startedNanos: Long): Duration = Duration.ofNanos(System.nanoTime() - startedNanos)
}

/**
 * 감싼 재고·요금 어댑터 목록. consumer 는 `List<AvailabilityAdapter>` 대신 이것을 주입받는다.
 *
 * 원본 어댑터도 `AvailabilityAdapter` 빈이라 `List<AvailabilityAdapter>` 를 주입받으면 감싸지 않은 것이 섞인다.
 * 그래서 감싼 목록에 자기 타입을 준다. 감싸는 곳은 [SupplierResilienceConfig] 한 곳이다.
 */
class ResilientAvailabilityAdapters(val adapters: List<AvailabilityAdapter>) {
    companion object {
        fun wrap(
            adapters: List<AvailabilityAdapter>,
            breakers: SupplierCircuitBreakers,
            metrics: SupplierCallMetrics,
            search: StayProperties.Search,
        ) = ResilientAvailabilityAdapters(
            adapters.map { ResilientAvailabilityAdapter(it, breakers.of(it.supplierId), search.retry, search.throttledRetry, metrics) },
        )
    }
}

@Configuration(proxyBeanMethods = false)
class SupplierResilienceConfig {
    @Bean
    fun resilientAvailabilityAdapters(
        adapters: List<AvailabilityAdapter>,
        breakers: SupplierCircuitBreakers,
        metrics: SupplierCallMetrics,
        properties: StayProperties,
    ): ResilientAvailabilityAdapters = ResilientAvailabilityAdapters.wrap(adapters, breakers, metrics, properties.search)
}
