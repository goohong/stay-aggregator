package com.stayaggregator.supplier

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 공급사마다 주소와 인증 키, 타임아웃을 설정 파일의 설정 블록 하나에 둔다 (ADR-0038, ADR-0039).
 * 검색 전체 타임아웃과 동시 실행 수는 공급사와 무관하게 하나다 (ADR-0045).
 *
 * 저장소에는 Mock 을 가리키는 값만 두고 실제 값은 환경 변수로 덮어쓴다.
 * 앱에는 Mock 의 존재를 아는 분기가 없고, 운영으로 옮길 때 바뀌는 것은 이 설정값뿐이다 (ADR-0005).
 */
@ConfigurationProperties("stay")
data class StayProperties(
    val suppliers: Map<String, Supplier> = emptyMap(),
    val search: Search,
) {
    data class Supplier(
        val baseUrl: String,
        val apiKey: String,
        /**
         * 목록 조회 타임아웃. 이 값이 없으면 앱이 기동에 실패한다.
         * 무응답 공급사 하나가 다른 공급사의 동기화와 이후 주기까지 막지 않게 하려는 것이고,
         * 값은 Mock 의 지연 모드로 측정해 정한다 (ADR-0039).
         */
        val timeout: Duration,
        /**
         * 재고·요금 호출 타임아웃. 목록 조회와 다른 값이다. 고객이 기다리는 요청이라 백그라운드 작업 값을 쓰지 않는다 (ADR-0045).
         * 이 값도 없으면 앱이 기동에 실패한다.
         */
        val availabilityTimeout: Duration,
        /**
         * 연결 타임아웃. 연결 수립에만 건다. 위 두 타임아웃은 연결부터 응답까지 전체에 걸리고, 이 값은 연결에만 걸린다 (ADR-0066).
         * 이 값도 없으면 앱이 기동에 실패한다.
         */
        val connectTimeout: Duration,
    ) {
        init {
            require(connectTimeout.toMillis() >= 1) { "연결 타임아웃이 1ms 미만이다" }
            // 연결 타임아웃이 호출 타임아웃보다 길면 연결 타임아웃이 발동할 일이 없다
            require(connectTimeout < timeout && connectTimeout < availabilityTimeout) {
                "연결 타임아웃($connectTimeout)은 목록·재고 호출 타임아웃($timeout, $availabilityTimeout)보다 짧아야 한다"
            }
        }
    }

    data class Search(
        /**
         * 검색 전체 타임아웃. 검색 한 건 전체에 걸고, 호출 타임아웃과 별개다.
         * 이 안에 끝내지 못한 공급사는 실패로 내보내고 나머지 결과로 응답한다 (ADR-0045).
         */
        val timeout: Duration,
        /** 공급사 하나에서 chunk 호출을 동시에 몇 개 띄우는가. WebClient 기본 풀 크기를 넘지 않게 잡는다 (ADR-0045) */
        val concurrencyPerSupplier: Int,
        /** 5xx·연결 실패처럼 재시도 가능한 실패의 재시도 기준 (ADR-0051, ADR-0068) */
        val retry: RetryPolicy,
        /** 요청 한도 초과(429·`E429`)의 재시도 기준. 공급사가 회복할 시간을 주려고 더 오래 기다리고 덜 재시도한다 (ADR-0068) */
        val throttledRetry: RetryPolicy,
        /** 공급사마다 하나씩 두는 서킷의 기준 (ADR-0056). 모든 공급사가 같은 기준을 쓴다 */
        val circuitBreaker: CircuitBreaker,
    )

    /**
     * 재시도 기준 하나. 첫 호출은 횟수에 들지 않는다. [maxRetries] 가 2 면 최대 세 번 호출하고, 0 이면 재시도하지 않는다 (ADR-0051).
     *
     * 대기는 [minBackoff] 에서 시작해 두 배씩 늘고 [maxBackoff] 를 넘지 않는다. Reactor 가 여기에 [JITTER_FACTOR] 만큼 무작위를 섞는다.
     */
    data class RetryPolicy(
        val maxRetries: Int,
        /** 최소 백오프. 첫 재시도 전에 기다리는 시간 */
        val minBackoff: Duration,
        /** 최대 백오프. 재시도 대기의 상한이고 걸지 않으면 사실상 무한이다 */
        val maxBackoff: Duration,
    ) {
        init {
            require(maxRetries >= 0) { "재시도 횟수가 음수다" }
            require(!minBackoff.isNegative) { "재시도 최소 백오프가 음수다" }
            require(maxBackoff >= minBackoff) { "재시도 최대 백오프가 최소 백오프보다 짧다" }
        }

        /**
         * 호출마다 [callTimeout] 을 다 쓰고 재시도를 모두 썼을 때 chunk 하나가 걸리는 시간의 상한 (ADR-0068 의 관계식).
         *
         * 재시도 i 번째(0 부터)의 대기는 Reactor 가 `min(최소 백오프 × 2^i, 최대 백오프)` 에 무작위를 더해 정하고,
         * 더하는 값은 그 대기의 [JITTER_FACTOR] 배와 `최대 백오프 − 대기` 중 작은 것을 넘지 않는다
         * (reactor-core 3.8.7 `RetryBackoffSpec.generateCompanion` 의 `highBound`). 그래서 한 번의 대기는 `min(최대 백오프, 1.5 × 대기)` 이하다.
         */
        fun worstCase(callTimeout: Duration): Duration {
            var total = callTimeout.multipliedBy(maxRetries + 1L)
            var backoff = minBackoff
            repeat(maxRetries) {
                val jittered = backoff.plusMillis((backoff.toMillis() * JITTER_FACTOR).toLong())
                total = total.plus(minOf(jittered, maxBackoff))
                backoff = minOf(backoff.multipliedBy(2), maxBackoff)
            }
            return total
        }

        companion object {
            /** 대기에 섞는 무작위의 비율. Reactor 의 기본값과 같고, [worstCase] 가 이 값을 전제하므로 검색이 명시적으로 넘긴다 */
            const val JITTER_FACTOR = 0.5
        }
    }

    /**
     * 서킷을 여닫는 기준 (ADR-0056). 값의 근거는 ADR-0068 에 있다.
     * 세는 단위는 호출 한 번이 아니라 **재시도까지 거친 chunk 하나의 최종 결과**다.
     */
    data class CircuitBreaker(
        /** 실패율 임계값(%). 최근 chunk 중 실패한 비율이 이 값 이상이면 연다 */
        val failureRateThreshold: Float,
        /** 슬라이딩 윈도 크기. 실패율을 계산할 최근 chunk 수 */
        val slidingWindowSize: Int,
        /** 최소 호출 수. 이만큼 chunk 가 쌓이기 전에는 실패율을 계산하지 않는다. 몇 번 실패로 바로 열리지 않게 한다 */
        val minimumNumberOfCalls: Int,
        /** 열림(OPEN) 상태 유지 시간. 이 시간 동안은 호출하지 않는다 */
        val waitDurationInOpenState: Duration,
        /** 반열림(HALF_OPEN) 상태에서 허용하는 chunk 호출 수 */
        val permittedNumberOfCallsInHalfOpenState: Int,
    ) {
        init {
            require(failureRateThreshold > 0 && failureRateThreshold <= 100) { "실패율 임계값은 0 초과 100 이하여야 한다" }
            require(slidingWindowSize >= 1) { "슬라이딩 윈도 크기가 1 미만이다" }
            require(minimumNumberOfCalls >= 1) { "최소 호출 수가 1 미만이다" }
            require(permittedNumberOfCallsInHalfOpenState >= 1) { "반열림 상태에서 허용하는 호출 수가 1 미만이다" }
            require(waitDurationInOpenState.toMillis() >= 1) { "열림 상태 유지 시간이 1ms 미만이다" }
        }
    }

    init {
        // 검색 전체 타임아웃으로 취소된 chunk 는 서킷이 실패로 세지 않는다. 재시도까지 다 쓴 chunk 가 그보다 먼저 끝나야
        // 무응답이거나 계속 실패하는 공급사가 실패로 세어져 서킷이 열린다 (ADR-0056, ADR-0068)
        suppliers.forEach { (id, supplier) ->
            listOf("재시도" to search.retry, "요청 한도 초과 재시도" to search.throttledRetry).forEach { (name, policy) ->
                val worstCase = policy.worstCase(supplier.availabilityTimeout)
                require(worstCase < search.timeout) {
                    "공급사 $id 의 chunk 하나가 ${name}까지 다 쓰면 최대 $worstCase 걸린다(재고·요금 호출 타임아웃 ${supplier.availabilityTimeout}). 검색 전체 타임아웃(${search.timeout})보다 짧아야 한다"
                }
            }
        }
    }

    fun of(supplierId: String): Supplier =
        suppliers[supplierId]
            ?: error("공급사 설정이 없다: stay.suppliers.$supplierId")
}
