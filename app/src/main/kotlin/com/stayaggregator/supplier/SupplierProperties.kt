package com.stayaggregator.supplier

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 공급사마다 주소와 인증 키, 타임아웃을 설정 파일의 한 묶음으로 둔다 (ADR-0038, ADR-0039).
 *
 * 저장소에는 Mock 을 가리키는 값만 두고 실제 값은 환경 변수로 덮어쓴다.
 * 앱에는 Mock 의 존재를 아는 분기가 없고, 운영으로 옮길 때 바뀌는 것은 이 설정값뿐이다 (ADR-0005).
 */
@ConfigurationProperties("stay")
data class StayProperties(
    val suppliers: Map<String, Supplier> = emptyMap(),
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
    )

    fun of(supplierId: String): Supplier =
        suppliers[supplierId]
            ?: error("공급사 설정이 없다: stay.suppliers.$supplierId")
}
