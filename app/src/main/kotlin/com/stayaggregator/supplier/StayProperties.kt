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
    )

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
    ) {
        init {
            require(maxRetries >= 0) { "재시도 횟수가 음수다" }
        }
    }

    fun of(supplierId: String): Supplier =
        suppliers[supplierId]
            ?: error("공급사 설정이 없다: stay.suppliers.$supplierId")
}
