package com.stayaggregator.supplier

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 공급사마다 주소와 인증 키, 타임아웃을 설정 파일의 한 묶음으로 둔다 (ADR-0038, ADR-0039).
 * 검색 한 건 전체의 시간 한계와 동시 실행 수는 공급사와 무관하게 하나다 (ADR-0045).
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
         * 목록 조회를 기다리는 한계. 이 값이 없으면 앱이 뜨지 않는다.
         * 무응답 공급사 하나가 다른 공급사의 동기화와 이후 주기까지 막지 않게 하려는 것이고,
         * 값은 Mock 의 지연 모드로 측정해 정한다 (ADR-0039).
         */
        val timeout: Duration,
        /**
         * 재고·요금 호출 하나를 기다리는 한계. 목록 조회와 다른 값이다. 고객이 기다리는 요청이라 배경 작업 값을 쓰지 않는다 (ADR-0045).
         * 이 값도 없으면 앱이 뜨지 않는다.
         */
        val availabilityTimeout: Duration,
        /**
         * 연결 수립만 기다리는 한계. 위 두 타임아웃은 연결부터 응답까지 전체에 걸리고, 이 값은 연결에만 걸린다 (ADR-0066).
         * 이 값도 없으면 앱이 뜨지 않는다.
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
         * 검색 한 건 전체의 시간 한계. 개별 호출 타임아웃과 별개다.
         * 이 안에 끝내지 못한 공급사는 실패로 내보내고 나머지 결과로 응답한다 (ADR-0045).
         */
        val budget: Duration,
        /** 공급사 하나에서 묶음 호출을 동시에 몇 개 띄우는가. WebClient 기본 풀 크기를 넘지 않게 잡는다 (ADR-0045) */
        val concurrencyPerSupplier: Int,
        /**
         * 일시적인 실패를 다시 부르는 최대 횟수. 첫 호출은 여기 들지 않는다. 2 면 최대 세 번 부른다 (ADR-0051).
         * 0 이면 재시도하지 않는다.
         */
        val maxRetries: Int,
        /** 첫 재시도 전에 기다리는 기준 시간. 여기서부터 두 배씩 늘고 Reactor 가 무작위를 섞는다 (ADR-0051) */
        val retryMinBackoff: Duration,
        /** 재시도 대기의 상한. 걸지 않으면 사실상 무한이다 (ADR-0051) */
        val retryMaxBackoff: Duration,
        /** 공급사마다 하나씩 두는 서킷의 기준 (ADR-0056). 모든 공급사가 같은 기준을 쓴다 */
        val circuitBreaker: CircuitBreaker,
    ) {
        init {
            require(maxRetries >= 0) { "재시도 횟수가 음수다" }
            require(!retryMinBackoff.isNegative) { "재시도 기준 대기가 음수다" }
            require(retryMaxBackoff >= retryMinBackoff) { "재시도 최대 대기가 기준 대기보다 짧다" }
        }
    }

    /**
     * 서킷을 여닫는 기준. 값은 아직 임시값이고 선택 항목을 마친 뒤 근거를 붙여 정한다 (ADR-0056).
     * 세는 단위는 호출 한 번이 아니라 **재시도까지 거친 묶음 하나의 최종 결과**다.
     */
    data class CircuitBreaker(
        /** 최근 묶음 중 실패한 비율이 이 퍼센트 이상이면 연다 */
        val failureRateThreshold: Float,
        /** 실패율을 계산할 최근 묶음 수 */
        val slidingWindowSize: Int,
        /** 이만큼 묶음이 쌓이기 전에는 실패율을 계산하지 않는다. 몇 번 실패로 바로 열리지 않게 한다 */
        val minimumNumberOfCalls: Int,
        /** 연 뒤 이 시간 동안은 부르지 않는다 */
        val waitDurationInOpenState: Duration,
        /** 이 시간이 지난 뒤 시험 삼아 부르는 묶음 수 */
        val permittedNumberOfCallsInHalfOpenState: Int,
    ) {
        init {
            require(failureRateThreshold > 0 && failureRateThreshold <= 100) { "실패율 기준은 0 초과 100 이하여야 한다" }
            require(slidingWindowSize >= 1) { "실패율을 계산할 묶음 수가 1 미만이다" }
            require(minimumNumberOfCalls >= 1) { "최소 묶음 수가 1 미만이다" }
            require(permittedNumberOfCallsInHalfOpenState >= 1) { "시험 삼아 부를 묶음 수가 1 미만이다" }
            require(waitDurationInOpenState.toMillis() >= 1) { "서킷을 열어 두는 시간이 1ms 미만이다" }
        }
    }

    init {
        // 검색 시간 한계로 취소된 묶음은 서킷이 실패로 세지 않는다. 호출 하나의 타임아웃이 한계보다 짧아야
        // 무응답 공급사가 타임아웃 실패로 세어져 서킷이 열린다 (ADR-0056)
        suppliers.forEach { (id, supplier) ->
            require(supplier.availabilityTimeout < search.budget) {
                "공급사 $id 의 재고·요금 타임아웃(${supplier.availabilityTimeout})이 검색 시간 한계(${search.budget})보다 짧아야 한다"
            }
        }
    }

    fun of(supplierId: String): Supplier =
        suppliers[supplierId]
            ?: error("공급사 설정이 없다: stay.suppliers.$supplierId")
}
